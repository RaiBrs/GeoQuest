package com.raibrs.geoquest.account;

import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.QuestionType;
import java.io.IOException;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Persistent identities, hashed sessions and server-calculated scores. All SQL parameters are bound. */
public final class PostgresAccounts implements AccountsBackend {
    private final AccountConfig config;
    private final OAuthIdentity identity;
    public PostgresAccounts(AccountConfig config, OAuthIdentity identity) { this.config = config; this.identity = identity; }
    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.databaseUser());
        properties.setProperty("password", config.databasePassword());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        properties.setProperty("ApplicationName", "GeoQuest");
        return DriverManager.getConnection(config.databaseUrl(), properties);
    }
    private static IOException unavailable() { return new IOException("Account storage is unavailable."); }
    @Override public User exchange(String provider, String code, String verifier) throws IOException, InterruptedException {
        return login(identity.exchange(provider, code, verifier));
    }
    // Package access permits integration tests with verified provider fixtures, not a public login bypass.
    User login(OAuthIdentity.Identity verified) throws IOException {
        try (var db = connect()) {
            db.setAutoCommit(false);
            try {
                try (var lock = db.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    lock.setString(1, verified.provider() + ":" + verified.subject()); lock.execute();
                }
                UUID id = null;
                try (var query = db.prepareStatement("select user_id from player_identities where provider=? and subject=?")) {
                    query.setString(1, verified.provider()); query.setString(2, verified.subject());
                    try (var result = query.executeQuery()) { if (result.next()) id = result.getObject(1, UUID.class); }
                }
                if (id == null) {
                    id = UUID.randomUUID();
                    try (var insert = db.prepareStatement("insert into player_profiles(id,display_name) values (?,?)")) {
                        insert.setObject(1, id); insert.setString(2, verified.displayName()); insert.executeUpdate();
                    }
                    try (var insert = db.prepareStatement("insert into player_identities(provider,subject,user_id) values (?,?,?)")) {
                        insert.setString(1, verified.provider()); insert.setString(2, verified.subject()); insert.setObject(3, id); insert.executeUpdate();
                    }
                } else try (var update = db.prepareStatement("update player_profiles set display_name=? where id=?")) {
                    update.setString(1, verified.displayName()); update.setObject(2, id); update.executeUpdate();
                }
                db.commit();
                return new User(id, verified.displayName(), Instant.now());
            } catch (SQLException exception) { db.rollback(); throw exception; }
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public void createSession(User user, String hash) throws IOException {
        try (var db = connect()) {
            db.setAutoCommit(false);
            try {
                try (var cleanup = db.prepareStatement("delete from player_sessions where expires_at<=now()")) { cleanup.executeUpdate(); }
                try (var insert = db.prepareStatement("insert into player_sessions(token_hash,user_id) values (?,?)")) {
                    insert.setString(1, hash); insert.setObject(2, user.id()); insert.executeUpdate();
                }
                db.commit();
            } catch (SQLException exception) { db.rollback(); throw exception; }
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public User findSession(String hash) throws IOException {
        try (var db = connect(); var query = db.prepareStatement("select p.id,p.display_name,s.created_at from player_sessions s join player_profiles p on p.id=s.user_id where s.token_hash=? and s.expires_at>now()")) {
            query.setString(1, hash);
            try (var result = query.executeQuery()) {
                return result.next() ? new User(result.getObject(1, UUID.class), result.getString(2), result.getTimestamp(3).toInstant()) : null;
            }
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public void logout(String hash) throws IOException {
        try (var db = connect(); var delete = db.prepareStatement("delete from player_sessions where token_hash=?")) {
            delete.setString(1, hash); delete.executeUpdate();
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public void delete(User user) throws IOException {
        try (var db = connect(); var delete = db.prepareStatement("delete from player_profiles where id=?")) {
            delete.setObject(1, user.id()); delete.executeUpdate();
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public void saveResult(User user, GameView state, QuestionType type) throws IOException {
        if (!state.finished()) return;
        try (var db = connect(); var insert = db.prepareStatement("insert into player_results(game_id,user_id,mode,question_type,score,answered) values (?,?,?,?,?,?) on conflict(game_id) do nothing")) {
            insert.setObject(1, state.gameId()); insert.setObject(2, user.id()); insert.setString(3, state.mode().name());
            insert.setString(4, type.name()); insert.setInt(5, state.score()); insert.setInt(6, state.answered()); insert.executeUpdate();
        } catch (SQLException exception) { throw unavailable(); }
    }
    @Override public List<Leader> leaderboard(GameMode mode, QuestionType type) throws IOException {
        String sql = "select display_name,score,answered from (select distinct on (r.user_id) p.display_name,r.score,r.answered,r.created_at,r.user_id from player_results r join player_profiles p on p.id=r.user_id where r.mode=? and r.question_type=? order by r.user_id,r.score desc,r.created_at) best order by score desc,created_at,user_id limit 20";
        try (var db = connect(); var query = db.prepareStatement(sql)) {
            query.setString(1, mode.name()); query.setString(2, type.name());
            List<Leader> leaders = new ArrayList<>();
            try (var result = query.executeQuery()) { while (result.next()) leaders.add(new Leader(result.getString(1), result.getInt(2), result.getInt(3))); }
            return List.copyOf(leaders);
        } catch (SQLException exception) { throw unavailable(); }
    }
    public void verifySchema() throws IOException {
        try (var db = connect(); var query = db.prepareStatement("select token_hash from player_sessions limit 0")) { query.executeQuery().close(); }
        catch (SQLException exception) { throw new IOException("Apply database/001_accounts.sql before enabling accounts."); }
    }
}
