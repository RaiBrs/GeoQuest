package com.raibrs.geoquest.account;

import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.model.QuestionType;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Keeps existing seven-day database sessions; Spring handles the OAuth handshake. */
public final class AccountSessions {
    public static final String COOKIE = "geoquest_session";
    private final AccountConfig config;
    private final AccountsBackend backend;
    private final SecureRandom random = new SecureRandom();
    public AccountSessions(AccountConfig config, AccountsBackend backend) { this.config = config; this.backend = backend; }
    public boolean enabled() { return backend != null; }
    public AccountConfig config() { return config; }
    public AccountsBackend backend() { return backend; }
    public List<String> providers() { return enabled() ? config.providers() : List.of(); }
    public AccountsBackend.User user(Principal principal) {
        return principal instanceof Authentication auth && auth.getPrincipal() instanceof AccountsBackend.User user ? user : null;
    }
    public AccountsBackend.User find(HttpServletRequest request) throws IOException {
        String token = token(request);
        return enabled() && token != null ? backend.findSession(hash(token)) : null;
    }
    public void signIn(SocialIdentity identity, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var user = backend.login(identity);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        backend.createSession(user, hash(token));
        String old = token(request);
        if (old != null) backend.logout(hash(old));
        cookie(response, COOKIE, token, 7 * 24 * 60 * 60, "/");
        clearHandshake(request, response);
    }
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String token = token(request);
        if (token != null && enabled()) backend.logout(hash(token));
        clear(response);
        clearHandshake(request, response);
    }
    public void clear(HttpServletResponse response) { cookie(response, COOKIE, "", 0, "/"); }
    public void clearHandshake(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        cookie(response, "JSESSIONID", "", 0, "/");
        cookie(response, "geoquest_pkce", "", 0, "/auth/callback");
    }
    public void save(AccountsBackend.User user, GameView state, QuestionType type) throws IOException {
        if (user != null && state.finished()) backend.saveResult(user, state, type);
    }
    private void cookie(HttpServletResponse response, String name, String value, long seconds, String path) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).path(path).maxAge(seconds)
                .httpOnly(true).secure(config != null && config.secureCookies()).sameSite("Lax").build().toString());
    }
    private String token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies)
            if (cookie.getName().equals(COOKIE) && cookie.getValue() != null && cookie.getValue().matches("[A-Za-z0-9_-]{43}")) return cookie.getValue();
        return null;
    }
    public static String hash(String value) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
