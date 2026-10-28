package com.raibrs.geoquest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.dto.GameDtos;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.UUID;
import com.raibrs.geoquest.model.GameMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {
    @Test
    void timedRoundLoadsFreshBatchesPastTenAndStopsOnlyAtItsDeadline() throws Exception {
        var time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return time.get(); }
        };
        var client = new TestCountriesClient();
        var timedGames = new GameService(client, new QuestionService(new java.util.Random(1)), clock);
        var state = timedGames.start(GameMode.TIMED);
        var deadline = state.expiresAt();
        assertNull(state.total());
        var seen = new java.util.HashSet<String>();
        for (int number = 1; number <= 25; number++) {
            assertEquals(number, state.number());
            seen.add(state.question().text());
            var submission = correctRequest(state);
            var answer = timedGames.answer(submission);
            assertEquals(answer, timedGames.answer(submission), "Retries cannot advance twice");
            state = answer.state();
            assertFalse(state.finished());
            assertEquals(number, state.score());
            assertEquals(number, state.answered());
            assertEquals(deadline, state.expiresAt());
        }
        assertTrue(seen.size() > 10, "The next batch must not loop over the same ten questions");
        assertEquals(1, client.calls, "Refilling uses the cached country catalog");
        var lateAnswer = correctRequest(state);
        time.updateAndGet(now -> now.plusSeconds(60));
        var result = timedGames.answer(lateAnswer).state();
        assertTrue(result.timedOut());
        assertTrue(result.finished());
        assertEquals(25, result.score());
        assertEquals(25, result.answered());
        assertTrue(result.mistakes().isEmpty());
    }
    @Test
    void deadlineEndsIdleRoundsAndLateAnswersCannotScore() throws Exception {
        var time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return time.get(); }
        };
        var timedGames = new GameService(new TestCountriesClient(), new QuestionService(), clock);
        var state = timedGames.start(GameMode.TIMED);
        var request = correctRequest(state);
        time.updateAndGet(now -> now.plusSeconds(60));
        var status = timedGames.status(state.gameId());
        assertTrue(status.finished());
        assertTrue(status.timedOut());
        assertNull(status.question());
        var answer = timedGames.answer(request);
        assertFalse(answer.correct());
        assertEquals(0, answer.state().score());
        assertEquals(status, answer.state());
        assertEquals(answer, timedGames.answer(request));
    }

    private final TestCountriesClient client = new TestCountriesClient();
    private final GameService games = new GameService(client, new QuestionService());

    @Test
    void createsIndependentAnonymousRoundsAndReusesTheCatalog() throws Exception {
        var first = games.start();
        var second = games.start();
        assertNotEquals(first.gameId(), second.gameId());
        assertEquals(1, client.calls);
        assertEquals(10, first.total());
        assertEquals(0, first.score());
        assertEquals(4, first.question().alternatives().size());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(first));
        assertFalse(json.get("question").has("correctAnswer"));
        assertFalse(json.has("playerName"));
        assertEquals(1, games.answer(correctRequest(first)).state().score());
        assertEquals(1, games.answer(correctRequest(second)).state().score());
    }

    @Test
    void completesTenQuestionsAndReplaysTheFinalAnswer() throws Exception {
        var state = games.start();
        GameDtos.AnswerRequest last = null;
        GameDtos.AnswerView response = null;
        for (int number = 1; number <= 10; number++) {
            assertEquals(number, state.number());
            last = correctRequest(state);
            response = games.answer(last);
            state = response.state();
            assertEquals(number, state.score());
        }
        assertTrue(state.finished());
        assertNull(state.question());
        assertEquals(response, games.answer(last));
    }

    @Test
    void rejectsInvalidAndOutOfOrderAnswersWithoutChangingTheRound() throws Exception {
        var state = games.start();
        assertThrows(IllegalArgumentException.class, () -> games.answer(
                new GameDtos.AnswerRequest(state.gameId(), 1, "invalid")));
        assertThrows(IllegalStateException.class, () -> games.answer(
                new GameDtos.AnswerRequest(state.gameId(), 2, state.question().alternatives().getFirst())));
        var request = correctRequest(state);
        var first = games.answer(request);
        assertEquals(first, games.answer(request));
        assertEquals(1, first.state().score());
    }

    @Test
    void firstWrongAnswerEndsRoundAndCannotBeChanged() throws Exception {
        var state = games.start();
        var correct = correctRequest(state);
        String wrong = state.question().alternatives().stream()
                .filter(choice -> !choice.equals(correct.choice())).findFirst().orElseThrow();
        var request = new GameDtos.AnswerRequest(state.gameId(), 1, wrong);
        var answer = games.answer(request);
        assertFalse(answer.correct());
        assertTrue(answer.state().finished());
        assertEquals(0, answer.state().score());
        assertEquals(answer, games.answer(request));
        assertThrows(IllegalStateException.class, () -> games.answer(correct));
    }

    @Test
    void missingAnswersAndUnknownRoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> games.answer(null));
        assertThrows(NoSuchElementException.class,
                () -> games.answer(new GameDtos.AnswerRequest(UUID.randomUUID(), 1, "Capital")));
    }

    @Test
    void failedCountryFetchCanBeRetried() throws Exception {
        client.failing = true;
        assertThrows(IOException.class, games::start);
        client.failing = false;
        assertNotNull(games.start().question());
        assertEquals(2, client.calls);
    }

    private GameDtos.AnswerRequest correctRequest(GameDtos.GameView state) {
        return new GameDtos.AnswerRequest(state.gameId(), state.number(),
                TestCountriesClient.answerFor(state.question().text()));
    }
}
