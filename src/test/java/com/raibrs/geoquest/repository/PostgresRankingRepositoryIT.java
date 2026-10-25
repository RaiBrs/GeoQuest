package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRankingRepositoryIT {
    private static PGSimpleDataSource source;
    private static final String SCHEMA = "geoquest_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;
    private PostgresRankingRepository repository;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv("TEST_DATABASE_USER");
        if (url == null || !url.startsWith("jdbc:postgresql://") || user == null || user.isBlank()) {
            throw new IllegalStateException("Set TEST_DATABASE_URL and TEST_DATABASE_USER for PostgreSQL integration tests.");
        }
        source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(user);
        source.setPassword(System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
        source.setConnectTimeout(5);
        source.setSocketTimeout(10);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        source.setCurrentSchema(SCHEMA);
        try (var resource = PostgresRankingRepositoryIT.class.getResourceAsStream(
                "/db/migration/V1__create_ranking_entries.sql")) {
            if (resource == null) {
                throw new IllegalStateException("Initial schema resource is missing.");
            }
            String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    @AfterAll
    static void dropOnlyTheSchemaCreatedByThisTest() throws SQLException {
        if (schemaCreated) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @BeforeEach
    void resetTestData() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM ranking_entries");
        }
        repository = new PostgresRankingRepository(source);
    }

    @Test
    void persistsResultsAcrossRepositoryInstances() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 3, 10, 1234L));

        RankingEntry entry = new PostgresRankingRepository(source).findTop(10).getFirst();

        assertEquals("Rai", entry.getPlayerName());
        assertEquals(3, entry.getScore());
        assertEquals(10, entry.getTotalQuestions());
        assertEquals(1234L, entry.getPlayedAtEpochMillis());
    }

    @Test
    void keepsOnlyTheBestResultIgnoringCase() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 3, 10, 2000L));
        repository.saveBest(new RankingEntry("RAI", 8, 10, 1000L));
        repository.saveBest(new RankingEntry("rai", 2, 10, 3000L));

        List<RankingEntry> entries = repository.findTop(10);

        assertEquals(1, entries.size());
        assertEquals(8, entries.getFirst().getScore());
        assertEquals("RAI", entries.getFirst().getPlayerName());
        assertEquals(1000L, entries.getFirst().getPlayedAtEpochMillis());
    }

    @Test
    void keepsTheMostRecentTiedResultWhenResultsArriveOutOfOrder() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 8, 10, 1000L));
        repository.saveBest(new RankingEntry("RAI", 8, 10, 3000L));
        repository.saveBest(new RankingEntry("rai", 8, 10, 2000L));

        RankingEntry entry = repository.findTop(10).getFirst();

        assertEquals("RAI", entry.getPlayerName());
        assertEquals(3000L, entry.getPlayedAtEpochMillis());
    }

    @Test
    void limitsTheDisplayWithoutDiscardingPlayers() throws SQLException {
        for (int score = 0; score <= 10; score++) {
            repository.saveBest(new RankingEntry("Player " + score, score, 10, score + 1));
        }

        List<RankingEntry> top = repository.findTop(10);

        assertEquals(10, top.size());
        assertEquals(10, top.getFirst().getScore());
        assertEquals(1, top.getLast().getScore());
        assertEquals(11, countRows());
    }

    @Test
    void ordersByScoreThenMostRecentResultWithoutChangingData() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 2, 10, 1L));
        repository.saveBest(new RankingEntry("Ana", 8, 10, 2L));
        repository.saveBest(new RankingEntry("Bia", 8, 10, 3L));

        assertEquals(List.of("Bia", "Ana", "Rai"), repository.findTop(10).stream()
                .map(RankingEntry::getPlayerName).toList());
        assertEquals(3, countRows());
    }

    @Test
    void deletesOnlyTheRequestedPlayerIgnoringCaseAndWhitespace() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 3, 10, 1L));
        repository.saveBest(new RankingEntry("Ana", 4, 10, 2L));

        assertTrue(repository.deleteByPlayerName(" RAI "));
        assertFalse(repository.deleteByPlayerName("Rai"));
        assertEquals("Ana", repository.findTop(10).getFirst().getPlayerName());
    }

    @Test
    void treatsSqlSyntaxInNamesAsData() throws SQLException {
        String name = "Rai'); DROP TABLE ranking_entries; --";

        repository.saveBest(new RankingEntry(name, 3, 10, 1L));

        assertEquals(name, repository.findTop(10).getFirst().getPlayerName());
        assertTrue(repository.deleteByPlayerName(name));
        assertEquals(0, countRows());
    }

    @Test
    void enforcesScoreConstraintsEvenWhenBypassingJavaValidation() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            SQLException exception = assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO ranking_entries VALUES ('invalid', 'Invalid', 11, 10, 1)
                    """));

            assertEquals("23514", exception.getSQLState());
        }
        assertEquals(0, countRows());
    }

    @Test
    void concurrentResultsCannotReplaceTheBestScoreWithAWorseOne() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int score = 0; score <= 10; score++) {
                int result = score;
                futures.add(executor.submit(() -> {
                    repository.saveBest(new RankingEntry(result % 2 == 0 ? "RAI" : "rai",
                            result, 10, 1000L + result));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertEquals(1, countRows());
        assertEquals(10, repository.findTop(10).getFirst().getScore());
    }

    @Test
    void failedUpdatePreservesTheExistingResult() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 3, 10, 1L));
        RankingEntry invalid = new RankingEntry("RAI", 1, 10, 2L) {
            @Override public int getScore() { return 11; }
        };

        SQLException exception = assertThrows(SQLException.class, () -> repository.saveBest(invalid));

        assertEquals("23514", exception.getSQLState());
        assertEquals(1, countRows());
        RankingEntry preserved = repository.findTop(10).getFirst();
        assertEquals("Rai", preserved.getPlayerName());
        assertEquals(3, preserved.getScore());
        assertEquals(1L, preserved.getPlayedAtEpochMillis());
    }

    @Test
    void preservesTheExistingUnicodeIdentityRule() throws SQLException {
        repository.saveBest(new RankingEntry("İ", 2, 10, 1L));
        repository.saveBest(new RankingEntry("i", 3, 10, 2L));

        assertEquals(1, countRows());
        assertEquals(3, repository.findTop(10).getFirst().getScore());
        assertTrue(repository.deleteByPlayerName("ı"));
    }

    @Test
    void rejectsInvalidLimitsBeforeQuerying() {
        assertThrows(IllegalArgumentException.class, () -> repository.findTop(0));
    }

    private int countRows() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM ranking_entries")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
