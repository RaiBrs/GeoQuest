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
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingServiceTest {

    @Test
    void renamePreservesResultAndDoesNotMergePlayers() throws SQLException {
        var repository = new InMemoryRankingRepository();
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        repository.saveBest(new RankingEntry("Other", 2, 10, 5678));
        var service = new RankingService(repository);
        service.renamePlayer(" rai ", " Raibr ");
        var renamed = repository.allEntries().stream()
                .filter(entry -> entry.getPlayerName().equals("Raibr")).findFirst().orElseThrow();
        assertEquals(7, renamed.getScore());
        assertEquals(1234, renamed.getPlayedAtEpochMillis());
        assertEquals(2, repository.allEntries().size());
        var before = repository.allEntries();
        var failure = assertThrows(IllegalStateException.class, () -> service.renamePlayer("Raibr", "OTHER"));
        assertEquals("23505", ((SQLException) failure.getCause()).getSQLState());
        assertEquals(before, repository.allEntries());
    }

    @Test
    void renameRejectsMissingOrInvalidNamesWithoutCreatingRows() {
        var repository = new InMemoryRankingRepository();
        var service = new RankingService(repository);
        assertThrows(java.util.NoSuchElementException.class, () -> service.renamePlayer("Missing", "New"));
        assertThrows(IllegalArgumentException.class, () -> service.renamePlayer("Rai", " "));
        assertThrows(IllegalArgumentException.class, () -> service.renamePlayer("Rai", "Bad\tName"));
        assertTrue(repository.allEntries().isEmpty());
    }

    @Test
    void renameDoesNotMisreportConnectionFailureAsANameCollision() {
        var repository = new InMemoryRankingRepository() {
            @Override
            public boolean renameByPlayerName(String current, String replacement) throws SQLException {
                throw new SQLException("Test connection failure", "08006");
            }
        };
        var service = new RankingService(repository);
        assertEquals("08006", assertThrows(SQLException.class,
                () -> service.renamePlayer("Rai", "New")).getSQLState());
    }


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

    // Displaying the Top 10 must not discard players outside that window.
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
    void deletesOnlyTheSpecifiedPlayerResult() throws SQLException {
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
