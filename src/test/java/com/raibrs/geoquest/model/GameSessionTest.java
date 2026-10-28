package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSessionTest {
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void capitalsAdvancesAfterErrorsAndCountsOnlyCorrectAnswers() {
        var session = new GameSession(List.of(
                new Question("First?", "One", ALTERNATIVES),
                new Question("Second?", "Two", ALTERNATIVES)), GameMode.CAPITALS, Clock.systemUTC());
        assertFalse(session.answer("Three").correct());
        assertFalse(session.isFinished());
        assertEquals(2, session.getCurrentQuestionNumber());
        assertEquals(0, session.getScore());
        assertTrue(session.answer("Two").correct());
        assertTrue(session.isFinished());
        assertEquals(1, session.getScore());
    }

    @Test
    void timedRoundContinuesAfterErrorsAndRejectsAnswersAtTheDeadline() {
        var clock = new MutableClock();
        var session = new GameSession(List.of(
                new Question("First?", "One", ALTERNATIVES),
                new Question("Second?", "Two", ALTERNATIVES)), GameMode.TIMED, clock);
        assertEquals(clock.now.plusSeconds(60).toEpochMilli(), session.getExpiresAt());
        session.answer("Three");
        assertFalse(session.isFinished());
        clock.now = clock.now.plusSeconds(59);
        assertFalse(session.isFinished());
        clock.now = clock.now.plusSeconds(1);
        assertTrue(session.isFinished());
        assertTrue(session.isTimedOut());
        assertThrows(IllegalStateException.class, () -> session.answer("Two"));
        assertEquals(0, session.getScore());
        assertEquals(1, session.getMistakes().size(), "Unanswered questions are not mistakes");
    }

    @Test
    void finishingABatchDoesNotFinishATimedRound() {
        var clock = new MutableClock();
        var session = new GameSession(List.of(new Question("First?", "One", ALTERNATIVES)),
                GameMode.TIMED, clock);
        session.answer("One");
        assertFalse(session.isFinished());
        assertEquals("First?", session.getCurrentQuestion().getText());
        assertEquals(2, session.getCurrentQuestionNumber());
        clock.now = clock.now.plusSeconds(60);
        assertTrue(session.isFinished());
        assertTrue(session.isTimedOut());
        assertEquals(1, session.getScore());
    }

    private static final List<String> ALTERNATIVES = List.of("One", "Two", "Three", "Four");

    @Test
    void wrongAnswerEndsRoundWithoutChangingTheScoreAndCannotBeReplayed() {
        GameSession session = new GameSession(List.of(
                new Question("First?", "One", ALTERNATIVES),
                new Question("Second?", "Two", ALTERNATIVES)));
        session.answer("One");

        var result = session.answer("Three");

        assertFalse(result.correct());
        assertEquals("Two", result.correctAnswer());
        assertEquals(1, session.getScore());
        assertTrue(session.isFinished());
        assertFalse(session.hasNextQuestion());
        assertThrows(IllegalStateException.class, session::getCurrentQuestion);
        assertThrows(IllegalStateException.class, () -> session.answer("Two"));
        assertEquals(1, session.getScore());
    }

    @Test
    void invalidChoicesDoNotAdvanceOrEndTheRound() {
        GameSession session = new GameSession(List.of(new Question("First?", "One", ALTERNATIVES)));
        assertThrows(IllegalArgumentException.class, () -> session.answer("unknown"));
        assertThrows(IllegalArgumentException.class, () -> session.answer(null));
        assertEquals(0, session.getScore());
        assertEquals(1, session.getCurrentQuestionNumber());
        assertFalse(session.isFinished());
    }

    // Ensures a correct answer advances exactly one question and increments the score once.
    @Test
    void advancesTheQuestionAndScoreAfterACorrectAnswer() {
        Question firstQuestion = new Question("First?", "One", ALTERNATIVES);
        Question secondQuestion = new Question("Second?", "Two", ALTERNATIVES);
        GameSession session = new GameSession(List.of(firstQuestion, secondQuestion));

        assertEquals(firstQuestion, session.getCurrentQuestion());

        session.answer(session.getCurrentQuestion().getCorrectAnswer());

        assertEquals(1, session.getScore());
        assertEquals(secondQuestion, session.getCurrentQuestion());

        session.answer(session.getCurrentQuestion().getCorrectAnswer());

        assertEquals(2, session.getScore());
        assertFalse(session.hasNextQuestion());
    }

    // Protects callers from reading a question after the session has finished.
    @Test
    void rejectsReadingPastTheLastQuestion() {
        GameSession session = new GameSession(List.of(new Question("First?", "One", ALTERNATIVES)));

        session.answer(session.getCurrentQuestion().getCorrectAnswer());

        assertThrows(IllegalStateException.class, session::getCurrentQuestion);
    }

    // Prevents a round from starting without any question to ask.
    @Test
    void rejectsAnEmptyQuestionList() {
        assertThrows(IllegalArgumentException.class, () -> new GameSession(List.of()));
    }

    @Test
    void rejectsScoringAfterTheSessionEndsWithoutChangingTheScore() {
        GameSession session = new GameSession(List.of(new Question("First?", "One", ALTERNATIVES)));
        session.answer(session.getCurrentQuestion().getCorrectAnswer());

        assertThrows(IllegalStateException.class, () -> session.answer("One"));

        assertEquals(1, session.getScore());
        assertFalse(session.hasNextQuestion());
    }

    @Test
    void preservesItsQuestionsWhenTheOriginalListChanges() {
        Question question = new Question("First?", "One", ALTERNATIVES);
        List<Question> original = new ArrayList<>(List.of(question));
        GameSession session = new GameSession(original);

        original.clear();

        assertEquals(1, session.getTotalQuestions());
        assertEquals(question, session.getCurrentQuestion());
        assertTrue(session.hasNextQuestion());
    }
}
