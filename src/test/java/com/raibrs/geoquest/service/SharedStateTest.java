package com.raibrs.geoquest.service;

import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql://.*")
class SharedStateTest {
    private PGSimpleDataSource source;
    private GameService first, second;
    private Instant now;
    private Clock clock;

    @BeforeEach void prepare() throws Exception {
        source = new PGSimpleDataSource();
        source.setURL(System.getenv("TEST_DATABASE_URL"));
        source.setUser(System.getenv().getOrDefault("TEST_DATABASE_USER", "geoquest_test"));
        source.setPassword(System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "test-only-password"));
        try (var db = source.getConnection(); var sql = db.createStatement()) {
            assertEquals("geoquest_test", db.getCatalog(), "Only the disposable test database is allowed");
            for (String migration : List.of("001_accounts", "002_oauth_profile", "003_shared_rounds", "004_shared_oauth_sessions", "005_timed_rankings"))
                sql.execute(Files.readString(Path.of("database/" + migration + ".sql")));
            sql.execute("truncate player_profiles,game_rounds,geoquest_http_session cascade");
        }
        now = Instant.parse("2026-01-01T00:00:00Z");
        clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        };
        first = instance(); second = instance();
    }

    private GameService instance() {
        var storage = new PostgresRoundStorage(source); storage.verifySchema();
        return new GameService(new TestCountriesClient(), new QuestionService(), clock, storage);
    }

    @Test void controllerReadsStateAndScoringMetadataInOneTransaction() throws Exception {
        var counted = org.mockito.Mockito.mock(javax.sql.DataSource.class,
                org.mockito.AdditionalAnswers.delegatesTo(source));
        var games = new GameService(new TestCountriesClient(), new QuestionService(), clock,
                new PostgresRoundStorage(counted));
        var controller = new com.raibrs.geoquest.controller.GameController(games,
                new com.raibrs.geoquest.account.AccountSessions(null, null));
        var state = games.start(GameMode.CAPITALS, QuestionType.COUNTRY, GameLanguage.EN);
        org.mockito.Mockito.clearInvocations(counted);
        var answer = controller.answer(new AnswerRequest(state.gameId(), 1,
                state.question().alternatives().getFirst()), null);
        org.mockito.Mockito.verify(counted, org.mockito.Mockito.times(1)).getConnection();
        assertEquals(1, answer.state().answered());
        org.mockito.Mockito.clearInvocations(counted);
        assertEquals(answer.state(), controller.status(
                new com.raibrs.geoquest.dto.GameDtos.StatusRequest(state.gameId()), null));
        org.mockito.Mockito.verify(counted, org.mockito.Mockito.times(1)).getConnection();
        assertEquals(QuestionType.COUNTRY, games.statusWithType(state.gameId(), null).questionType());
    }

    @Test void aRoundAndItsExactRetrySurviveAcrossInstances() throws Exception {
        var state = first.start(GameMode.CAPITALS, QuestionType.CAPITAL, GameLanguage.EN);
        var request = new AnswerRequest(state.gameId(), 1, TestCountriesClient.answerFor(state.question().text()));
        var answer = second.answer(request);
        assertEquals(1, answer.state().score());
        assertEquals(answer, first.answer(request));
        assertEquals(2, first.status(state.gameId()).number());
        assertEquals(1, instance().status(state.gameId()).score(), "A fresh process restores the same round");
    }

    @Test void concurrentDuplicateAnswersScoreOnlyOnce() throws Exception {
        var state = first.start(GameMode.CAPITALS);
        var request = new AnswerRequest(state.gameId(), 1, TestCountriesClient.answerFor(state.question().text()));
        try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = tasks.submit(() -> first.answer(request));
            var b = tasks.submit(() -> second.answer(request));
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, first.status(state.gameId()).score());
        assertEquals(1, second.status(state.gameId()).answered());
    }

    @Test void survivalContinuesPastTenQuestionsAcrossInstancesAndStopsOnAMistake() throws Exception {
        var state = first.start(GameMode.SURVIVAL);
        assertEquals(40, state.total());
        for (int number = 1; number <= 13; number++) {
            var request = new AnswerRequest(state.gameId(), number, TestCountriesClient.answerFor(state.question().text()));
            state = (number % 2 == 0 ? first : second).answer(request).state();
        }
        assertFalse(state.finished());
        assertEquals(13, instance().status(state.gameId()).score());
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream().filter(choice -> !choice.equals(correct)).findFirst().orElseThrow();
        var request = new AnswerRequest(state.gameId(), 14, wrong);
        var answer = second.answer(request);
        assertTrue(answer.state().finished());
        assertEquals(13, answer.state().score());
        assertEquals(answer, first.answer(request));
    }

    @Test void timedBatchRefillsAndExpiryKeepTheOriginalDeadline() throws Exception {
        var state = first.start(GameMode.TIMED, QuestionType.CAPITAL, GameLanguage.PT_BR);
        long deadline = state.expiresAt();
        for (int i = 0; i < 12; i++) {
            String choice = state.question().alternatives().getFirst();
            state = (i % 2 == 0 ? first : second).answer(new AnswerRequest(state.gameId(), state.number(), choice)).state();
        }
        assertEquals(12, state.answered());
        assertEquals(deadline, state.expiresAt());
        now = Instant.ofEpochMilli(deadline);
        var result = second.status(state.gameId());
        assertTrue(result.finished()); assertTrue(result.timedOut());
        assertNull(result.question());
        assertEquals(result, first.status(state.gameId()));
        now = now.plusSeconds(301);
        UUID id = state.gameId();
        assertThrows(NoSuchElementException.class, () -> first.status(id));
        assertEquals(1, new PostgresRoundStorage(source).cleanup(now));
    }

    @Test void invalidOwnershipRollsBackAndDeletingAProfileRemovesItsRounds() throws Exception {
        UUID owner = UUID.randomUUID();
        try (var db = source.getConnection(); var insert = db.prepareStatement("insert into player_profiles(id,display_name) values (?,?)")) {
            insert.setObject(1, owner); insert.setString(2, "Test"); insert.executeUpdate();
        }
        var state = first.start(GameMode.CAPITALS, QuestionType.CAPITAL, GameLanguage.EN, owner);
        assertThrows(NoSuchElementException.class, () -> second.status(state.gameId()));
        assertEquals(0, first.status(state.gameId(), owner).score());
        try (var db = source.getConnection(); var delete = db.prepareStatement("delete from player_profiles where id=?")) {
            delete.setObject(1, owner); delete.executeUpdate();
        }
        assertThrows(NoSuchElementException.class, () -> second.status(state.gameId(), owner));
    }

    @Test void oauthAuthorizationRequestIsAvailableToAnotherSessionRepository() {
        verifyHandshake(sessions(), sessions());
    }

    private <S extends org.springframework.session.Session> void verifyHandshake(
            org.springframework.session.SessionRepository<S> first,
            org.springframework.session.SessionRepository<S> second) {
        S session = first.createSession();
        var request = OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://example.test/authorize")
                .clientId("test-client").redirectUri("https://example.test/callback").state("test-state")
                .attributes(attributes -> attributes.put("code_verifier", "test-verifier")).build();
        session.setAttribute("SPRING_SECURITY_SAVED_AUTHORIZATION_REQUEST", request);
        first.save(session);
        var loaded = second.findById(session.getId());
        assertNotNull(loaded);
        OAuth2AuthorizationRequest restored = loaded.getAttribute("SPRING_SECURITY_SAVED_AUTHORIZATION_REQUEST");
        assertEquals(request.getState(), restored.getState());
        assertEquals("test-verifier", restored.getAttribute("code_verifier"));
        second.deleteById(session.getId());
        assertNull(first.findById(session.getId()));
    }

    private JdbcIndexedSessionRepository sessions() {
        var repository = new JdbcIndexedSessionRepository(new org.springframework.jdbc.core.JdbcTemplate(source),
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        repository.setTableName("geoquest_http_session");
        return repository;
    }
}
