package com.raibrs.geoquest.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/** Authorization-code + PKCE. Identity is fetched from the provider over HTTPS, never trusted from the browser. */
public final class OAuthIdentity {
    public record Identity(String provider, String subject, String displayName) {}
    private final AccountConfig config;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    public OAuthIdentity(AccountConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    public OAuthIdentity(AccountConfig config, HttpClient http) { this.config = config; this.http = http; }

    public static String authorizationUrl(AccountConfig config, String provider, String state, String challenge) {
        if (!config.providers().contains(provider)) throw new IllegalArgumentException("Choose Google or GitHub.");
        String endpoint = provider.equals("google") ? "https://accounts.google.com/o/oauth2/v2/auth" : "https://github.com/login/oauth/authorize";
        return endpoint + "?" + form(Map.of("client_id", config.clientId(provider), "redirect_uri", config.appOrigin() + "/auth/callback",
                "response_type", "code", "scope", provider.equals("google") ? "openid profile" : "read:user",
                "state", state, "code_challenge", challenge, "code_challenge_method", "S256"));
    }

    public Identity exchange(String provider, String code, String verifier) throws IOException, InterruptedException {
        if (!config.providers().contains(provider)) throw new IllegalArgumentException("Choose Google or GitHub.");
        URI tokenUri = URI.create(provider.equals("google") ? "https://oauth2.googleapis.com/token" : "https://github.com/login/oauth/access_token");
        var tokenRequest = HttpRequest.newBuilder(tokenUri).timeout(Duration.ofSeconds(10)).header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("client_id", config.clientId(provider),
                        "client_secret", config.clientSecret(provider), "code", code, "code_verifier", verifier,
                        "redirect_uri", config.appOrigin() + "/auth/callback", "grant_type", "authorization_code")))).build();
        var tokens = send(tokenRequest);
        String access = tokens.path("access_token").asText("");
        if (access.isBlank() || access.chars().anyMatch(c -> c < 33 || c > 126)) throw new IOException("Sign-in could not be completed.");
        URI userUri = URI.create(provider.equals("google") ? "https://openidconnect.googleapis.com/v1/userinfo" : "https://api.github.com/user");
        var identityRequest = HttpRequest.newBuilder(userUri).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + access).header("Accept", "application/json")
                .header("User-Agent", "GeoQuest").GET().build();
        // Access tokens and provider responses are never persisted or logged.
        return readIdentity(provider, send(identityRequest));
    }
    static Identity readIdentity(String provider, JsonNode data) throws IOException {
        String subject = data.path(provider.equals("google") ? "sub" : "id").asText("");
        if (subject.isBlank() || subject.length() > 256 || subject.chars().anyMatch(Character::isISOControl)) throw new IOException("Sign-in returned an invalid identity.");
        String name = data.path("name").asText("");
        if (name.isBlank()) name = data.path("login").asText("Player");
        name = name.replaceAll("\\p{Cc}", "").strip();
        if (name.isBlank()) name = "Player";
        name = name.codePoints().limit(80).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        return new Identity(provider, subject, name);
    }
    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Sign-in provider unavailable.");
        try { return json.readTree(response.body()); }
        catch (IOException exception) { throw new IOException("Sign-in returned an invalid response."); }
    }
    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }
}
