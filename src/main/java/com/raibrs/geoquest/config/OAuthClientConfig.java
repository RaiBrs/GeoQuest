package com.raibrs.geoquest.config;

import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.Duration;

/** Preserve bounded provider calls while delegating protocol handling to Spring. */
@Configuration(proxyBeanMethods = false)
public class OAuthClientConfig {
    private JdkClientHttpRequestFactory requestFactory() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
    @Bean OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(RestClient.builder().requestFactory(requestFactory())
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                }).defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }
    @Bean DefaultOAuth2UserService providerUsers() {
        var rest = new RestTemplate(requestFactory());
        rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        var service = new DefaultOAuth2UserService();
        service.setRestOperations(rest);
        return service;
    }
    @Bean OidcUserService oidcUsers(DefaultOAuth2UserService users) {
        var service = new OidcUserService();
        service.setOauth2UserService(users);
        return service;
    }
}
