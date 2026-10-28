package com.raibrs.geoquest.model;

import com.raibrs.geoquest.api.CountryJsonParser;
import com.raibrs.geoquest.service.QuestionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CountryLocalizationTest {
    @Test
    void readsPortugueseApiNamesAndLocalCapitalLabels() {
        var json = """
                {"data":{"objects":[{"names":{"common":"Germany","translations":{"por":{"common":"Alemanha"}}},
                "codes":{"alpha_2":"DE"},"capitals":[{"name":"Berlin"}]}]}}
                """;
        var country = new CountryJsonParser().parse(json).getFirst();
        assertEquals("Germany", country.getName());
        assertEquals("Berlin", country.getCapital());
        assertEquals("Alemanha", country.getName(GameLanguage.PT_BR));
        assertEquals("Berlim", country.getCapital(GameLanguage.PT_BR));
    }

    @Test
    void usesIsoCountryNameWhenApiTranslationIsMissing() {
        var json = """
                {"data":{"objects":[{"names":{"common":"English alias"},
                "codes":{"alpha_2":"DE"},"capitals":[{"name":"Berlin"}]}]}}
                """;
        var country = new CountryJsonParser().parse(json).getFirst();
        assertEquals("Alemanha", country.getName(GameLanguage.PT_BR));
    }

    @Test
    void localizesPromptsAndAllAlternativesForBothQuestionTypes() {
        var countries = List.of(new Country("Germany", "Berlin"), new Country("Brazil", "Brasilia"),
                new Country("Japan", "Tokyo"), new Country("United Kingdom", "London"));
        var service = new QuestionService(new Random(1));
        var question = service.createQuestions(countries, 1, QuestionType.CAPITAL, GameLanguage.PT_BR).getFirst();
        assertEquals(Set.of("Berlim", "Brasília", "Tóquio", "Londres"), new HashSet<>(question.getAlternatives()));
        assertFalse(question.getText().contains("Germany"));
        assertFalse(question.getText().contains("United Kingdom"));
        var inverted = service.createQuestions(countries, 1, QuestionType.COUNTRY, GameLanguage.PT_BR).getFirst();
        assertEquals(Set.of("Alemanha", "Brasil", "Japão", "Reino Unido"), new HashSet<>(inverted.getAlternatives()));
        assertTrue(inverted.getAlternatives().contains(inverted.getCorrectAnswer()));
        assertEquals("Germany", countries.getFirst().getName(), "Localization preserves source data");
    }

    @Test
    void missingTranslationsNeverSilentlyFallBackToEnglish() {
        var unknown = new Country("Unknown country", "Unknown capital");
        assertFalse(unknown.supports(GameLanguage.PT_BR));
        assertTrue(unknown.supports(GameLanguage.EN));
        assertThrows(IllegalArgumentException.class, () -> new QuestionService().createQuestions(
                List.of(unknown), 1, QuestionType.CAPITAL, GameLanguage.PT_BR));
    }
}
