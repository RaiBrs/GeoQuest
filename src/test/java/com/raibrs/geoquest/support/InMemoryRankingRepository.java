package com.raibrs.geoquest.support;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// A test double for application/service tests. SQL behavior is covered against real PostgreSQL.
public class InMemoryRankingRepository implements RankingRepository {
    private final Map<String, RankingEntry> entries = new LinkedHashMap<>();

    @Override
    public void saveBest(RankingEntry entry) throws SQLException {
        entries.compute(RankingEntry.playerKey(entry.getPlayerName()), (key, previous) ->
                previous == null || entry.getScore() > previous.getScore()
                        || (entry.getScore() == previous.getScore()
                        && entry.getPlayedAtEpochMillis() >= previous.getPlayedAtEpochMillis())
                        ? entry : previous);
    }

    @Override
    public List<RankingEntry> findTop(int limit) throws SQLException {
        return entries.values().stream().sorted(Comparator
                .comparingInt(RankingEntry::getScore).reversed()
                .thenComparing(Comparator.comparingLong(RankingEntry::getPlayedAtEpochMillis).reversed())
                .thenComparing(entry -> RankingEntry.playerKey(entry.getPlayerName())))
                .limit(limit).toList();
    }

    @Override
    public boolean deleteByPlayerName(String playerName) throws SQLException {
        return entries.remove(RankingEntry.playerKey(playerName)) != null;
    }

    public List<RankingEntry> allEntries() {
        return List.copyOf(entries.values());
    }

    @Override
    public boolean renameByPlayerName(String currentName, String newName) throws SQLException {
        String currentKey = RankingEntry.playerKey(currentName);
        String normalizedName = RankingEntry.normalizePlayerName(newName);
        String newKey = RankingEntry.playerKey(normalizedName);
        RankingEntry current = entries.get(currentKey);
        if (current == null) return false;
        if (!currentKey.equals(newKey) && entries.containsKey(newKey)) {
            throw new SQLException("Player name already exists.", "23505");
        }
        RankingEntry replacement = new RankingEntry(normalizedName, current.getScore(),
                current.getTotalQuestions(), current.getPlayedAtEpochMillis());
        entries.remove(currentKey);
        entries.put(newKey, replacement);
        return true;
    }
}
