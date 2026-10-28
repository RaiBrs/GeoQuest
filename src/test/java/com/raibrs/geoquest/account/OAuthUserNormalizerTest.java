package com.raibrs.geoquest.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OAuthUserNormalizerTest {
    @Test void googleUsesSubjectAndPreservesOptionalProfileData() {
        var identity = OAuthUserNormalizer.normalize("google", Map.of("sub", "123", "name", " Rai ", "email", "rai@example.test", "picture", "https://example.test/photo"));
        assertEquals(new SocialIdentity("google", "123", "Rai", "rai@example.test", "https://example.test/photo"), identity);
    }
    @Test void githubSupportsNumericIdsPrivateEmailAndMissingNames() {
        var identity = OAuthUserNormalizer.normalize("github", Map.of("id", 456, "login", "Rai<script>", "avatar_url", "javascript:alert(1)"));
        assertEquals("456", identity.subject()); assertEquals("Rai<script>", identity.displayName());
        assertNull(identity.email()); assertNull(identity.avatarUrl());
        assertEquals("Player", OAuthUserNormalizer.normalize("github", Map.of("id", 1, "name", "\n\t")).displayName());
    }
    @Test void unknownProvidersAndMalformedIdsAreRejectedWithoutNullPointerExceptions() {
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthUserNormalizer.normalize(null, Map.of()));
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthUserNormalizer.normalize("other", Map.of()));
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthUserNormalizer.normalize("google", null));
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthUserNormalizer.normalize("google", Map.of("email", "rai@example.test")));
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthUserNormalizer.normalize("github", Map.of("id", Map.of("sub", 123))));
    }
    @Test void insecureAndIncompleteConfigurationFailsWithoutLeakingSecrets() {
        assertNull(AccountConfig.from(Map.of()));
        var env = new HashMap<>(Map.of("ACCOUNTS_ENABLED", "true", "APP_URL", "http://public.example", "JDBC_DATABASE_URL", "jdbc:postgresql://localhost/g",
                "DATABASE_USER", "u", "DATABASE_PASSWORD", "private-value", "GOOGLE_CLIENT_ID", "id", "GOOGLE_CLIENT_SECRET", "secret"));
        assertFalse(assertThrows(IllegalArgumentException.class, () -> AccountConfig.from(env)).getMessage().contains("private-value"));
        env.put("APP_URL", "http://localhost:8080"); assertNotNull(AccountConfig.from(env));
        env.remove("GOOGLE_CLIENT_SECRET"); assertThrows(IllegalArgumentException.class, () -> AccountConfig.from(env));
    }
}
