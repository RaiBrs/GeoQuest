package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingEntryTest {

    // Ensures a completed round keeps the player and score data needed by the ranking.
    @Test
    void createsAnEntryWithValidRoundData() {
        RankingEntry entry = new RankingEntry("Rai", 3, 5);

        assertEquals("Rai", entry.getPlayerName());
        assertEquals(3, entry.getScore());
        assertEquals(5, entry.getTotalQuestions());
    }

    // Prevents ranking entries that cannot identify the player who completed the round.
    @Test
    void rejectsMissingPlayerName() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry(null, 3, 5));
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("   ", 3, 5));
    }

    // Prevents impossible results with fewer than zero correct answers.
    @Test
    void rejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", -1, 5));
    }

    // Prevents results for a round that did not contain any question.
    @Test
    void rejectsMissingTotalQuestions() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", 0, 0));
    }

    // Prevents scores that are higher than the number of questions in the round.
    @Test
    void rejectsScoreAboveTotalQuestions() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", 6, 5));
    }
}
