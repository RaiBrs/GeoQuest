package com.raibrs.geoquest.model;

// Stores a stable snapshot of one completed round.
public class RankingEntry {
    public static final int MAX_PLAYER_NAME_LENGTH = 80;
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
        this.playerName = normalizePlayerName(playerName);

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

        this.score = score;
        this.totalQuestions = totalQuestions;
        this.playedAtEpochMillis = playedAtEpochMillis;
    }

    public static String normalizePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank.");
        }
        if (playerName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Player name must not contain control characters.");
        }
        String normalized = playerName.strip();
        if (normalized.codePointCount(0, normalized.length()) > MAX_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException("Player name must contain at most "
                    + MAX_PLAYER_NAME_LENGTH + " characters.");
        }
        return normalized;
    }

    // Preserve Java's equalsIgnoreCase identity, including Turkish I variants.
    public static String playerKey(String playerName) {
        StringBuilder key = new StringBuilder();
        normalizePlayerName(playerName).codePoints()
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
