package com.raibrs.geoquest.service;

import com.raibrs.geoquest.dto.GameDtos.*;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MistakeReviewTest {
    @Test
    void reviewIsAvailableOnlyAtTheEndAndRetriesDoNotDuplicateMistakes() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService());
        var state = games.start(GameMode.CAPITALS);
        AnswerView firstAnswer = null;
        for (int number = 1; number <= 10; number++) {
            String correct = TestCountriesClient.answerFor(state.question().text());
            String wrong = state.question().alternatives().stream().filter(a -> !a.equals(correct)).findFirst().orElseThrow();
            var request = new AnswerRequest(state.gameId(), number, number == 1 || number == 3 ? wrong : correct);
            var answer = games.answer(request);
            assertEquals(answer, games.answer(request));
            if (number == 1) firstAnswer = answer;
            state = answer.state();
            if (number < 10) assertTrue(state.mistakes().isEmpty());
        }
        assertEquals(2, state.mistakes().size());
        assertEquals(1, state.mistakes().getFirst().number());
        assertEquals(3, state.mistakes().getLast().number());
        assertEquals(firstAnswer.correctAnswer(), state.mistakes().getFirst().correctAnswer());
        assertNotEquals(state.mistakes().getFirst().selectedAnswer(), state.mistakes().getFirst().correctAnswer());
        var finalMistakes = state.mistakes();
        assertThrows(UnsupportedOperationException.class, finalMistakes::clear);
    }

    @Test
    void survivalShowsTheMistakeThatEndedTheRound() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService());
        var state = games.start();
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream().filter(a -> !a.equals(correct)).findFirst().orElseThrow();
        var result = games.answer(new AnswerRequest(state.gameId(), 1, wrong)).state();
        assertTrue(result.finished());
        assertEquals(1, result.mistakes().size());
        assertEquals(state.question().text(), result.mistakes().getFirst().text());
        assertEquals(wrong, result.mistakes().getFirst().selectedAnswer());
    }
}
