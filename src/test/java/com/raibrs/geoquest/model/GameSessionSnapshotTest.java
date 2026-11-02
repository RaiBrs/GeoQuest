package com.raibrs.geoquest.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionSnapshotTest {
    private final List<Question> questions = List.of(
            new Question("First?", "One", List.of("One", "Two", "Three", "Four")),
            new Question("Second?", "Two", List.of("One", "Two", "Three", "Four")));

    @Test void restoresScoreMistakesAndTheSameNextQuestionFromJson() throws Exception {
        var clock = Clock.systemUTC();
        var original = new GameSession(questions, GameMode.CAPITALS, clock);
        original.answer("Three");
        var json = new ObjectMapper();
        var saved = json.readValue(json.writeValueAsBytes(original.snapshot()), GameSession.Snapshot.class);
        var restored = GameSession.restore(saved, clock, () -> questions);
        assertEquals(2, restored.getCurrentQuestionNumber());
        assertEquals("Second?", restored.getCurrentQuestion().getText());
        assertEquals(original.getMistakes(), restored.getMistakes());
        assertTrue(restored.answer("Two").correct());
        assertTrue(restored.isFinished());
        assertEquals(1, restored.getScore());
        assertFalse(original.isFinished(), "Restoring creates an independent session");
    }

    @Test void restoresTheOriginalDeadlineAndCanRefillTimedBatches() {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var original = new GameSession(questions, GameMode.TIMED, Clock.fixed(start, ZoneOffset.UTC));
        original.answer("One");
        original.answer("Two");
        var restored = GameSession.restore(original.snapshot(),
                Clock.fixed(start.plusSeconds(59), ZoneOffset.UTC), () -> questions);
        assertEquals(original.getExpiresAt(), restored.getExpiresAt());
        assertEquals(3, restored.getCurrentQuestionNumber());
        assertEquals("First?", restored.getCurrentQuestion().getText());
        assertEquals(2, restored.getScore());
        var expired = GameSession.restore(restored.snapshot(),
                Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC), () -> questions);
        assertTrue(expired.isFinished());
        assertTrue(expired.isTimedOut());
        assertThrows(IllegalStateException.class, () -> expired.answer("One"));
    }

    @Test void completedSurvivalRoundStaysFinishedAfterRestoration() {
        var original = new GameSession(questions);
        original.answer("Three");
        var restored = GameSession.restore(original.snapshot(), Clock.systemUTC(), () -> questions);
        assertTrue(restored.isFinished());
        assertNull(restored.getCurrentQuestionOrNull());
        assertEquals(original.getMistakes(), restored.getMistakes());
    }
}
