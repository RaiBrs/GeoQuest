package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankingEntryTest {

    @Test
    void acceptsTheNameLengthBoundaryCountingUnicodeCodePoints() {
        String name = "😀".repeat(RankingEntry.MAX_PLAYER_NAME_LENGTH);

        assertEquals(name, new RankingEntry(name, 3, 10, 1L).getPlayerName());
        assertThrows(IllegalArgumentException.class,
                () -> new RankingEntry(name + "x", 3, 10, 1L));
        assertThrows(IllegalArgumentException.class, () -> RankingEntry.playerKey("a".repeat(81)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rai\0", "Ra\ti", "Ra\ni", "Ra\u0085i"})
    void rejectsControlCharactersBeforePersistence(String name) {
        assertThrows(IllegalArgumentException.class, () -> new RankingEntry(name, 3, 10, 1L));
        assertThrows(IllegalArgumentException.class, () -> RankingEntry.playerKey(name));
    }

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
