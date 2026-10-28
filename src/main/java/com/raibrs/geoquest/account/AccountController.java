package com.raibrs.geoquest.account;

import com.raibrs.geoquest.http.JsonHttp;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.QuestionType;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Browser-facing login and session boundary. Authentication credentials stay server-side. */
public final class AccountController implements HttpHandler {
    public static final Set<String> ROUTES = Set.of("/auth/login", "/auth/callback", "/api/account", "/api/logout", "/api/account/delete", "/api/leaderboard");
    private static final String SESSION = "geoquest_session", PKCE = "geoquest_pkce";
    private final AccountConfig config;
    private final AccountsBackend backend;
    private final Clock clock;
    private final java.util.function.Consumer<UUID> onDelete;
    public AccountController(AccountConfig config, AccountsBackend backend) { this(config, backend, Clock.systemUTC()); }
    public AccountController(AccountConfig config, AccountsBackend backend, Clock clock) { this(config, backend, clock, id -> {}); }
    public AccountController(AccountConfig config, AccountsBackend backend, Clock clock, java.util.function.Consumer<UUID> onDelete) {
        this.config = config; this.backend = backend; this.clock = clock; this.onDelete = onDelete;
    }
    public boolean enabled() { return backend != null; }
    public record Profile(String displayName) {}
    public record AccountView(boolean enabled, Profile user, List<String> providers) {}
    public record DeleteRequest(String confirmation) {}

    @Override public void handle(HttpExchange exchange) throws IOException { JsonHttp.handle(exchange, () -> route(exchange)); }
    private void route(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        if (!ROUTES.contains(path)) { JsonHttp.error(exchange, 404, "Endpoint not found."); return; }
        if (path.equals("/api/account")) {
            if (!JsonHttp.requireMethod(exchange, "GET")) return;
            var user = user(exchange);
            JsonHttp.send(exchange, 200, new AccountView(enabled(), user == null ? null : new Profile(user.displayName()), enabled() ? config.providers() : List.of()));
            return;
        }
        if (!enabled()) { JsonHttp.error(exchange, 503, "Accounts are not configured yet."); return; }
        switch (path) {
            case "/auth/login" -> {
                if (!JsonHttp.requireMethod(exchange, "GET")) return;
                String provider = query(exchange).get("provider");
                if (!config.providers().contains(provider == null ? "" : provider)) throw new IllegalArgumentException("Choose Google or GitHub.");
                String verifier = randomToken();
                String state = randomToken();
                cookie(exchange, PKCE, provider + "." + state + "." + verifier, "/auth/callback", 600);
                redirect(exchange, OAuthIdentity.authorizationUrl(config, provider, state, hash(verifier)));
            }
            case "/auth/callback" -> {
                if (!JsonHttp.requireMethod(exchange, "GET")) return;
                String flow = cookieValue(exchange, PKCE), code = query(exchange).get("code");
                String[] parts = flow == null ? new String[0] : flow.split("\\.");
                cookie(exchange, PKCE, "", "/auth/callback", 0);
                if (parts.length != 3 || !config.providers().contains(parts[0]) || code == null || code.length() > 2048
                        || !parts[2].matches("[A-Za-z0-9_-]{43}")
                        || !MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.UTF_8), query(exchange).getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))) {
                    redirect(exchange, "/?login=failed"); return;
                }
                try {
                    var authenticated = backend.exchange(parts[0], code, parts[2]);
                    String token = randomToken();
                    backend.createSession(authenticated, hash(token));
                    String old = cookieValue(exchange, SESSION);
                    if (old != null) backend.logout(hash(old));
                    cookie(exchange, SESSION, token, "/", 7 * 24 * 60 * 60);
                    redirect(exchange, "/");
                } catch (IOException exception) {
                    System.getLogger(AccountController.class.getName()).log(System.Logger.Level.WARNING, "Sign-in could not be completed.");
                    redirect(exchange, "/?login=failed");
                }
            }
            case "/api/leaderboard" -> {
                if (!JsonHttp.requireMethod(exchange, "GET")) return;
                var query = query(exchange);
                var mode = GameMode.valueOf(query.getOrDefault("mode", "CAPITALS"));
                var type = QuestionType.valueOf(query.getOrDefault("questionType", "CAPITAL"));
                JsonHttp.send(exchange, 200, backend.leaderboard(mode, type));
            }
            case "/api/logout", "/api/account/delete" -> {
                if (!JsonHttp.requireMethod(exchange, "POST") || !requireOrigin(exchange) || !JsonHttp.requireJson(exchange)) return;
                var authenticated = user(exchange);
                if (authenticated == null) { JsonHttp.error(exchange, 401, "Sign in to continue."); return; }
                if (path.equals("/api/account/delete")) {
                    var request = JsonHttp.read(exchange, DeleteRequest.class);
                    if (!"DELETE".equals(request.confirmation())) throw new IllegalArgumentException("Type DELETE to confirm account deletion.");
                    if (clock.instant().isAfter(authenticated.authenticatedAt().plusSeconds(600))) {
                        JsonHttp.error(exchange, 403, "Sign in again before deleting your account."); return;
                    }
                    backend.delete(authenticated);
                    onDelete.accept(authenticated.id());
                } else backend.logout(hash(cookieValue(exchange, SESSION)));
                cookie(exchange, SESSION, "", "/", 0);
                JsonHttp.send(exchange, 200, Map.of("ok", true));
            }
            default -> throw new IllegalStateException("Unexpected account route.");
        }
    }

    public AccountsBackend.User user(HttpExchange exchange) throws IOException, InterruptedException {
        if (!enabled()) return null;
        String token = cookieValue(exchange, SESSION);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return backend.findSession(hash(token));
    }
    public boolean requireOrigin(HttpExchange exchange) throws IOException {
        if (!enabled() || config.appOrigin().equals(exchange.getRequestHeaders().getFirst("Origin"))) return true;
        JsonHttp.error(exchange, 403, "Request origin is not allowed."); return false;
    }
    public void save(AccountsBackend.User user, com.raibrs.geoquest.dto.GameDtos.GameView state, QuestionType type) throws IOException, InterruptedException {
        if (user != null && state.finished()) backend.saveResult(user, state, type);
    }
    private void cookie(HttpExchange exchange, String name, String value, String path, int seconds) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=" + value + "; Path=" + path + "; Max-Age=" + seconds
                + "; HttpOnly; SameSite=Lax" + (config.secureCookies() ? "; Secure" : ""));
    }
    private static String cookieValue(HttpExchange exchange, String name) {
        String raw = exchange.getRequestHeaders().getFirst("Cookie");
        if (raw == null || raw.length() > 8192) return null;
        for (String part : raw.split(";")) {
            String[] entry = part.strip().split("=", 2);
            if (entry.length == 2 && entry[0].equals(name)) return entry[1];
        }
        return null;
    }
    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) for (String part : raw.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (result.put(key, pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "") != null) throw new IllegalArgumentException("Duplicate query parameter.");
        }
        return result;
    }
    public static String hash(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String randomToken() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(303, -1);
    }
}
