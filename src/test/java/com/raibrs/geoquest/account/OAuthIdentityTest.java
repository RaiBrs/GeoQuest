package com.raibrs.geoquest.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthIdentityTest {
    @Test void onlyConfiguredProvidersAreAdvertisedAndRedirectsUsePkce() {
        var config = new AccountConfig("https://game.example", "jdbc:postgresql://localhost/g", "u", "p", "google-id", "secret", "", "");
        assertEquals(List.of("google"), config.providers());
        var uri = URI.create(OAuthIdentity.authorizationUrl(config, "google", "state-123", "challenge"));
        assertEquals("accounts.google.com", uri.getHost());
        assertTrue(uri.getQuery().contains("code_challenge_method=S256"));
        assertTrue(uri.getQuery().contains("state=state-123"));
        assertFalse(uri.getQuery().contains("secret"));
        assertThrows(IllegalArgumentException.class, () -> OAuthIdentity.authorizationUrl(config, "github", "s", "c"));
    }
    @Test void identityUsesProviderIdRatherThanEmailAndNamesRemainPlainText() throws Exception {
        var data = new ObjectMapper().readTree("{\"id\":123,\"login\":\"Rai<script>\",\"email\":\"same@example.test\"}");
        var identity = OAuthIdentity.readIdentity("github", data);
        assertEquals("123", identity.subject());
        assertEquals("Rai<script>", identity.displayName());
        assertThrows(java.io.IOException.class, () -> OAuthIdentity.readIdentity("google", data));
    }
    @Test void incompleteOrInsecureConfigurationFailsWithoutExposingSecrets() {
        assertNull(AccountConfig.from(Map.of()));
        var values = new HashMap<>(Map.of("ACCOUNTS_ENABLED", "true", "APP_URL", "http://public.example",
                "JDBC_DATABASE_URL", "jdbc:postgresql://localhost/g", "DATABASE_USER", "u", "DATABASE_PASSWORD", "private-value",
                "GOOGLE_CLIENT_ID", "id", "GOOGLE_CLIENT_SECRET", "secret"));
        var failure = assertThrows(IllegalArgumentException.class, () -> AccountConfig.from(values));
        assertFalse(failure.getMessage().contains("private-value"));
        values.put("APP_URL", "http://localhost:8080");
        assertNotNull(AccountConfig.from(values));
        values.remove("GOOGLE_CLIENT_SECRET");
        assertThrows(IllegalArgumentException.class, () -> AccountConfig.from(values));
    }
}
