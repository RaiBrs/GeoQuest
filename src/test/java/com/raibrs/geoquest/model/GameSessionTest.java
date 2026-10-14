package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    private static final List<String> ALTERNATIVES = List.of("One", "Two", "Three", "Four");

    // Ensures a correct answer advances exactly one question and increments the score once.
    @Test
    void advancesTheQuestionAndScoreAfterACorrectAnswer() {
        Question firstQuestion = new Question("First?", "One", ALTERNATIVES);
        Question secondQuestion = new Question("Second?", "Two", ALTERNATIVES);
        GameSession session = new GameSession(List.of(firstQuestion, secondQuestion));

        assertEquals(firstQuestion, session.getCurrentQuestion());

        session.registerCorrectAnswer();

        assertEquals(1, session.getScore());
        assertEquals(secondQuestion, session.getCurrentQuestion());

        session.registerCorrectAnswer();

        assertEquals(2, session.getScore());
        assertFalse(session.hasNextQuestion());
    }

    // Protects callers from reading a question after the session has finished.
    @Test
    void rejectsReadingPastTheLastQuestion() {
        GameSession session = new GameSession(List.of(new Question("First?", "One", ALTERNATIVES)));

        session.registerCorrectAnswer();

        assertThrows(IllegalStateException.class, session::getCurrentQuestion);
    }

    // Prevents a round from starting without any question to ask.
    @Test
    void rejectsAnEmptyQuestionList() {
        assertThrows(IllegalArgumentException.class, () -> new GameSession(List.of()));
    }
}
