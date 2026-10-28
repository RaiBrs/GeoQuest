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
    public PostgresAccounts(AccountConfig config) { this.config = config; }
    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.databaseUser());
        properties.setProperty("password", config.databasePassword());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "10");
        properties.setProperty("ApplicationName", "GeoQuest");
        return DriverManager.getConnection(config.databaseUrl(), properties);
    }
    private static IOException unavailable(SQLException error) {
        System.getLogger(PostgresAccounts.class.getName()).log(System.Logger.Level.WARNING,
                "Account storage failure; SQL state=" + error.getSQLState() + ", code=" + error.getErrorCode());
        return new IOException("Account storage is unavailable.");
    }
    @Override public User login(SocialIdentity verified) throws IOException {
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
                    try (var insert = db.prepareStatement("insert into player_profiles(id,display_name,email,avatar_url) values (?,?,?,?)")) {
                        insert.setObject(1, id); insert.setString(2, verified.displayName());
                        insert.setString(3, verified.email()); insert.setString(4, verified.avatarUrl()); insert.executeUpdate();
                    }
                    try (var insert = db.prepareStatement("insert into player_identities(provider,subject,user_id) values (?,?,?)")) {
                        insert.setString(1, verified.provider()); insert.setString(2, verified.subject()); insert.setObject(3, id); insert.executeUpdate();
                    }
                } else try (var update = db.prepareStatement("update player_profiles set display_name=?,email=coalesce(?,email),avatar_url=coalesce(?,avatar_url) where id=?")) {
                    update.setString(1, verified.displayName()); update.setString(2, verified.email());
                    update.setString(3, verified.avatarUrl()); update.setObject(4, id); update.executeUpdate();
                }
                db.commit();
                return new User(id, verified.displayName(), Instant.now(), verified.email(), verified.provider(), verified.subject(), verified.avatarUrl());
            } catch (SQLException exception) { db.rollback(); throw exception; }
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public void createSession(User user, String hash) throws IOException {
        try (var db = connect()) {
            db.setAutoCommit(false);
            try {
                try (var cleanup = db.prepareStatement("delete from player_sessions where expires_at<=now()")) { cleanup.executeUpdate(); }
                try (var insert = db.prepareStatement("insert into player_sessions(token_hash,user_id,provider) values (?,?,?)")) {
                    insert.setString(1, hash); insert.setObject(2, user.id()); insert.setString(3, user.provider()); insert.executeUpdate();
                }
                db.commit();
            } catch (SQLException exception) { db.rollback(); throw exception; }
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public User findSession(String hash) throws IOException {
        try (var db = connect(); var query = db.prepareStatement("select p.id,p.display_name,s.created_at,p.email,s.provider,i.subject,p.avatar_url from player_sessions s join player_profiles p on p.id=s.user_id left join player_identities i on i.user_id=p.id and i.provider=s.provider where s.token_hash=? and s.expires_at>now()")) {
            query.setString(1, hash);
            try (var result = query.executeQuery()) {
                return result.next() ? new User(result.getObject(1, UUID.class), result.getString(2), result.getTimestamp(3).toInstant(),
                        result.getString(4), result.getString(5), result.getString(6), result.getString(7)) : null;
            }
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public void logout(String hash) throws IOException {
        try (var db = connect(); var delete = db.prepareStatement("delete from player_sessions where token_hash=?")) {
            delete.setString(1, hash); delete.executeUpdate();
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public void delete(User user) throws IOException {
        try (var db = connect(); var delete = db.prepareStatement("delete from player_profiles where id=?")) {
            delete.setObject(1, user.id()); delete.executeUpdate();
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public void saveResult(User user, GameView state, QuestionType type) throws IOException {
        if (!state.finished()) return;
        try (var db = connect(); var insert = db.prepareStatement("insert into player_results(game_id,user_id,mode,question_type,score,answered) values (?,?,?,?,?,?) on conflict(game_id) do nothing")) {
            insert.setObject(1, state.gameId()); insert.setObject(2, user.id()); insert.setString(3, state.mode().name());
            insert.setString(4, type.name()); insert.setInt(5, state.score()); insert.setInt(6, state.answered()); insert.executeUpdate();
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    @Override public List<Leader> leaderboard(GameMode mode, QuestionType type) throws IOException {
        String sql = "select display_name,score,answered from (select distinct on (r.user_id) p.display_name,r.score,r.answered,r.created_at,r.user_id from player_results r join player_profiles p on p.id=r.user_id where r.mode=? and r.question_type=? order by r.user_id,r.score desc,r.created_at) best order by score desc,created_at,user_id limit 20";
        try (var db = connect(); var query = db.prepareStatement(sql)) {
            query.setString(1, mode.name()); query.setString(2, type.name());
            List<Leader> leaders = new ArrayList<>();
            try (var result = query.executeQuery()) { while (result.next()) leaders.add(new Leader(result.getString(1), result.getInt(2), result.getInt(3))); }
            return List.copyOf(leaders);
        } catch (SQLException exception) { throw unavailable(exception); }
    }
    public void verifySchema() throws IOException {
        try (var db = connect(); var query = db.prepareStatement("select p.email,p.avatar_url,s.provider,i.subject,r.game_id from player_profiles p left join player_sessions s on s.user_id=p.id left join player_identities i on i.user_id=p.id left join player_results r on r.user_id=p.id limit 0")) { query.executeQuery().close(); }
        catch (SQLException exception) {
            unavailable(exception);
            throw new IOException("Cannot verify account storage. Check the database connection and apply database/001_accounts.sql and database/002_oauth_profile.sql before enabling accounts.");
        }
    }
}
