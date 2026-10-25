package com.raibrs.geoquest.model;

// Stores a stable snapshot of one completed round.
public class RankingEntry {
    private final String playerName;
    private final int score;
    private final int totalQuestions;
    private final long playedAtEpochMillis;

    public RankingEntry(
            String playerName,
            int score,
            int totalQuestions,
            long playedAtEpochMillis
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

    // Preserve Java's equalsIgnoreCase identity, including Turkish I variants.
    public static String playerKey(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName must not be blank.");
        }
        StringBuilder key = new StringBuilder();
        playerName.strip().codePoints()
                .map(character -> Character.toLowerCase(Character.toUpperCase(character)))
                .forEach(key::appendCodePoint);
        return key.toString();
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

    public long getPlayedAtEpochMillis() {
        return playedAtEpochMillis;
    }
}
