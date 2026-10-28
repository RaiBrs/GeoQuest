package com.raibrs.geoquest.account;

import java.net.URI;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.raibrs.geoquest.config.EnvironmentConfig;

/** Explicit opt-in: visitor mode needs no database or OAuth credentials. */
public record AccountConfig(String appOrigin, String databaseUrl, String databaseUser, String databasePassword,
                            String googleId, String googleSecret, String githubId, String githubSecret) {
    public static AccountConfig from(Map<String, String> env) {
        if (!Boolean.parseBoolean(env.getOrDefault("ACCOUNTS_ENABLED", "false"))) return null;
        String origin = EnvironmentConfig.required(env, "APP_URL").replaceAll("/+$", "");
        URI app = URI.create(origin);
        boolean local = "http".equals(app.getScheme()) && ("localhost".equals(app.getHost()) || "127.0.0.1".equals(app.getHost()));
        if ((!"https".equals(app.getScheme()) && !local) || app.getHost() == null || app.getUserInfo() != null
                || (app.getPath() != null && !app.getPath().isEmpty()) || app.getQuery() != null || app.getFragment() != null) {
            throw new IllegalArgumentException("APP_URL must be an HTTPS origin (HTTP allowed only for localhost).");
        }
        String database = EnvironmentConfig.required(env, "JDBC_DATABASE_URL");
        if (!database.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException("JDBC_DATABASE_URL must be a PostgreSQL JDBC URL.");
        String googleId = env.getOrDefault("GOOGLE_CLIENT_ID", ""), googleSecret = env.getOrDefault("GOOGLE_CLIENT_SECRET", "");
        String githubId = env.getOrDefault("GITHUB_CLIENT_ID", ""), githubSecret = env.getOrDefault("GITHUB_CLIENT_SECRET", "");
        if (googleId.isBlank() != googleSecret.isBlank() || githubId.isBlank() != githubSecret.isBlank()) {
            throw new IllegalArgumentException("Configure both client ID and client secret for each login provider.");
        }
        if (googleId.isBlank() && githubId.isBlank()) throw new IllegalArgumentException("Configure at least one login provider.");
        return new AccountConfig(origin, database, EnvironmentConfig.required(env, "DATABASE_USER"),
                EnvironmentConfig.required(env, "DATABASE_PASSWORD"), googleId, googleSecret, githubId, githubSecret);
    }
    @Override public String toString() { return "AccountConfig[appOrigin=" + appOrigin + ", credentials=redacted]"; }
    public boolean secureCookies() { return appOrigin.startsWith("https://"); }
    public List<String> providers() {
        List<String> result = new ArrayList<>();
        if (!googleId.isBlank()) result.add("google");
        if (!githubId.isBlank()) result.add("github");
        return List.copyOf(result);
    }
    public String clientId(String provider) { return provider.equals("google") ? googleId : githubId; }
    public String clientSecret(String provider) { return provider.equals("google") ? googleSecret : githubSecret; }
}
