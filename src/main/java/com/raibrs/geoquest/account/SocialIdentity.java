package com.raibrs.geoquest.account;

/** Provider-verified identity; email does not link accounts from different providers. */
public record SocialIdentity(String provider, String subject, String displayName, String email, String avatarUrl) {
    @Override public String toString() { return "SocialIdentity[provider=" + provider + ", profile=redacted]"; }
}
