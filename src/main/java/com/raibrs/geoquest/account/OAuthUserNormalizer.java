package com.raibrs.geoquest.account;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.net.URI;
import java.util.Map;

public final class OAuthUserNormalizer {
    private record Fields(String id, String avatar, String fallbackName) {}
    private static final Map<String, Fields> PROVIDERS = Map.of(
            "google", new Fields("sub", "picture", "name"), "github", new Fields("id", "avatar_url", "login"));
    private OAuthUserNormalizer() {}

    public static SocialIdentity normalize(String provider, Map<String, Object> attributes) {
        var fields = provider == null ? null : PROVIDERS.get(provider);
        if (fields == null || attributes == null) throw new OAuth2AuthenticationException("invalid_provider");
        String subject = text(attributes.get(fields.id()));
        if (subject == null || subject.length() > 256 || subject.chars().anyMatch(Character::isISOControl))
            throw new OAuth2AuthenticationException("invalid_identity");
        String name = text(attributes.get("name"));
        if (name == null) name = text(attributes.get(fields.fallbackName()));
        if (name == null) name = "Player";
        name = name.replaceAll("\\p{Cc}", "").strip();
        if (name.isEmpty()) name = "Player";
        name = name.codePoints().limit(80).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        return new SocialIdentity(provider, subject, name, text(attributes.get("email")), avatar(attributes.get(fields.avatar())));
    }
    private static String text(Object value) {
        if (!(value instanceof String || value instanceof Number)) return null;
        String result = value.toString().strip();
        return result.isEmpty() ? null : result;
    }
    private static String avatar(Object value) {
        String text = text(value);
        if (text == null) return null;
        try {
            URI uri = URI.create(text);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null ? text : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
}
