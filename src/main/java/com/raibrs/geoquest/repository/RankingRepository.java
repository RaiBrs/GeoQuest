package com.raibrs.geoquest.repository;

import com.raibrs.geoquest.model.RankingEntry;
import java.sql.SQLException;
import java.util.List;

public interface RankingRepository {
    // Keep the higher score; on a tie, keep the more recent result.
    void saveBest(RankingEntry entry) throws SQLException;

    List<RankingEntry> findTop(int limit) throws SQLException;

    boolean deleteByPlayerName(String playerName) throws SQLException;

    // Administrative maintenance only; scores and timestamps must remain unchanged.
    boolean renameByPlayerName(String currentName, String newName) throws SQLException;
}
