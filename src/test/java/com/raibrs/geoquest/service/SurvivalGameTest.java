package com.raibrs.geoquest.service;

import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.AnswerView;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SurvivalGameTest {
    @Test void survivalUsesTheWholeCatalogAndEndsAfterTheLastCountry() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService(new Random(1)));
        var state = games.start(GameMode.SURVIVAL);
        assertEquals(40, state.total());
        Set<String> questions = new HashSet<>();
        AnswerView last = null;
        AnswerRequest request = null;
        for (int number = 1; number <= 40; number++) {
            assertFalse(state.finished());
            assertTrue(questions.add(state.question().text()), "A country must not be asked twice");
            request = new AnswerRequest(state.gameId(), number, TestCountriesClient.answerFor(state.question().text()));
            last = games.answer(request);
            state = last.state();
            assertEquals(number, state.score());
        }
        assertTrue(state.finished());
        assertNull(state.question());
        assertEquals(40, state.answered());
        assertEquals(last, games.answer(request), "The final retry must not score again");
    }

    @Test void theFirstMistakeAfterTenQuestionsStillEndsSurvival() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService(new Random(2)));
        var state = games.start(GameMode.SURVIVAL);
        for (int number = 1; number <= 11; number++) {
            state = games.answer(new AnswerRequest(state.gameId(), number,
                    TestCountriesClient.answerFor(state.question().text()))).state();
        }
        assertFalse(state.finished());
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream().filter(choice -> !choice.equals(correct)).findFirst().orElseThrow();
        var result = games.answer(new AnswerRequest(state.gameId(), 12, wrong));
        assertTrue(result.state().finished());
        assertEquals(11, result.state().score());
        assertEquals(12, result.state().answered());
        assertEquals(1, result.state().mistakes().size());
    }

    @Test void survivalUsesEveryEligibleSubjectInBothDirectionsAndLanguages() throws Exception {
        var catalog = new ArrayList<>(new TestCountriesClient().fetchAllCountries(80));
        catalog.add(new Country("Shared", "Capital 0", "Compartilhado", "Capital 0"));
        catalog.add(new Country("Untranslated", "Untranslated capital", null, null));
        var original = List.copyOf(catalog);
        var generator = new QuestionService(new Random(3));
        for (var language : GameLanguage.values()) {
            for (var type : QuestionType.values()) {
                var questions = generator.createSurvivalQuestions(catalog, type, language);
                int expected = language == GameLanguage.EN ? 42 : 41;
                if (type == QuestionType.COUNTRY) expected -= 2;
                assertEquals(expected, questions.size());
                assertEquals(expected, questions.stream().map(Question::getText).distinct().count());
                for (var question : questions) {
                    var choices = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                    choices.addAll(question.getAlternatives());
                    assertEquals(4, choices.size());
                    assertTrue(question.getAlternatives().contains(question.getCorrectAnswer()));
                }
            }
        }
        assertEquals(original, catalog);
    }

    @Test void smallCatalogsNeedFourDistinctChoicesAndDuplicateSubjectsAreAskedOnce() {
        var countries = List.of(new Country("A", "a"), new Country("B", "b"),
                new Country("C", "c"), new Country("D", "d"), new Country("A", "a"));
        var generator = new QuestionService(new Random(4));
        assertEquals(4, generator.createSurvivalQuestions(countries, QuestionType.CAPITAL, GameLanguage.EN).size());
        assertThrows(IllegalArgumentException.class,
                () -> generator.createSurvivalQuestions(countries.subList(0, 3), QuestionType.CAPITAL, GameLanguage.EN));
    }
}
