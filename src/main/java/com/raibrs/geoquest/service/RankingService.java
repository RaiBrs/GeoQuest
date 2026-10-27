package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class RankingService {
    private static final int MAX_DISPLAYED_ENTRIES = 10;
    private final RankingRepository repository;
    private final Clock clock;

    public RankingService(RankingRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public RankingService(RankingRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    public List<RankingEntry> registerResult(String playerName, int score, int totalQuestions)
            throws SQLException {
        saveResult(createResult(playerName, score, totalQuestions));
        return getTopRanking();
    }

    public RankingEntry createResult(String playerName, int score, int totalQuestions) {
        return new RankingEntry(playerName, score, totalQuestions, clock.millis());
    }

    public void saveResult(RankingEntry entry) throws SQLException {
        repository.saveBest(Objects.requireNonNull(entry, "Ranking entry must not be null."));
    }

    public List<RankingEntry> getTopRanking() throws SQLException {
        return repository.findTop(MAX_DISPLAYED_ENTRIES);
    }

    public boolean deleteResultFor(String playerName) throws SQLException {
        RankingEntry.playerKey(playerName);
        return repository.deleteByPlayerName(playerName);
    }

    public void renamePlayer(String currentName, String newName) throws SQLException {
        String current = RankingEntry.normalizePlayerName(currentName);
        String replacement = RankingEntry.normalizePlayerName(newName);
        boolean renamed;
        try {
            renamed = repository.renameByPlayerName(current, replacement);
        } catch (SQLException exception) {
            // PostgreSQL unique_violation: refuse a collision instead of overwriting another player.
            if ("23505".equals(exception.getSQLState())) {
                throw new IllegalStateException("Já existe um jogador com esse nome.", exception);
            }
            throw exception;
        }
        if (!renamed) {
            throw new NoSuchElementException("Não foi encontrado um registro para o nome informado.");
        }
    }
}
