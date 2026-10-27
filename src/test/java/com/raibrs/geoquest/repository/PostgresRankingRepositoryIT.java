package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.dto.GameDtos;
import com.raibrs.geoquest.config.DatabaseCheck;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
        for (String migration : List.of("V1__create_ranking_entries.sql", "V2__validate_player_names.sql")) {
            try (var resource = PostgresRankingRepositoryIT.class.getResourceAsStream("/db/migration/" + migration)) {
                if (resource == null) {
                    throw new IllegalStateException("Schema resource is missing: " + migration);
                }
                String sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
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
    void renamesOnlyTheSelectedPlayerAndPreservesTheOriginalResult() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        repository.saveBest(new RankingEntry("Other", 2, 10, 5678));
        assertTrue(repository.renameByPlayerName(" rai ", " Raibr "));

        var entries = new PostgresRankingRepository(source).findTop(10);
        assertEquals(2, entries.size());
        var renamed = entries.getFirst();
        assertEquals("Raibr", renamed.getPlayerName());
        assertEquals(7, renamed.getScore());
        assertEquals(10, renamed.getTotalQuestions());
        assertEquals(1234, renamed.getPlayedAtEpochMillis());
        assertEquals("Other", entries.getLast().getPlayerName());
        assertEquals(2, entries.getLast().getScore());
        assertEquals(5678, entries.getLast().getPlayedAtEpochMillis());
        assertFalse(repository.deleteByPlayerName("Rai"));
    }

    @Test
    void occupiedNameRejectsRenameWithoutChangingEitherResult() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        repository.saveBest(new RankingEntry("Other", 2, 10, 5678));
        var failure = assertThrows(SQLException.class, () -> repository.renameByPlayerName("Rai", "OTHER"));
        assertEquals("23505", failure.getSQLState());
        var entries = repository.findTop(10);
        assertEquals(2, entries.size());
        assertEquals("Rai", entries.getFirst().getPlayerName());
        assertEquals(7, entries.getFirst().getScore());
        assertEquals("Other", entries.getLast().getPlayerName());
        assertEquals(2, entries.getLast().getScore());
    }

    @Test
    void renameSupportsCaseOnlyChangesAndExistingUnicodeIdentityRules() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        assertTrue(repository.renameByPlayerName("rai", "RAI"));
        assertEquals("RAI", repository.findTop(10).getFirst().getPlayerName());
        repository.saveBest(new RankingEntry("İpek", 2, 10, 5678));
        assertTrue(repository.renameByPlayerName("ipek", "New İpek"));
        assertEquals("New İpek", repository.findTop(10).getLast().getPlayerName());
    }

    @Test
    void renameRejectsInvalidNamesAndDoesNotCreateMissingPlayers() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        assertFalse(repository.renameByPlayerName("Missing", "New"));
        assertThrows(IllegalArgumentException.class, () -> repository.renameByPlayerName("Rai", " "));
        assertThrows(IllegalArgumentException.class, () -> repository.renameByPlayerName("Rai", "Bad\tName"));
        var entry = repository.findTop(10).getFirst();
        assertEquals("Rai", entry.getPlayerName());
        assertEquals(7, entry.getScore());
    }

    @Test
    void concurrentRenamesCannotMergeOrOverwritePlayers() throws Exception {
        repository.saveBest(new RankingEntry("First", 7, 10, 1234));
        repository.saveBest(new RankingEntry("Second", 2, 10, 5678));
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (String name : List.of("First", "Second")) {
                attempts.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return repository.renameByPlayerName(name, "Destination");
                    } catch (SQLException exception) {
                        assertEquals("23505", exception.getSQLState());
                        return false;
                    }
                }));
            }
            int successful = 0;
            for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) successful++;
            assertEquals(1, successful);
        }
        var entries = repository.findTop(10);
        assertEquals(2, entries.size());
        assertEquals(List.of(7, 2), entries.stream().map(RankingEntry::getScore).toList());
        assertEquals(1, entries.stream().filter(entry -> entry.getPlayerName().equals("Destination")).count());
    }

    @Test
    void persistsTheJavaWebRoundScoreAndPreservesBestResult() throws Exception {
        var games = new GameService(new TestCountriesClient(),
                new QuestionService(), new RankingService(repository));
        var state = games.start("Web Player");
        for (int index = 0; index < 3; index++) {
            String correct = TestCountriesClient.answerFor(state.question().text());
            state = games.answer(new GameDtos.AnswerRequest(
                    state.gameId(), state.number(), correct)).state();
        }
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream()
                .filter(choice -> !choice.equals(correct)).findFirst().orElseThrow();
        games.answer(new GameDtos.AnswerRequest(
                state.gameId(), state.number(), wrong));
        games.saveResult(state.gameId());
        games.saveResult(state.gameId());

        var saved = new PostgresRankingRepository(source).findTop(10).getFirst();
        assertEquals("Web Player", saved.getPlayerName());
        assertEquals(3, saved.getScore());
        assertEquals(10, saved.getTotalQuestions());
        repository.saveBest(new RankingEntry("WEB PLAYER", 1, 10,
                saved.getPlayedAtEpochMillis() + 1000));
        assertEquals(3, repository.findTop(10).getFirst().getScore());
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

    @Test
    void enforcesNameLengthAndControlCharacterConstraintsAtTheDatabaseBoundary() throws SQLException {
        for (String invalidName : List.of("a".repeat(81), "Ra\ti", "Ra\u0085i")) {
            try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                    "INSERT INTO ranking_entries VALUES (?, ?, 3, 10, 1)")) {
                statement.setString(1, invalidName);
                statement.setString(2, invalidName);

                SQLException exception = assertThrows(SQLException.class, statement::executeUpdate);

                assertEquals("23514", exception.getSQLState());
            }
        }
        assertEquals(0, countRows());
    }

    @Test
    void persistsTheUnicodeNameLengthBoundaryAndKeepsRetriesIdempotent() throws SQLException {
        RankingEntry result = new RankingEntry("😀".repeat(80), 10, 10, 1234L);

        repository.saveBest(result);
        repository.saveBest(result);

        assertEquals(1, countRows());
        assertEquals(result.getPlayerName(), repository.findTop(10).getFirst().getPlayerName());
        assertEquals(1234L, repository.findTop(10).getFirst().getPlayedAtEpochMillis());
    }

    @Test
    void checksTheActualDatabaseAndSchemaWithoutChangingData() throws SQLException {
        repository.saveBest(new RankingEntry("Rai", 3, 10, 1L));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        DatabaseCheck.check(source, source.getDatabaseName(), new PrintStream(output));

        assertTrue(output.toString().contains("Connected database: " + source.getDatabaseName()));
        assertTrue(output.toString().contains("Ranking schema: ready"));
        assertEquals(1, countRows());
    }

    @Test
    void rejectsAConnectionToAnUnexpectedDatabase() {
        assertThrows(IllegalStateException.class, () -> DatabaseCheck.check(
                source, "a_different_database", new PrintStream(new ByteArrayOutputStream())));
    }

    private int countRows() throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM ranking_entries")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
