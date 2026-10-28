package com.raibrs.geoquest.account;

/** Provider-verified profile data. Never used to link separate accounts by email. */
public record SocialIdentity(String provider, String subject, String displayName, String email, String avatarUrl) {
    @Override public String toString() { return "SocialIdentity[provider=" + provider + ", profile=redacted]"; }
}
