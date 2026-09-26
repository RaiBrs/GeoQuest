package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {

    @Test
    void advancesTheQuestionAndScoreAfterACorrectAnswer() {
        Question firstQuestion = new Question("First?", "One");
        Question secondQuestion = new Question("Second?", "Two");
        GameSession session = new GameSession(List.of(firstQuestion, secondQuestion));

        assertEquals(firstQuestion, session.getCurrentQuestion());

        session.registerCorrectAnswer();

        assertEquals(1, session.getScore());
        assertEquals(secondQuestion, session.getCurrentQuestion());

        session.registerCorrectAnswer();

        assertEquals(2, session.getScore());
        assertFalse(session.hasNextQuestion());
    }

    @Test
    void rejectsReadingPastTheLastQuestion() {
        GameSession session = new GameSession(List.of(new Question("First?", "One")));

        session.registerCorrectAnswer();

        assertThrows(IllegalStateException.class, session::getCurrentQuestion);
    }

    @Test
    void rejectsAnEmptyQuestionList() {
        assertThrows(IllegalArgumentException.class, () -> new GameSession(List.of()));
    }
}
