package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionServiceTest {

    private final QuestionService questionService = new QuestionService(new Random(0));

    // Ensures a round gives every displayed alternative a unique capital to avoid answer hints.
    @Test
    void createsCapitalQuestionsWithoutRepeatingAlternatives() {
        List<Country> countries = countriesWithDistinctCapitals(40);

        List<Question> questions = questionService.createCapitalQuestions(countries, 10);
        Set<String> questionTexts = questions.stream()
                .map(Question::getText)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> alternatives = questions.stream()
                .flatMap(question -> question.getAlternatives().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(10, questions.size());
        assertEquals(10, questionTexts.size());
        assertEquals(40, alternatives.size());
        assertTrue(questions.stream().allMatch(question -> question.getAlternatives().size() == 4));
        assertTrue(questions.stream().allMatch(question -> question.getAlternatives().contains(
                question.getCorrectAnswer())));
        assertFalse(questions.stream().anyMatch(question -> question.isCorrectAnswer("Not a capital")));
    }

    // Prevents impossible round configurations from reaching the game session.
    @Test
    void rejectsAnInvalidQuestionCount() {
        List<Country> countries = List.of(new Country("Brazil", "Brasília"));

        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(List.of(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(countries, 0));
        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(countries, 2));
        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(countriesWithDistinctCapitals(7), 2));
    }

    private List<Country> countriesWithDistinctCapitals(int count) {
        java.util.ArrayList<Country> countries = new java.util.ArrayList<>();

        for (int index = 1; index <= count; index++) {
            countries.add(new Country("Country " + index, "Capital " + index));
        }

        return countries;
    }

    @Test
    void handlesRepeatedCapitalsIgnoringCaseWithoutMutatingTheInput() {
        List<Country> countries = new ArrayList<>(countriesWithDistinctCapitals(40));
        countries.add(new Country("Duplicate country", "capital 1"));
        countries.add(new Country("Another duplicate", "CAPITAL 2"));
        List<Country> original = List.copyOf(countries);

        List<Question> questions = questionService.createCapitalQuestions(countries, 10);
        List<String> alternatives = questions.stream()
                .flatMap(question -> question.getAlternatives().stream()).toList();

        assertEquals(10, questions.size());
        assertEquals(40, alternatives.size());
        for (int index = 0; index < alternatives.size(); index++) {
            String capital = alternatives.get(index);
            assertTrue(alternatives.subList(index + 1, alternatives.size()).stream()
                    .noneMatch(capital::equalsIgnoreCase));
        }
        assertEquals(original, countries);
    }
}
