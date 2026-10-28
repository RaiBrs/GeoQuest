package com.raibrs.geoquest.account;

import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import java.io.IOException;

/** Bridges a Spring-verified identity to the existing profile and persistent session. */
public final class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AccountSessions accounts;
    private final OAuth2AuthorizedClientService clients;
    public OAuthLoginSuccessHandler(AccountSessions accounts, OAuth2AuthorizedClientService clients) { this.accounts = accounts; this.clients = clients; }
    @Override public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        var oauth = (OAuth2AuthenticationToken) authentication;
        try {
            var identity = OAuthUserNormalizer.normalize(oauth.getAuthorizedClientRegistrationId(), oauth.getPrincipal().getAttributes());
            accounts.signIn(identity, request, response);
            response.sendRedirect("/");
        } catch (IOException | RuntimeException error) {
            accounts.clearHandshake(request, response);
            System.getLogger(OAuthLoginSuccessHandler.class.getName()).log(System.Logger.Level.WARNING,
                    "Login could not be completed: " + error.getClass().getSimpleName());
            response.sendRedirect("/?login=failed");
        } finally {
            // The game needs the verified identity, with no later provider API calls.
            clients.removeAuthorizedClient(oauth.getAuthorizedClientRegistrationId(), oauth.getName());
        }
    }
}
