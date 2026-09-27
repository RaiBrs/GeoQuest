package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionTest {

    // Ensures question text and answers are normalized before the game displays or compares them.
    @Test
    void normalizesQuestionFields() {
        Question question = new Question(" What is the capital of Japan? ", " Tokyo ");

        assertEquals("What is the capital of Japan?", question.getText());
        assertEquals("Tokyo", question.getCorrectAnswer());
    }

    // Prevents a question without displayable text from entering a game session.
    @Test
    void rejectsMissingQuestionText() {
        assertThrows(IllegalArgumentException.class, () -> new Question(null, "Tokyo"));
        assertThrows(IllegalArgumentException.class, () -> new Question("   ", "Tokyo"));
    }

    // Prevents a question without a valid expected answer from entering a game session.
    @Test
    void rejectsMissingCorrectAnswer() {
        assertThrows(IllegalArgumentException.class, () -> new Question("What is the capital?", null));
        assertThrows(IllegalArgumentException.class, () -> new Question("What is the capital?", "   "));
    }
}
