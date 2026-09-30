package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingServiceTest {

    @TempDir
    Path temporaryDirectory;

    // Ensures higher scores rank first and newer rounds resolve score ties.
    @Test
    void ordersResultsByScoreThenByMostRecentRound() throws IOException {
        RankingRepository repository = new RankingRepository(
                temporaryDirectory.resolve("data/ranking.json"));

        new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"))
                .registerResult("Rai", 3, 5);
        new RankingService(repository, fixedClock("2025-09-01T11:00:00Z"))
                .registerResult("Ana", 5, 5);
        List<RankingEntry> ranking = new RankingService(
                repository,
                fixedClock("2025-09-01T12:00:00Z"))
                .registerResult("Bia", 5, 5);

        assertEquals(List.of("Bia", "Ana", "Rai"),
                ranking.stream().map(RankingEntry::getPlayerName).toList());
    }

    // Ensures one player keeps only their best result, even when names differ by letter case.
    @Test
    void keepsOnlyTheBestResultForEachPlayer() throws IOException {
        RankingRepository repository = new RankingRepository(
                temporaryDirectory.resolve("data/ranking.json"));

        new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"))
                .registerResult("Rai", 1, 5);
        new RankingService(repository, fixedClock("2025-09-01T11:00:00Z"))
                .registerResult("rai", 4, 5);
        List<RankingEntry> ranking = new RankingService(
                repository,
                fixedClock("2025-09-01T12:00:00Z"))
                .registerResult("RAI", 2, 5);

        assertEquals(1, ranking.size());
        assertEquals("rai", ranking.getFirst().getPlayerName());
        assertEquals(4, ranking.getFirst().getScore());
        assertEquals(1, repository.load().size());
    }

    // Ensures the terminal receives only the Top 10 while the repository keeps the full history.
    @Test
    void returnsOnlyTheTopTenEntriesAndKeepsAllSavedResults() throws IOException {
        RankingRepository repository = new RankingRepository(
                temporaryDirectory.resolve("data/ranking.json"));
        RankingService service = new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"));
        List<RankingEntry> ranking = List.of();

        for (int score = 0; score <= 10; score++) {
            ranking = service.registerResult("Player" + score, score, 10);
        }

        assertEquals(10, ranking.size());
        assertEquals(10, ranking.getFirst().getScore());
        assertEquals(1, ranking.getLast().getScore());
        assertEquals(11, repository.load().size());
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }
}
