package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PostgresRankingRepository implements RankingRepository {
    private static final String UPSERT = """
            INSERT INTO ranking_entries
                (player_key, player_name, score, total_questions, played_at_epoch_millis)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (player_key) DO UPDATE SET
                player_name = EXCLUDED.player_name,
                score = EXCLUDED.score,
                total_questions = EXCLUDED.total_questions,
                played_at_epoch_millis = EXCLUDED.played_at_epoch_millis
            WHERE EXCLUDED.score > ranking_entries.score
               OR (EXCLUDED.score = ranking_entries.score
                   AND EXCLUDED.played_at_epoch_millis >= ranking_entries.played_at_epoch_millis)
            """;
    private static final String SELECT_TOP = """
            SELECT player_name, score, total_questions, played_at_epoch_millis
            FROM ranking_entries
            ORDER BY score DESC, played_at_epoch_millis DESC, player_key ASC
            LIMIT ?
            """;
    private final DataSource dataSource;

    public PostgresRankingRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "Data source must not be null.");
    }

    @Override
    public void saveBest(RankingEntry entry) throws SQLException {
        Objects.requireNonNull(entry, "Ranking entry must not be null.");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, RankingEntry.playerKey(entry.getPlayerName()));
            statement.setString(2, entry.getPlayerName());
            statement.setInt(3, entry.getScore());
            statement.setInt(4, entry.getTotalQuestions());
            statement.setLong(5, entry.getPlayedAtEpochMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public List<RankingEntry> findTop(int limit) throws SQLException {
        if (limit < 1) {
            throw new IllegalArgumentException("Ranking limit must be positive.");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_TOP)) {
            statement.setInt(1, limit);
            try (var rows = statement.executeQuery()) {
                List<RankingEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    try {
                        entries.add(new RankingEntry(rows.getString("player_name"), rows.getInt("score"),
                                rows.getInt("total_questions"), rows.getLong("played_at_epoch_millis")));
                    } catch (IllegalArgumentException exception) {
                        throw new SQLException("Stored ranking data is invalid. Check database migrations.",
                                "22000", exception);
                    }
                }
                return List.copyOf(entries);
            }
        }
    }

    @Override
    public boolean deleteByPlayerName(String playerName) throws SQLException {
        String playerKey = RankingEntry.playerKey(playerName);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM ranking_entries WHERE player_key = ?")) {
            statement.setString(1, playerKey);
            return statement.executeUpdate() > 0;
        }
    }

}
