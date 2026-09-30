package com.raibrs.geoquest.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// Stores a stable snapshot of one completed round.
public class RankingEntry {
    private final String playerName;
    private final int score;
    private final int totalQuestions;
    private final long playedAtEpochMillis;

    // Jackson uses this constructor to rebuild a validated entry from the ranking JSON file.
    @JsonCreator
    public RankingEntry(
            @JsonProperty("playerName") String playerName,
            @JsonProperty("score") int score,
            @JsonProperty("totalQuestions") int totalQuestions,
            @JsonProperty("playedAtEpochMillis") long playedAtEpochMillis
    ) {
        // A saved result needs a name so the ranking can identify its player.
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName must not be blank.");
        }

        // The ranking must only persist results that could happen in a completed round.
        if (score < 0) {
            throw new IllegalArgumentException("Score must not be negative.");
        }
        if (totalQuestions < 1) {
            throw new IllegalArgumentException("Total questions must be at least one.");
        }
        if (score > totalQuestions) {
            throw new IllegalArgumentException("Score must not exceed total questions.");
        }
        // A timestamp lets the ranking consistently resolve tied scores by recency.
        if (playedAtEpochMillis < 1) {
            throw new IllegalArgumentException("Played-at timestamp must be positive.");
        }

        this.playerName = playerName.strip();
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.playedAtEpochMillis = playedAtEpochMillis;
    }

    // Getters
    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public long getPlayedAtEpochMillis() {
        return playedAtEpochMillis;
    }
}
