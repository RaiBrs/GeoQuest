package com.raibrs.geoquest.service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;

/** One transaction and row lock own each transition, including retry responses. */
public final class PostgresRoundStorage {
    private final DataSource source;
    public PostgresRoundStorage(DataSource source) { this.source = source; }

    public void create(UUID id, UUID owner, byte[] state, Instant expiresAt) {
        try (var db = source.getConnection(); var insert = db.prepareStatement(
                "insert into game_rounds(id,owner_id,state,expires_at) values (?,?,?,?)")) {
            insert.setObject(1, id); insert.setObject(2, owner);
            insert.setBytes(3, state); insert.setTimestamp(4, Timestamp.from(expiresAt));
            insert.executeUpdate();
        } catch (SQLException error) { throw unavailable(error); }
    }

    public <T> T update(UUID id, Instant now, Function<byte[], Change<T>> action) {
        if (id == null) throw missing();
        try (var db = source.getConnection()) {
            db.setAutoCommit(false);
            try {
                byte[] saved;
                try (var query = db.prepareStatement("select state,expires_at from game_rounds where id=? for update")) {
                    query.setObject(1, id);
                    try (var rows = query.executeQuery()) {
                        if (!rows.next() || !now.isBefore(rows.getTimestamp(2).toInstant())) throw missing();
                        saved = rows.getBytes(1);
                    }
                }
                Change<T> change = action.apply(saved);
                try (var update = db.prepareStatement("update game_rounds set state=?,expires_at=? where id=?")) {
                    update.setBytes(1, change.state()); update.setTimestamp(2, Timestamp.from(change.expiresAt()));
                    update.setObject(3, id); update.executeUpdate();
                }
                db.commit();
                return change.result();
            } catch (RuntimeException | SQLException error) {
                db.rollback();
                throw error;
            }
        } catch (SQLException error) { throw unavailable(error); }
    }

    public int cleanup(Instant now) {
        try (var db = source.getConnection(); var delete = db.prepareStatement("delete from game_rounds where expires_at<=?")) {
            delete.setTimestamp(1, Timestamp.from(now)); return delete.executeUpdate();
        } catch (SQLException error) { throw unavailable(error); }
    }

    public void removePlayer(UUID owner) {
        try (var db = source.getConnection(); var delete = db.prepareStatement("delete from game_rounds where owner_id=?")) {
            delete.setObject(1, owner); delete.executeUpdate();
        } catch (SQLException error) { throw unavailable(error); }
    }

    public void verifySchema() {
        try (var db = source.getConnection(); var query = db.prepareStatement("select id,owner_id,state,expires_at from game_rounds limit 0")) {
            query.executeQuery().close();
        } catch (SQLException error) { throw unavailable(error); }
    }

    private static NoSuchElementException missing() {
        return new NoSuchElementException("Round not found. Start a new game.");
    }
    private static RoundStorageUnavailable unavailable(SQLException error) {
        System.getLogger(PostgresRoundStorage.class.getName()).log(System.Logger.Level.WARNING,
                "Round storage failure; SQL state=" + error.getSQLState());
        return new RoundStorageUnavailable();
    }
    public record Change<T>(byte[] state, Instant expiresAt, T result) {}
}
