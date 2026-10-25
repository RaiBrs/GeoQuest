package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingServiceTest {


    // Ensures higher scores rank first and newer rounds resolve score ties.
    @Test
    void ordersResultsByScoreThenByMostRecentRound() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();

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
    void keepsOnlyTheBestResultForEachPlayer() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();

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
        assertEquals(1, repository.allEntries().size());
    }

    // Ensures the terminal receives only the Top 10 while the repository keeps the full history.
    @Test
    void returnsOnlyTheTopTenEntriesAndKeepsAllSavedResults() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();
        RankingService service = new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"));
        List<RankingEntry> ranking = List.of();

        for (int score = 0; score <= 10; score++) {
            ranking = service.registerResult("Player" + score, score, 10);
        }

        assertEquals(10, ranking.size());
        assertEquals(10, ranking.getFirst().getScore());
        assertEquals(1, ranking.getLast().getScore());
        assertEquals(11, repository.allEntries().size());
    }

    // Ensures deletion ignores name capitalization and preserves every other player's result.
    @Test
    void deletesOnlyTheLoggedInPlayerResult() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();
        RankingService service = new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"));
        service.registerResult("Rai", 4, 5);
        service.registerResult("Ana", 3, 5);

        boolean removed = service.deleteResultFor("RAI");

        assertTrue(removed);
        assertEquals(List.of("Ana"), repository.allEntries().stream()
                .map(RankingEntry::getPlayerName)
                .toList());
    }

    // Ensures a missing player result does not remove any existing ranking entry.
    @Test
    void keepsTheRankingWhenThePlayerHasNoResult() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();
        RankingService service = new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"));
        service.registerResult("Rai", 4, 5);
        service.registerResult("Ana", 3, 5);

        boolean removed = service.deleteResultFor("Bia");

        assertFalse(removed);
        assertEquals(List.of("Rai", "Ana"), repository.allEntries().stream()
                .map(RankingEntry::getPlayerName)
                .toList());
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void updatesTheTimestampAndNameForTheSamePlayersTiedResult() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();
        new RankingService(repository, fixedClock("2025-09-01T10:00:00Z"))
                .registerResult("Rai", 4, 10);
        Clock later = fixedClock("2025-09-01T12:00:00Z");

        List<RankingEntry> ranking = new RankingService(repository, later).registerResult("RAI", 4, 10);

        assertEquals(1, ranking.size());
        assertEquals("RAI", ranking.getFirst().getPlayerName());
        assertEquals(later.millis(), ranking.getFirst().getPlayedAtEpochMillis());
        assertEquals(later.millis(), repository.allEntries().getFirst().getPlayedAtEpochMillis());
    }

    @Test
    void ordersTheRankingWithoutChangingSavedEntries() throws SQLException {
        InMemoryRankingRepository repository = new InMemoryRankingRepository();
        repository.saveBest(new RankingEntry("Rai", 2, 10, 1L));
        repository.saveBest(new RankingEntry("Ana", 8, 10, 2L));
        repository.saveBest(new RankingEntry("Bia", 8, 10, 3L));
        List<RankingEntry> original = repository.allEntries();

        List<RankingEntry> ranking = new RankingService(repository).getTopRanking();

        assertEquals(List.of("Bia", "Ana", "Rai"), ranking.stream().map(RankingEntry::getPlayerName).toList());
        assertEquals(original, repository.allEntries());
    }
}
