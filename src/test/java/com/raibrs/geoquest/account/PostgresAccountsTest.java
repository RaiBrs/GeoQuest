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
        String user = System.getenv().getOrDefault("TEST_DATABASE_USER", "geoquest_test");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "test-only-password");
        db = DriverManager.getConnection(url, user, password);
        assertEquals("geoquest_test", db.getCatalog(), "Integration tests require the dedicated disposable database");
        try (var statement = db.createStatement()) {
            statement.execute(Files.readString(Path.of("database/001_accounts.sql")));
            statement.execute(Files.readString(Path.of("database/002_oauth_profile.sql")));
            statement.execute(Files.readString(Path.of("database/005_timed_rankings.sql")));
            statement.execute("truncate player_profiles cascade");
        }
        var config = new AccountConfig("http://localhost:8080", url, user, password, "fake", "fake", "fake", "fake");
        accounts = new PostgresAccounts(config);
    }
    @AfterEach void close() throws Exception { if (db != null) db.close(); }
    @Test void sameProviderIdentityIsStableAndDifferentProvidersAreNotMergedByName() throws Exception {
        var google = accounts.login(new SocialIdentity("google", "123", "Rai", null, null));
        var again = accounts.login(new SocialIdentity("google", "123", "Rai renamed", null, null));
        var github = accounts.login(new SocialIdentity("github", "123", "Rai", null, null));
        assertEquals(google.id(), again.id());
        assertNotEquals(google.id(), github.id());
        String hash = AccountSessions.hash("token");
        accounts.createSession(again, hash);
        assertEquals("Rai renamed", accounts.findSession(hash).displayName());
        accounts.logout(hash);
        assertNull(accounts.findSession(hash));
    }
    @Test void rankingIsIdempotentAndDeletionCascadesToAllPrivateData() throws Exception {
        var user = accounts.login(new SocialIdentity("google", "123", "Rai", null, null));
        var other = accounts.login(new SocialIdentity("github", "456", "Other", null, null));
        var state = result(7, GameMode.TIMED);
        accounts.saveResult(user, state, QuestionType.CAPITAL);
        accounts.saveResult(user, state, QuestionType.CAPITAL);
        accounts.saveResult(user, result(9, GameMode.TIMED), QuestionType.CAPITAL);
        accounts.saveResult(other, result(8, GameMode.TIMED), QuestionType.CAPITAL);
        assertEquals(List.of(9, 8), accounts.leaderboard(GameMode.TIMED, QuestionType.CAPITAL).stream().map(AccountsBackend.Leader::score).toList());
        assertTrue(accounts.leaderboard(GameMode.CAPITALS, QuestionType.CAPITAL).isEmpty());
        assertTrue(accounts.leaderboard(GameMode.SURVIVAL, QuestionType.CAPITAL).isEmpty());
        String hash = AccountSessions.hash("token");
        accounts.createSession(user, hash);
        accounts.delete(user);
        assertNull(accounts.findSession(hash));
        assertEquals(List.of("Other"), accounts.leaderboard(GameMode.TIMED, QuestionType.CAPITAL).stream().map(AccountsBackend.Leader::displayName).toList());
        for (String table : List.of("player_identities", "player_results", "player_sessions")) {
            try (var query = db.prepareStatement("select count(*) from " + table + " where user_id=?")) {
                query.setObject(1, user.id());
                try (var rows = query.executeQuery()) { rows.next(); assertEquals(0, rows.getInt(1)); }
            }
        }
        assertThrows(java.io.IOException.class, () -> accounts.saveResult(user, state, QuestionType.CAPITAL));
    }
    @Test void databaseRejectsExpiredSessions() throws Exception {
        var user = accounts.login(new SocialIdentity("google", "123", "Rai", null, null));
        String hash = AccountSessions.hash("token");
        accounts.createSession(user, hash);
        try (var update = db.prepareStatement("update player_sessions set expires_at=now()-interval '1 second' where token_hash=?")) {
            update.setString(1, hash); update.executeUpdate();
        }
        assertNull(accounts.findSession(hash));
    }
    @Test void concurrentLoginsForOneIdentityCreateOnlyOneProfile() throws Exception {
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<java.util.concurrent.Future<AccountsBackend.User>>();
            for (int i = 0; i < 8; i++) requests.add(executor.submit(() -> accounts.login(new SocialIdentity("github", "concurrent", "Rai", null, null))));
            var ids = new HashSet<UUID>();
            for (var request : requests) ids.add(request.get().id());
            assertEquals(1, ids.size());
            try (var statement = db.createStatement(); var rows = statement.executeQuery("select count(*) from player_profiles")) {
                rows.next(); assertEquals(1, rows.getInt(1));
            }
        }
    }
    @Test void migrationKeepsExistingProfilesSessionsAndScoresAndAddsOptionalMetadata() throws Exception {
        var user = accounts.login(new SocialIdentity("google", "old-subject", "Existing", "old@example.test", "https://example.test/old-avatar"));
        String hash = AccountSessions.hash("old-session");
        // The old application wrote no provider column in its sessions.
        try (var statement = db.prepareStatement("insert into player_sessions(token_hash,user_id) values (?,?)")) {
            statement.setString(1, hash); statement.setObject(2, user.id()); statement.executeUpdate();
        }
        accounts.saveResult(user, result(6, GameMode.TIMED), QuestionType.CAPITAL);
        try (var statement = db.createStatement()) { statement.execute(Files.readString(Path.of("database/002_oauth_profile.sql"))); }
        accounts.verifySchema();
        var restored = accounts.findSession(hash);
        assertEquals(user.id(), restored.id()); assertEquals("google", restored.provider()); assertEquals("old-subject", restored.providerId());
        assertEquals("old@example.test", restored.email());
        accounts.login(new SocialIdentity("google", "old-subject", "Updated", null, null));
        assertEquals("old@example.test", accounts.findSession(hash).email());
        assertEquals(List.of(6), accounts.leaderboard(GameMode.TIMED, QuestionType.CAPITAL).stream().map(AccountsBackend.Leader::score).toList());
    }
    @Test void practiceResultsAreIgnoredAndTimedTypesStaySeparate() throws Exception {
        var user = accounts.login(new SocialIdentity("github", "practice", "Practice", null, null));
        for (var mode : List.of(GameMode.CAPITALS, GameMode.SURVIVAL)) {
            accounts.saveResult(user, result(10, mode), QuestionType.CAPITAL);
            assertTrue(accounts.leaderboard(mode, QuestionType.CAPITAL).isEmpty());
        }
        try (var query = db.createStatement(); var rows = query.executeQuery("select count(*) from player_results")) {
            rows.next(); assertEquals(0, rows.getInt(1));
        }
        accounts.saveResult(user, result(6, GameMode.TIMED), QuestionType.CAPITAL);
        accounts.saveResult(user, result(8, GameMode.TIMED), QuestionType.COUNTRY);
        assertEquals(6, accounts.leaderboard(GameMode.TIMED, QuestionType.CAPITAL).getFirst().score());
        assertEquals(8, accounts.leaderboard(GameMode.TIMED, QuestionType.COUNTRY).getFirst().score());
    }

    @Test void rankingMigrationRemovesOnlyPracticeResultsAndIgnoresOlderWriters() throws Exception {
        var user = accounts.login(new SocialIdentity("github", "migration", "Migration", null, null));
        String hash = AccountSessions.hash("migration-session");
        accounts.createSession(user, hash);
        UUID timedId = UUID.randomUUID();
        // Seed the old format only inside the disposable test database.
        try (var sql = db.createStatement()) { sql.execute("alter table player_results disable trigger player_results_timed_only"); }
        try (var insert = db.prepareStatement("insert into player_results(game_id,user_id,mode,question_type,score,answered) values (?,?,?,'CAPITAL',5,10)")) {
            for (var mode : GameMode.values()) {
                insert.setObject(1, mode == GameMode.TIMED ? timedId : UUID.randomUUID());
                insert.setObject(2, user.id()); insert.setString(3, mode.name()); insert.executeUpdate();
            }
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var sql = db.createStatement()) { sql.execute(Files.readString(Path.of("database/005_timed_rankings.sql"))); }
        }
        assertEquals(user.id(), accounts.findSession(hash).id());
        try (var query = db.createStatement(); var rows = query.executeQuery("select game_id,mode,score,answered from player_results")) {
            assertTrue(rows.next());
            assertEquals(timedId, rows.getObject(1, UUID.class));
            assertEquals("TIMED", rows.getString(2));
            assertEquals(5, rows.getInt(3)); assertEquals(10, rows.getInt(4));
            assertFalse(rows.next());
        }
        try (var insert = db.prepareStatement("insert into player_results(game_id,user_id,mode,question_type,score,answered) values (?,?,?,'CAPITAL',5,10)")) {
            for (var mode : GameMode.values()) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, user.id()); insert.setString(3, mode.name());
                assertEquals(mode.isRanked() ? 1 : 0, insert.executeUpdate());
            }
        }
    }

    private GameView result(int score, GameMode mode) {
        return new GameView(UUID.randomUUID(), 10, 10, score, true, null, mode, null, false, List.of(), 10);
    }
}
