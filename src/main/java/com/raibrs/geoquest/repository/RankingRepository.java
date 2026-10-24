package com.raibrs.geoquest.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.model.RankingEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.file.StandardCopyOption;

public class RankingRepository {

    private final Path rankingFile;
    private final ObjectMapper objectMapper;

    public RankingRepository(Path rankingFile) {
        this.rankingFile = rankingFile;
        this.objectMapper = new ObjectMapper();
    }

    public List<RankingEntry> load() throws IOException {
        // A missing file represents a player who has not saved any result yet.
        if (Files.notExists(rankingFile)) {
            return List.of();
        }

        // TypeReference preserves List<RankingEntry> at runtime for Jackson's generic deserialization.
        List<RankingEntry> entries = objectMapper.readValue(rankingFile.toFile(), new TypeReference<List<RankingEntry>>() {
        });

        if (entries == null) {
            throw new IOException("Ranking file must contain a list of entries.");
        }

        if (entries.stream().anyMatch(entry -> entry == null)) {
            throw new IOException("Ranking file must not contain null entries.");
        }

        return entries;
    }

    public void save(List<RankingEntry> entries) throws IOException {
        Path destination = rankingFile.toAbsolutePath();
        Path directory = destination.getParent();

        Files.createDirectories(directory);

        Path temporaryFile = Files.createTempFile(
                directory, "ranking-", ".tmp");

        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temporaryFile.toFile(), entries);

            Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }
}
