package com.raibrs.geoquest.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.model.RankingEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        return objectMapper.readValue(rankingFile.toFile(), new TypeReference<List<RankingEntry>>() {
        });
    }

    public void save(List<RankingEntry> entries) throws IOException {
        Path parentDirectory = rankingFile.getParent();
        // Create the storage folder before writing the first ranking file.
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(rankingFile.toFile(), entries);
    }
}
