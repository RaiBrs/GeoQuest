package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionServiceTest {

    private final QuestionService questionService = new QuestionService(new Random(0));

    @Test
    void createsCapitalQuestionsWithoutRepeatingCountries() {
        List<Country> countries = List.of(
                new Country("Brazil", "Brasília"),
                new Country("Japan", "Tokyo"),
                new Country("Chile", "Santiago"));

        List<Question> questions = questionService.createCapitalQuestions(countries, 3);
        Set<String> questionTexts = questions.stream()
                .map(Question::getText)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(3, questions.size());
        assertEquals(Set.of(
                "What is the capital of Brazil?",
                "What is the capital of Japan?",
                "What is the capital of Chile?"), questionTexts);
        assertTrue(questions.stream().anyMatch(question -> question.isCorrectAnswer("brasília")));
        assertTrue(questions.stream().anyMatch(question -> question.isCorrectAnswer("  Tokyo  ")));
        assertFalse(questions.stream().anyMatch(question -> question.isCorrectAnswer("Rio de Janeiro")));
    }

    @Test
    void rejectsAnInvalidQuestionCount() {
        List<Country> countries = List.of(new Country("Brazil", "Brasília"));

        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(List.of(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(countries, 0));
        assertThrows(IllegalArgumentException.class,
                () -> questionService.createCapitalQuestions(countries, 2));
    }
}
