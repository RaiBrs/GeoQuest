package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
