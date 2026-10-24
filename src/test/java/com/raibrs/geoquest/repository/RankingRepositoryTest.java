package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    // Ensures a first-time player starts with an empty ranking when no file has been created yet.
    @Test
    void returnsAnEmptyListWhenTheRankingFileDoesNotExist() throws IOException {
        RankingRepository repository = new RankingRepository(
                temporaryDirectory.resolve("data/ranking.json"));

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void loadsAnEmptyRankingList() throws IOException {
        Path rankingFile = temporaryDirectory.resolve("ranking.json");
        Files.writeString(rankingFile, "[]");
        RankingRepository repository = new RankingRepository(rankingFile);

        assertTrue(repository.load().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "[null]",
            "[{\"playerName\":\"Rai\",\"score\":3,\"totalQuestions\":10,\"playedAtEpochMillis\":1},null]",
            "["
    })
    void rejectsInvalidRankingDataWithoutChangingTheFile(String content) throws IOException {
        Path rankingFile = temporaryDirectory.resolve("ranking.json");
        Files.writeString(rankingFile, content);
        byte[] originalBytes = Files.readAllBytes(rankingFile);
        RankingRepository repository = new RankingRepository(rankingFile);

        assertThrows(IOException.class, repository::load);

        assertArrayEquals(originalBytes, Files.readAllBytes(rankingFile));
    }

    @Test
    void replacesAnExistingRankingWithTheNewResults() throws IOException {
        Path rankingFile = temporaryDirectory.resolve("ranking.json");
        RankingRepository repository = new RankingRepository(rankingFile);
        repository.save(List.of(new RankingEntry("Rai", 1, 10, 1L)));

        repository.save(List.of(new RankingEntry("Ana", 8, 10, 2L)));

        List<RankingEntry> entries = repository.load();
        assertEquals(1, entries.size());
        assertEquals("Ana", entries.getFirst().getPlayerName());
        assertEquals(8, entries.getFirst().getScore());
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(rankingFile), files.toList());
        }
    }

    @Test
    void preservesThePreviousFileAndRemovesTheTemporaryFileWhenSerializationFails() throws IOException {
        Path rankingFile = temporaryDirectory.resolve("ranking.json");
        RankingRepository repository = new RankingRepository(rankingFile);
        repository.save(List.of(new RankingEntry("Rai", 3, 10, 1L)));
        byte[] original = Files.readAllBytes(rankingFile);
        RankingEntry brokenEntry = new RankingEntry("Ana", 5, 10, 2L) {
            @Override
            public int getScore() {
                throw new IllegalStateException("Simulated serialization failure.");
            }
        };

        assertThrows(IOException.class, () -> repository.save(List.of(brokenEntry)));

        assertArrayEquals(original, Files.readAllBytes(rankingFile));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(rankingFile), files.toList());
        }
    }

    @Test
    void removesTheTemporaryFileWhenReplacementFails() throws IOException {
        Path destination = Files.createDirectory(temporaryDirectory.resolve("ranking.json"));
        Path marker = destination.resolve("marker.txt");
        Files.writeString(marker, "preserve this directory");
        RankingRepository repository = new RankingRepository(destination);

        assertThrows(IOException.class,
                () -> repository.save(List.of(new RankingEntry("Rai", 3, 10, 1L))));

        assertEquals("preserve this directory", Files.readString(marker));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(destination), files.toList());
        }
    }

    // Ensures saved results survive a JSON write-and-read cycle without using the player's real files.
    @Test
    void savesAndLoadsRankingEntries() throws IOException {
        Path rankingFile = temporaryDirectory.resolve("data/ranking.json");
        RankingRepository repository = new RankingRepository(rankingFile);
        List<RankingEntry> entries = List.of(
                new RankingEntry("Rai", 3, 5, 1_756_684_800_000L),
                new RankingEntry("Ana", 5, 5, 1_756_688_400_000L));

        repository.save(entries);
        List<RankingEntry> loadedEntries = repository.load();

        assertTrue(Files.exists(rankingFile));
        assertEquals(2, loadedEntries.size());
        assertEquals("Rai", loadedEntries.getFirst().getPlayerName());
        assertEquals(3, loadedEntries.getFirst().getScore());
        assertEquals("Ana", loadedEntries.get(1).getPlayerName());
        assertEquals(5, loadedEntries.get(1).getScore());
    }
}
