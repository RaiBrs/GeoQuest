package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingEntryTest {

    @Test
    void preservesCaseInsensitiveIdentityIncludingUnicodeAndWhitespace() {
        assertEquals(RankingEntry.playerKey(" Rai "), RankingEntry.playerKey("RAI"));
        assertEquals(RankingEntry.playerKey("İ"), RankingEntry.playerKey("i"));
        assertEquals(RankingEntry.playerKey("ı"), RankingEntry.playerKey("I"));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                RankingEntry.playerKey("ß"), RankingEntry.playerKey("SS"));
        assertThrows(IllegalArgumentException.class, () -> RankingEntry.playerKey(" "));
    }

    // Ensures a completed round keeps the player and score data needed by the ranking.
    @Test
    void createsAnEntryWithValidRoundData() {
        RankingEntry entry = new RankingEntry("Rai", 3, 5, 1_756_684_800_000L);

        assertEquals("Rai", entry.getPlayerName());
        assertEquals(3, entry.getScore());
        assertEquals(5, entry.getTotalQuestions());
        assertEquals(1_756_684_800_000L, entry.getPlayedAtEpochMillis());
    }

    // Ensures stored player names use the same whitespace normalization as terminal input.
    @Test
    void normalizesWhitespaceAroundThePlayerName() {
        RankingEntry entry = new RankingEntry("  Rai  ", 3, 5, 1_756_684_800_000L);

        assertEquals("Rai", entry.getPlayerName());
    }

    // Prevents ranking entries that cannot identify the player who completed the round.
    @Test
    void rejectsMissingPlayerName() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry(null, 3, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("   ", 3, 5, 1L));
    }

    // Prevents impossible results with fewer than zero correct answers.
    @Test
    void rejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", -1, 5, 1L));
    }

    // Prevents results for a round that did not contain any question.
    @Test
    void rejectsMissingTotalQuestions() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", 0, 0, 1L));
    }

    // Prevents scores that are higher than the number of questions in the round.
    @Test
    void rejectsScoreAboveTotalQuestions() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", 6, 5, 1L));
    }

    // Prevents tied results without a valid time from being ordered inconsistently.
    @Test
    void rejectsNonPositivePlayedAtTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry("Rai", 3, 5, 0L));
    }
}
