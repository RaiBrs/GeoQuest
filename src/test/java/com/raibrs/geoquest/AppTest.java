package com.raibrs.geoquest;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.ui.ConsoleUi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private TrackingRepository repository;
    private RankingService rankingService;
    private StubCountriesClient client;
    private final Logger logger = Logger.getLogger(App.class.getName());
    private final List<LogRecord> logRecords = new ArrayList<>();
    private final Handler logHandler = new Handler() {
        @Override public void publish(LogRecord record) { logRecords.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private boolean previousParentHandlers;

    @BeforeEach
    void setUp() {
        repository = new TrackingRepository();
        rankingService = new RankingService(repository,
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC));
        client = new StubCountriesClient();
        previousParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(logHandler);
    }

    @AfterEach
    void restoreLogging() {
        logger.removeHandler(logHandler);
        logger.setUseParentHandlers(previousParentHandlers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Rai\n", "Rai\n4\n"})
    void handlesClosedInputAtLoginMenuAndLogout(String input) {
        ConsoleUi ui = new ConsoleUi(new Scanner(input), new PrintStream(output));

        assertDoesNotThrow(() -> app(ui).run());

        assertTrue(output.toString().contains("Input is no longer available."));
        assertEquals(0, client.requests);
        assertEquals(0, repository.saves);
    }

    @Test
    void stopsAtTheFirstWrongAnswerAndSavesOnce() throws SQLException {
        AnsweringUi ui = new AnsweringUi("Rai\n1\n5\n", 0);

        app(ui).run();

        assertEquals(1, ui.questionsAsked);
        assertEquals(1, repository.saves);
        assertEquals(0, repository.allEntries().getFirst().getScore());
        assertTrue(output.toString().contains("Final score: 0/10."));
    }

    @Test
    void savesOnlyTheCorrectAnswersBeforeTheFirstMistake() throws SQLException {
        AnsweringUi ui = new AnsweringUi("Rai\n1\n5\n", 3);

        app(ui).run();

        assertEquals(4, ui.questionsAsked);
        assertEquals(3, repository.allEntries().getFirst().getScore());
        assertEquals(1, repository.saves);
    }

    @Test
    void completesTenCorrectAnswersAndSavesOnce() throws SQLException {
        AnsweringUi ui = new AnsweringUi("Rai\n1\n5\n", 10);

        app(ui).run();

        assertEquals(10, ui.questionsAsked);
        assertEquals(1, repository.saves);
        assertEquals(10, repository.allEntries().getFirst().getScore());
        assertEquals(10, repository.allEntries().getFirst().getTotalQuestions());
        assertTrue(output.toString().contains("Final score: 10/10."));
    }

    @Test
    void reusesTheCatalogAndCreatesNewQuestionsForEachRound() {
        List<List<String>> rounds = new ArrayList<>();
        QuestionService questions = new QuestionService(new Random(0)) {
            @Override
            public List<Question> createCapitalQuestions(List<Country> countries, int count) {
                List<Question> round = super.createCapitalQuestions(countries, count);
                rounds.add(round.stream().map(Question::getText).toList());
                return round;
            }
        };
        AnsweringUi ui = new AnsweringUi("Rai\n1\n1\n5\n", 0);

        new App(ui, client, questions, rankingService).run();

        assertEquals(1, client.requests);
        assertEquals(2, repository.saves);
        assertEquals(2, rounds.size());
        assertNotEquals(rounds.get(0), rounds.get(1));
    }

    @Test
    void changesThePlayerIdentityAfterLogout() throws SQLException {
        app(new AnsweringUi("Rai\n1\n4\nAna\n1\n5\n", 0)).run();

        assertEquals(List.of("Ana", "Rai"), repository.allEntries().stream()
                .map(RankingEntry::getPlayerName).sorted().toList());
        assertEquals(1, client.requests);
    }

    @Test
    void cancelledDeletionPreservesTheRankingWithoutSaving() throws SQLException {
        rankingService.registerResult("Rai", 3, 10);
        List<RankingEntry> original = repository.allEntries();
        repository.saves = 0;

        app(new AnsweringUi("Rai\n3\nn\n5\n", 0)).run();

        assertEquals(0, repository.saves);
        assertEquals(original, repository.allEntries());
        assertTrue(output.toString().contains("deletion cancelled"));
    }

    @Test
    void confirmedDeletionRemovesOnlyTheCurrentPlayer() throws SQLException {
        rankingService.registerResult("Rai", 3, 10);
        rankingService.registerResult("Ana", 4, 10);

        app(new AnsweringUi("Rai\n3\ny\n5\n", 0)).run();

        assertEquals(List.of("Ana"), repository.allEntries().stream().map(RankingEntry::getPlayerName).toList());
    }

    @Test
    void returnsToTheMenuAfterAnApiFailureAndRetriesOnlyWhenAsked() {
        client.failuresRemaining = 1;

        app(new AnsweringUi("Rai\n1\n2\n1\n5\n", 0)).run();

        assertEquals(2, client.requests);
        assertEquals(1, repository.saves);
        assertTrue(output.toString().contains("Could not load countries"));
        assertTrue(output.toString().contains("No ranking entries yet."));
        assertTrue(output.toString().contains("Thanks for playing"));
        assertFalse(output.toString().contains("Could not read or save"));
    }

    @Test
    void rejectsAnInsufficientCatalogWithoutSavingAndAllowsAnotherAttempt() {
        client.countries = List.of(new Country("Brazil", "Brasília"));

        app(new AnsweringUi("Rai\n1\n1\n5\n", 0)).run();

        assertEquals(2, client.requests);
        assertEquals(0, repository.saves);
        assertTrue(output.toString().contains("Country data cannot produce a round."));
    }

    @Test
    void logsTheDatabaseCauseAndKeepsTheMenuAvailable() {
        repository.readFailure = new SQLTransientConnectionException("Test database unavailable.", "08006");

        app(new AnsweringUi("Rai\n2\n5\n", 0)).run();

        assertTrue(output.toString().contains("Could not read or save the ranking"));
        assertTrue(output.toString().contains("Thanks for playing"));
        assertEquals(1, logRecords.size());
        SQLException cause = assertInstanceOf(SQLException.class, logRecords.getFirst().getThrown());
        assertEquals("08006", cause.getSQLState());
    }

    @Test
    void identifiesDatabaseAccessFailuresWithoutReportingAnApiFailure() {
        repository.readFailure = new SQLInvalidAuthorizationSpecException("Test access denied.", "28000");

        app(new AnsweringUi("Rai\n2\n5\n", 0)).run();

        assertEquals(1, logRecords.size());
        assertInstanceOf(SQLInvalidAuthorizationSpecException.class, logRecords.getFirst().getThrown());
        assertTrue(output.toString().contains("Check the database connection."));
        assertFalse(output.toString().contains("Could not load countries"));
        assertTrue(output.toString().contains("Thanks for playing"));
    }

    @Test
    void stopsOnInterruptionAndRestoresTheInterruptFlag() {
        client.interrupted = true;
        try {
            app(new AnsweringUi("Rai\n1\n1\n5\n", 0)).run();

            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, client.requests);
            assertEquals(0, repository.saves);
            assertTrue(output.toString().contains("request was interrupted"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotSaveAnAbandonedRoundWhenInputCloses() {
        ConsoleUi ui = new ConsoleUi(new Scanner("Rai\n1\n"), new PrintStream(output));

        app(ui).run();

        assertEquals(0, repository.saves);
        assertTrue(output.toString().contains("Input is no longer available."));
    }

    private App app(ConsoleUi ui) {
        return new App(ui, client, new QuestionService(new Random(0)), rankingService);
    }

    private class AnsweringUi extends ConsoleUi {
        private final int correctAnswers;
        private int questionsAsked;

        AnsweringUi(String input, int correctAnswers) {
            super(new Scanner(input), new PrintStream(output));
            this.correctAnswers = correctAnswers;
        }

        @Override
        public String askQuestion(Question question) {
            return questionsAsked++ < correctAnswers ? question.getCorrectAnswer() : "Not a capital";
        }
    }

    private static class TrackingRepository extends InMemoryRankingRepository {
        private int saves;
        private SQLException readFailure;

        @Override
        public void saveBest(RankingEntry entry) throws SQLException {
            super.saveBest(entry);
            saves++;
        }

        @Override
        public List<RankingEntry> findTop(int limit) throws SQLException {
            if (readFailure != null) {
                throw readFailure;
            }
            return super.findTop(limit);
        }
    }

    private static class StubCountriesClient extends CountriesClient {
        private int requests;
        private int failuresRemaining;
        private boolean interrupted;
        private List<Country> countries = java.util.stream.IntStream.range(0, 40)
                .mapToObj(index -> new Country("Country " + index, "Capital " + index)).toList();

        StubCountriesClient() {
            super("test-key");
        }

        @Override
        public List<Country> fetchAllCountries(int pageSize) throws IOException, InterruptedException {
            requests++;
            if (interrupted) {
                throw new InterruptedException("Test interruption");
            }
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IOException("Test connection failure");
            }
            return countries;
        }
    }
}
