package com.raibrs.geoquest.account;

import com.raibrs.geoquest.dto.GameDtos.*;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountControllerTest {
    static class Backend implements AccountsBackend {
        User user = new User(UUID.randomUUID(), "Rai", Instant.now());
        int exchanges, deleted;
        String hash;
        public User exchange(String provider, String code, String verifier) { exchanges++; return user; }
        public void createSession(User user, String tokenHash) { hash = tokenHash; }
        public User findSession(String tokenHash) { return user; }
        public void logout(String tokenHash) {}
        public void delete(User user) { deleted++; }
        public void saveResult(User user, GameView state, QuestionType type) {}
        public List<Leader> leaderboard(GameMode mode, QuestionType type) { return List.of(new Leader("Rai", 10, 10)); }
    }
    static AccountConfig config() { return new AccountConfig("https://game.example", "jdbc:postgresql://localhost/g", "u", "p", "id", "secret", "id2", "secret2"); }

    @Test void oauthStateMismatchCannotCreateASessionAndSecretsNeverReachRedirects() throws Exception {
        var backend = new Backend();
        try (var http = TestHttpServer.start(new AccountController(config(), backend)); var client = HttpClient.newHttpClient()) {
            var start = http.get("/auth/login?provider=google");
            assertEquals(303, start.statusCode());
            var cookie = start.headers().firstValue("Set-Cookie").orElseThrow();
            assertTrue(cookie.contains("HttpOnly; SameSite=Lax; Secure"));
            var location = start.headers().firstValue("Location").orElseThrow();
            assertFalse(location.contains("secret"));
            String base = start.uri().getScheme() + "://" + start.uri().getAuthority();
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/callback?code=code&state=wrong"))
                    .header("Cookie", cookie.split(";", 2)[0]).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(303, response.statusCode());
            assertEquals("/?login=failed", response.headers().firstValue("Location").orElseThrow());
            assertEquals(0, backend.exchanges);
        }
    }
    @Test void successfulCallbackSetsAnOpaqueCookieAndPersistsOnlyItsHash() throws Exception {
        var backend = new Backend();
        try (var http = TestHttpServer.start(new AccountController(config(), backend)); var client = HttpClient.newHttpClient()) {
            var start = http.get("/auth/login?provider=github");
            String flow = start.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            String state = flow.split("=", 2)[1].split("\\.")[1];
            String base = start.uri().getScheme() + "://" + start.uri().getAuthority();
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/auth/callback?code=code&state=" + state))
                    .header("Cookie", flow).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(303, response.statusCode());
            String session = response.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("geoquest_session=")).findFirst().orElseThrow();
            String token = session.split(";", 2)[0].split("=", 2)[1];
            assertNotEquals(token, backend.hash);
            assertEquals(AccountController.hash(token), backend.hash);
            assertEquals(1, backend.exchanges);
        }
    }
    @Test void deletionRequiresOriginConfirmationAndRecentAuthentication() throws Exception {
        var backend = new Backend();
        try (var http = TestHttpServer.start(new AccountController(config(), backend)); var client = HttpClient.newHttpClient()) {
            String endpoint = http.get("/api/account").uri().resolve("/api/account/delete").toString();
            for (String origin : List.of("https://evil.example", "https://game.example")) {
                var request = HttpRequest.newBuilder(URI.create(endpoint)).header("Origin", origin)
                        .header("Cookie", "geoquest_session=" + "a".repeat(43)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"DELETE\"}")).build();
                var result = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(origin.contains("evil") ? 403 : 200, result.statusCode());
            }
            assertEquals(1, backend.deleted);
            backend.user = new AccountsBackend.User(backend.user.id(), "Rai", Instant.now().minusSeconds(601));
            var request = HttpRequest.newBuilder(URI.create(endpoint)).header("Origin", "https://game.example")
                    .header("Cookie", "geoquest_session=" + "a".repeat(43)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"confirmation\":\"DELETE\"}")).build();
            assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, backend.deleted);
        }
    }
}
