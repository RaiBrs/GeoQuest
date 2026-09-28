package com.raibrs.geoquest.model;

// Stores a stable snapshot of one completed round.
public class RankingEntry {
    private final String playerName;
    private final int score;
    private final int totalQuestions;

    public RankingEntry(String playerName, int score, int totalQuestions) {
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

        this.playerName = playerName;
        this.score = score;
        this.totalQuestions = totalQuestions;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }
}
