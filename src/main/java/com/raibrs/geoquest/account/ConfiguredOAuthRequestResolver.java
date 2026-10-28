package com.raibrs.geoquest.account;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Unknown providers fall through to MVC's 404 instead of Spring's redirect-filter error. */
public final class ConfiguredOAuthRequestResolver implements OAuth2AuthorizationRequestResolver {
    private static final String BASE = "/oauth2/authorization";
    private final ClientRegistrationRepository registrations;
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    public ConfiguredOAuthRequestResolver(ClientRegistrationRepository registrations) {
        this.registrations = registrations;
        delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, BASE);
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        if (path.startsWith(BASE + "/") && registrations.findByRegistrationId(path.substring(BASE.length() + 1)) == null) return null;
        return delegate.resolve(request);
    }
    @Override public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return registrationId == null || registrations.findByRegistrationId(registrationId) == null ? null : delegate.resolve(request, registrationId);
    }
}
