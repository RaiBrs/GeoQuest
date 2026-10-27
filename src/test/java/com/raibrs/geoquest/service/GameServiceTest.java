package com.raibrs.geoquest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.dto.GameDtos;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {
    private final TestCountriesClient client = new TestCountriesClient();
    private final InMemoryRankingRepository repository = new InMemoryRankingRepository();
    private final GameService games = service(repository);

    private GameService service(InMemoryRankingRepository target) {
        return new GameService(client, new QuestionService(), new RankingService(target,
                Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC)));
    }

    @Test
    void createsIndependentRoundsWithoutLeakingAnswersAndReusesCompleteCatalog() throws Exception {
        var first = games.start(" Rai ");
        var second = games.start("Other");
        assertNotEquals(first.gameId(), second.gameId());
        assertEquals(1, client.calls);
        assertEquals(10, first.total());
        assertEquals(0, first.score());
        assertEquals(4, first.question().alternatives().size());
        assertFalse(new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(first))
                .get("question").has("correctAnswer"));

        var response = games.answer(correctRequest(first));
        assertEquals(1, response.state().score());
        assertFalse(response.state().finished());
        assertThrows(IllegalStateException.class, () -> games.saveResult(second.gameId()));
    }

    @Test
    void completesTenQuestionsAndSavesTheServerScoreOnlyOnce() throws Exception {
        var state = games.start(" Rai ");
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
        games.saveResult(state.gameId());
        games.saveResult(state.gameId());
        assertEquals(1, repository.allEntries().size());
        var saved = repository.allEntries().getFirst();
        assertEquals("Rai", saved.getPlayerName());
        assertEquals(10, saved.getScore());
        assertEquals(1234, saved.getPlayedAtEpochMillis());
    }

    @Test
    void rejectsInvalidAndOutOfOrderAnswersWithoutChangingTheRound() throws Exception {
        var state = games.start("Rai");
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
        var state = games.start("Rai");
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
        games.saveResult(state.gameId());
        assertEquals(0, repository.allEntries().getFirst().getScore());
    }

    @Test
    void failedSaveKeepsTheOriginalResultForRetry() throws Exception {
        List<RankingEntry> attempts = new ArrayList<>();
        var failingRepository = new InMemoryRankingRepository() {
            @Override
            public void saveBest(RankingEntry entry) throws SQLException {
                attempts.add(entry);
                if (attempts.size() == 1) throw new SQLException("Test DB unavailable");
                super.saveBest(entry);
            }
        };
        var service = service(failingRepository);
        var state = service.start("Rai");
        String wrong = state.question().alternatives().stream()
                .filter(choice -> !choice.equals(correctRequest(state).choice())).findFirst().orElseThrow();
        service.answer(new GameDtos.AnswerRequest(state.gameId(), 1, wrong));
        assertThrows(SQLException.class, () -> service.saveResult(state.gameId()));
        service.saveResult(state.gameId());
        service.saveResult(state.gameId());
        assertEquals(2, attempts.size());
        assertSame(attempts.getFirst(), attempts.getLast());
    }

    @Test
    void invalidPlayersDoNotFetchCatalogAndUnknownRoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> games.start(" "));
        assertEquals(0, client.calls);
        assertThrows(NoSuchElementException.class, () -> games.saveResult(UUID.randomUUID()));
    }

    @Test
    void failedCountryFetchCanBeRetried() throws Exception {
        client.failing = true;
        assertThrows(IOException.class, () -> games.start("Rai"));
        client.failing = false;
        assertNotNull(games.start("Rai").question());
        assertEquals(2, client.calls);
    }

    private GameDtos.AnswerRequest correctRequest(GameDtos.GameView state) {
        return new GameDtos.AnswerRequest(state.gameId(), state.number(),
                TestCountriesClient.answerFor(state.question().text()));
    }
}
