package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionTest {

    // Ensures question text and answers are normalized before the game displays or compares them.
    @Test
    void normalizesQuestionFields() {
        Question question = new Question(" What is the capital of Japan? ", " Tokyo ", List.of(
                " Seoul ", " Tokyo ", " Beijing ", " Bangkok "));

        assertEquals("What is the capital of Japan?", question.getText());
        assertEquals("Tokyo", question.getCorrectAnswer());
        assertEquals(List.of("Seoul", "Tokyo", "Beijing", "Bangkok"), question.getAlternatives());
    }

    // Prevents a question without displayable text from entering a game session.
    @Test
    void rejectsMissingQuestionText() {
        assertThrows(IllegalArgumentException.class, () -> new Question(null, "Tokyo", alternatives()));
        assertThrows(IllegalArgumentException.class, () -> new Question("   ", "Tokyo", alternatives()));
    }

    // Prevents a question without a valid expected answer from entering a game session.
    @Test
    void rejectsMissingCorrectAnswer() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("What is the capital?", null, alternatives()));
        assertThrows(IllegalArgumentException.class,
                () -> new Question("What is the capital?", "   ", alternatives()));
    }

    // Prevents the terminal from receiving choices that cannot be represented by A-D.
    @Test
    void rejectsInvalidAlternatives() {
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Question?", "Tokyo", List.of("Tokyo", "Seoul", "Beijing")));
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Question?", "Tokyo", List.of("Tokyo", "Seoul", "Seoul", "Bangkok")));
        assertThrows(IllegalArgumentException.class,
                () -> new Question("Question?", "Tokyo", List.of("Seoul", "Paris", "Beijing", "Bangkok")));
    }

    private List<String> alternatives() {
        return List.of("Tokyo", "Seoul", "Beijing", "Bangkok");
    }
}
