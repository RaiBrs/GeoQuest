package com.raibrs.geoquest.account;

import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches="jdbc:postgresql://.*")
class PostgresAccountsTest {
    private PostgresAccounts accounts;
    private Connection db;
    @BeforeEach void prepare() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        db = DriverManager.getConnection(url, "geoquest_test", "test-only-password");
        assertEquals("geoquest_test", db.getCatalog(), "Integration tests require the dedicated disposable database");
        try (var statement = db.createStatement()) {
            statement.execute(Files.readString(Path.of("database/001_accounts.sql")));
            statement.execute("truncate player_profiles cascade");
        }
        var config = new AccountConfig("http://localhost:8080", url, "geoquest_test", "test-only-password", "fake", "fake", "fake", "fake");
        accounts = new PostgresAccounts(config, new OAuthIdentity(config));
    }
    @AfterEach void close() throws Exception { if (db != null) db.close(); }
    @Test void sameProviderIdentityIsStableAndDifferentProvidersAreNotMergedByName() throws Exception {
        var google = accounts.login(new OAuthIdentity.Identity("google", "123", "Rai"));
        var again = accounts.login(new OAuthIdentity.Identity("google", "123", "Rai renamed"));
        var github = accounts.login(new OAuthIdentity.Identity("github", "123", "Rai"));
        assertEquals(google.id(), again.id());
        assertNotEquals(google.id(), github.id());
        String hash = AccountController.hash("token");
        accounts.createSession(again, hash);
        assertEquals("Rai renamed", accounts.findSession(hash).displayName());
        accounts.logout(hash);
        assertNull(accounts.findSession(hash));
    }
    @Test void rankingIsIdempotentAndDeletionCascadesToAllPrivateData() throws Exception {
        var user = accounts.login(new OAuthIdentity.Identity("google", "123", "Rai"));
        var other = accounts.login(new OAuthIdentity.Identity("github", "456", "Other"));
        var state = result(7, GameMode.CAPITALS);
        accounts.saveResult(user, state, QuestionType.CAPITAL);
        accounts.saveResult(user, state, QuestionType.CAPITAL);
        accounts.saveResult(user, result(9, GameMode.CAPITALS), QuestionType.CAPITAL);
        accounts.saveResult(other, result(8, GameMode.CAPITALS), QuestionType.CAPITAL);
        assertEquals(List.of(9, 8), accounts.leaderboard(GameMode.CAPITALS, QuestionType.CAPITAL).stream().map(AccountsBackend.Leader::score).toList());
        assertTrue(accounts.leaderboard(GameMode.TIMED, QuestionType.CAPITAL).isEmpty());
        String hash = AccountController.hash("token");
        accounts.createSession(user, hash);
        accounts.delete(user);
        assertNull(accounts.findSession(hash));
        assertEquals(List.of("Other"), accounts.leaderboard(GameMode.CAPITALS, QuestionType.CAPITAL).stream().map(AccountsBackend.Leader::displayName).toList());
        for (String table : List.of("player_identities", "player_results", "player_sessions")) {
            try (var query = db.prepareStatement("select count(*) from " + table + " where user_id=?")) {
                query.setObject(1, user.id());
                try (var rows = query.executeQuery()) { rows.next(); assertEquals(0, rows.getInt(1)); }
            }
        }
        assertThrows(java.io.IOException.class, () -> accounts.saveResult(user, state, QuestionType.CAPITAL));
    }
    @Test void databaseRejectsExpiredSessions() throws Exception {
        var user = accounts.login(new OAuthIdentity.Identity("google", "123", "Rai"));
        String hash = AccountController.hash("token");
        accounts.createSession(user, hash);
        try (var update = db.prepareStatement("update player_sessions set expires_at=now()-interval '1 second' where token_hash=?")) {
            update.setString(1, hash); update.executeUpdate();
        }
        assertNull(accounts.findSession(hash));
    }
    private GameView result(int score, GameMode mode) {
        return new GameView(UUID.randomUUID(), 10, 10, score, true, null, mode, null, false, List.of(), 10);
    }
}
