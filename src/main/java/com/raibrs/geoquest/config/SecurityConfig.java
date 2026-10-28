package com.raibrs.geoquest.config;

import com.raibrs.geoquest.account.*;
import com.raibrs.geoquest.http.ApiErrors;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import java.util.ArrayList;

@Configuration(proxyBeanMethods = false)
@Import(OAuthClientConfig.class)
@EnableWebSecurity
public class SecurityConfig {
    @Bean ClientRegistrationRepository clientRegistrations(AccountSessions accounts) {
        var registrations = new ArrayList<ClientRegistration>();
        for (String provider : accounts.providers()) {
            var config = accounts.config();
            var builder = CommonOAuth2Provider.valueOf(provider.toUpperCase(java.util.Locale.ROOT)).getBuilder(provider)
                    .clientId(config.clientId(provider)).clientSecret(config.clientSecret(provider))
                    .redirectUri(config.appOrigin() + "/login/oauth2/code/{registrationId}");
            if (provider.equals("google")) builder.scope("openid", "profile", "email");
            else builder.scope("read:user");
            registrations.add(builder.build());
        }
        return registrations.isEmpty() ? id -> null : new InMemoryClientRegistrationRepository(registrations);
    }
    @Bean OAuth2AuthorizedClientService authorizedClients(ClientRegistrationRepository registrations) {
        return new InMemoryOAuth2AuthorizedClientService(registrations);
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AccountSessions accounts, ClientRegistrationRepository registrations,
                                       OAuth2AuthorizedClientService clients,
                                       OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokens,
                                       DefaultOAuth2UserService users, OidcUserService oidcUsers) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> {
            cookie.httpOnly(true).sameSite("Lax");
            if (accounts.config() != null && accounts.config().secureCookies()) cookie.secure(true);
        });
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/me", "/api/logout", "/api/account/delete").authenticated()
                        .anyRequest().permitAll())
                .csrf(protection -> protection.csrfTokenRepository(csrf))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .addFilterAfter(new PersistentSessionFilter(accounts), SecurityContextHolderFilter.class)
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> ApiErrors.write(response, 401, "Sign in to continue."))
                        .accessDeniedHandler((request, response, error) -> ApiErrors.write(response, 403, "Request is not allowed. Refresh the page and try again.")));
        if (accounts.enabled()) {
            var resolver = new ConfiguredOAuthRequestResolver(registrations);
            http.oauth2Login(login -> login
                    .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
                    .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokens))
                    .userInfoEndpoint(endpoint -> endpoint.userService(users).oidcUserService(oidcUsers))
                    .successHandler(new OAuthLoginSuccessHandler(accounts, clients))
                    .failureHandler((request, response, error) -> {
                        accounts.clearHandshake(request, response);
                        response.sendRedirect("/?login=failed");
                    }));
        }
        return http.build();
    }
}
