package com.raibrs.geoquest.service;

import com.raibrs.geoquest.dto.GameDtos.*;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleTest {
    @Test
    void expiredRoundsAreRejectedEvenBeforeTheScheduledSweep() throws Exception {
        var clock = new TestClock();
        var games = new GameService(new TestCountriesClient(), new QuestionService(), clock);
        var state = games.start();
        clock.advance(1800);
        assertThrows(NoSuchElementException.class, () -> games.status(state.gameId()));
        assertEquals(0, games.cleanupExpiredRounds());
    }

    @Test
    void timedRetentionStartsAtTheDeadlineEvenWhenNobodyPolls() throws Exception {
        var clock = new TestClock();
        var games = new GameService(new TestCountriesClient(), new QuestionService(), clock);
        var state = games.start(GameMode.TIMED);
        clock.advance(359);
        assertEquals(0, games.cleanupExpiredRounds());
        clock.advance(1);
        assertEquals(1, games.cleanupExpiredRounds());
        assertThrows(NoSuchElementException.class, () -> games.status(state.gameId()));
    }
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }

    @Test
    void cleanupRemovesIdleRoundsButActivityRenewsTheirLifetime() throws Exception {
        var clock = new TestClock();
        var games = new GameService(new TestCountriesClient(), new QuestionService(), clock);
        var abandoned = games.start();
        var active = games.start();
        clock.advance(1799);
        assertEquals(0, games.cleanupExpiredRounds());
        games.status(active.gameId());
        clock.advance(1);
        assertEquals(1, games.cleanupExpiredRounds());
        assertThrows(NoSuchElementException.class, () -> games.status(abandoned.gameId()));
        assertNotNull(games.status(active.gameId()).question());
        assertEquals(0, games.cleanupExpiredRounds());
    }

    @Test
    void completedRoundsAllowRetriesForFiveMinutesWithoutExtendingRetention() throws Exception {
        var clock = new TestClock();
        var games = new GameService(new TestCountriesClient(), new QuestionService(), clock);
        var state = games.start();
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream().filter(c -> !c.equals(correct)).findFirst().orElseThrow();
        var request = new AnswerRequest(state.gameId(), 1, wrong);
        var response = games.answer(request);
        clock.advance(299);
        assertEquals(response, games.answer(request));
        clock.advance(1);
        assertEquals(1, games.cleanupExpiredRounds());
        assertThrows(NoSuchElementException.class, () -> games.answer(request));
    }

    @Test
    void simultaneousRetriesScoreOnlyOnce() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService());
        var state = games.start();
        var request = new AnswerRequest(state.gameId(), 1, TestCountriesClient.answerFor(state.question().text()));
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<AnswerView>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) results.add(executor.submit(() -> { gate.await(); return games.answer(request); }));
            gate.countDown();
            var first = results.getFirst().get(5, TimeUnit.SECONDS);
            for (var result : results) assertEquals(first, result.get(5, TimeUnit.SECONDS));
            assertEquals(1, games.status(state.gameId()).score());
        }
    }

    @Test
    void newRoundGenerationDoesNotBlockExistingPlayers() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var questions = new QuestionService() {
            int calls;
            @Override public List<Question> createQuestions(List<Country> countries, int count, QuestionType type, GameLanguage language) {
                if (++calls == 2) {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Release timed out"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                }
                return super.createQuestions(countries, count, type, language);
            }
        };
        var games = new GameService(new TestCountriesClient(), questions);
        var existing = games.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var starting = executor.submit(() -> games.start());
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertNotNull(executor.submit(() -> games.status(existing.gameId())).get(2, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            assertNotNull(starting.get(5, TimeUnit.SECONDS));
        }
    }
}
