package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionVariantsTest {
    @Test
    void invertedPortugueseQuestionsKeepCountriesAsAnswers() throws Exception {
        var countries = new TestCountriesClient().fetchAllCountries(80);
        var questions = new QuestionService(new Random(1)).createQuestions(countries, 10, QuestionType.COUNTRY, GameLanguage.PT_BR);
        for (var question : questions) {
            assertTrue(question.getText().startsWith("De qual país Capital "));
            String index = question.getText().replace("De qual país Capital ", "").replace(" é a capital?", "");
            assertEquals("País " + index, question.getCorrectAnswer());
            assertEquals(4, new HashSet<>(question.getAlternatives()).size());
            assertTrue(question.getAlternatives().contains(question.getCorrectAnswer()));
        }
    }

    @Test
    void capitalQuestionsSupportBothLanguagesAndPreserveSourceData() throws Exception {
        var countries = new TestCountriesClient().fetchAllCountries(80);
        var questions = new QuestionService(new Random(1)).createQuestions(countries, 10, QuestionType.CAPITAL, GameLanguage.PT_BR);
        assertTrue(questions.getFirst().getText().startsWith("Qual é a capital de País "));
        assertEquals("Country 0", countries.getFirst().getName());
    }

    @Test
    void invertedQuestionsExcludeAmbiguousSharedCapitals() throws Exception {
        var countries = new ArrayList<>(new TestCountriesClient().fetchAllCountries(80));
        countries.add(new Country("Other country", "Capital 0"));
        var questions = new QuestionService(new Random(1)).createQuestions(countries, 9, QuestionType.COUNTRY, GameLanguage.EN);
        assertTrue(questions.stream().noneMatch(q -> q.getText().equals("Which country has Capital 0 as its capital?")));
    }
}
