package com.raibrs.geoquest.account;

import com.raibrs.geoquest.http.ApiErrors;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.service.GameService;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.security.Principal;
import java.time.Clock;
import java.util.*;

@RestController
public final class AccountController {
    private final AccountSessions accounts;
    private final GameService games;
    private final Clock clock;
    public AccountController(AccountSessions accounts, GameService games, Clock clock) { this.accounts = accounts; this.games = games; this.clock = clock; }
    public record Profile(String displayName) {}
    public record AccountView(boolean enabled, Profile user, List<String> providers) {}
    public record Me(String name, String email, String provider, String providerId, String avatarUrl) {}
    public record DeleteRequest(String confirmation) {}

    @GetMapping("/api/account")
    public AccountView account(Principal principal) {
        var user = accounts.user(principal);
        return new AccountView(accounts.enabled(), user == null ? null : new Profile(user.displayName()), accounts.providers());
    }
    @GetMapping("/api/me")
    public Me me(Principal principal) {
        var user = requireUser(principal);
        return new Me(user.displayName(), user.email(), user.provider(), user.providerId(), user.avatarUrl());
    }
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/api/leaderboard")
    public ResponseEntity<?> leaderboard(@RequestParam(defaultValue = "CAPITALS") GameMode mode,
                                          @RequestParam(defaultValue = "CAPITAL") QuestionType questionType) throws IOException {
        if (!accounts.enabled()) return ApiErrors.response(503, "Accounts are not configured yet.");
        return ResponseEntity.ok(accounts.backend().leaderboard(mode, questionType));
    }
    @PostMapping(value = "/api/logout", consumes = "application/json")
    public Map<String, Boolean> logout(Principal principal, HttpServletRequest request, HttpServletResponse response) throws IOException {
        requireUser(principal);
        accounts.logout(request, response);
        return Map.of("ok", true);
    }
    @PostMapping(value = "/api/account/delete", consumes = "application/json")
    public ResponseEntity<?> delete(@RequestBody DeleteRequest confirmation, Principal principal,
                                     HttpServletRequest request, HttpServletResponse response) throws IOException {
        var user = requireUser(principal);
        if (!"DELETE".equals(confirmation.confirmation())) throw new IllegalArgumentException("Type DELETE to confirm account deletion.");
        if (clock.instant().isAfter(user.authenticatedAt().plusSeconds(600))) return ApiErrors.response(403, "Sign in again before deleting your account.");
        accounts.backend().delete(user);
        games.removePlayerRounds(user.id());
        accounts.clear(response);
        accounts.clearHandshake(request, response);
        return ResponseEntity.ok(Map.of("ok", true));
    }
    private AccountsBackend.User requireUser(Principal principal) {
        var user = accounts.user(principal);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return user;
    }
}
