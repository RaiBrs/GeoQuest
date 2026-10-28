package com.raibrs.geoquest.account;

import com.raibrs.geoquest.config.SecurityConfig;
import com.raibrs.geoquest.controller.GameController;
import com.raibrs.geoquest.dto.GameDtos.*;
import com.raibrs.geoquest.http.*;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.service.*;
import com.raibrs.geoquest.support.TestCountriesClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(AccountControllerTest.Config.class)
@WebAppConfiguration
class AccountControllerTest {
    static final String TOKEN = "a".repeat(43);
    static class Backend implements AccountsBackend {
        User user;
        int logins, deleted, lookups;
        String hash;
        public User login(SocialIdentity identity) { logins++; return user; }
        public void createSession(User user, String hash) { this.hash = hash; }
        public User findSession(String hash) { lookups++; return AccountSessions.hash(TOKEN).equals(hash) || Objects.equals(this.hash, hash) ? user : null; }
        public void logout(String hash) { user = null; }
        public void delete(User user) { deleted++; this.user = null; }
        public void saveResult(User user, GameView state, QuestionType type) {}
        public List<Leader> leaderboard(GameMode mode, QuestionType type) { return List.of(new Leader("Rai", 10, 10)); }
    }
    static AccountConfig config() { return new AccountConfig("https://game.example", "jdbc:postgresql://localhost/g", "u", "p", "id", "secret", "id2", "secret2"); }
    @Configuration @EnableWebMvc
    @Import({SecurityConfig.class, AccountController.class, GameController.class, StaticFileHandler.class, ApiErrors.class, JsonBodyLimit.class})
    static class Config {
        @Bean Backend backend() { return new Backend(); }
        @Bean AccountSessions accounts(Backend backend) { return new AccountSessions(config(), backend); }
        @Bean GameService games() { return new GameService(new TestCountriesClient(), new QuestionService()); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean Path frontend() { return Path.of("frontend"); }
    }
    @Autowired WebApplicationContext context;
    @Autowired Backend backend;
    MockMvc mvc;
    Cookie cookie() { return new Cookie(AccountSessions.COOKIE, TOKEN); }
    org.springframework.test.web.servlet.request.RequestPostProcessor browserCsrf() throws Exception {
        var response = mvc.perform(get("/api/csrf")).andReturn().getResponse();
        var token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        String header = response.getHeader("Set-Cookie").split(";", 2)[0];
        var cookie = new Cookie(header.split("=", 2)[0], header.split("=", 2)[1]);
        return request -> {
            request.addHeader(token.get("headerName").asText(), token.get("token").asText());
            var cookies = new ArrayList<Cookie>();
            if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
            cookies.add(cookie); request.setCookies(cookies.toArray(Cookie[]::new));
            return request;
        };
    }
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        backend.user = new AccountsBackend.User(UUID.randomUUID(), "Rai", Instant.now(), null, "github", "123", "https://example.test/avatar");
        backend.deleted = backend.logins = backend.lookups = 0;
    }
    @Test void anonymousVisitorsCanPlayButCannotAccessPrivateAccountEndpoints() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/account")).andExpect(status().isOk()).andExpect(jsonPath("$.user").isEmpty());
        mvc.perform(post("/api/games").with(browserCsrf()).contentType("application/json").content("{}")).andExpect(status().isCreated());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/logout").with(browserCsrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/account/delete").with(browserCsrf()).contentType("application/json").content("{\"confirmation\":\"DELETE\"}")).andExpect(status().isUnauthorized());
    }
    @Test void meNormalizesProfileWithoutExposingCredentialsAndLooksUpTheSessionOnce() throws Exception {
        mvc.perform(get("/api/me").cookie(cookie())).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rai")).andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.provider").value("github")).andExpect(jsonPath("$.providerId").value("123"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.test/avatar"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        assertEquals(1, backend.lookups);
        mvc.perform(get("/api/me").cookie(new Cookie(AccountSessions.COOKIE, "b".repeat(43)))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new Cookie(AccountSessions.COOKIE, null))).andExpect(status().isUnauthorized());
    }
    @Test void csrfGuardsAllMutationsAndCanBeObtainedWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
        mvc.perform(post("/api/games").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/account/delete").cookie(cookie()).contentType("application/json").content("{\"confirmation\":\"DELETE\"}")).andExpect(status().isForbidden());
        assertEquals(0, backend.deleted);
    }
    @Test void deletionRequiresConfirmationAndRecentAuthenticationAndRevokesTheSession() throws Exception {
        mvc.perform(post("/api/account/delete").with(browserCsrf()).cookie(cookie()).contentType("application/json").content("{\"confirmation\":\"wrong\"}")).andExpect(status().isBadRequest());
        var fresh = backend.user;
        backend.user = new AccountsBackend.User(fresh.id(), "Rai", Instant.now().minusSeconds(601));
        mvc.perform(post("/api/account/delete").with(browserCsrf()).cookie(cookie()).contentType("application/json").content("{\"confirmation\":\"DELETE\"}")).andExpect(status().isForbidden());
        backend.user = fresh;
        mvc.perform(post("/api/account/delete").with(browserCsrf()).cookie(cookie()).contentType("application/json").content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk()).andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Max-Age=0"))));
        assertEquals(1, backend.deleted);
        mvc.perform(get("/api/me").cookie(cookie())).andExpect(status().isUnauthorized());
    }
    @Test void standardLoginRoutesUseSpringStatePkceAndProviderSpecificCallbacks() throws Exception {
        for (String provider : List.of("google", "github")) {
            var result = mvc.perform(get("/oauth2/authorization/" + provider)).andExpect(status().is3xxRedirection()).andReturn();
            String redirect = result.getResponse().getRedirectedUrl();
            assertTrue(redirect.contains("state=")); assertTrue(redirect.contains("code_challenge_method=S256"));
            assertTrue(java.net.URLDecoder.decode(redirect, java.nio.charset.StandardCharsets.UTF_8).contains("redirect_uri=https://game.example/login/oauth2/code/" + provider), redirect);
            assertFalse(redirect.contains("secret"));
            mvc.perform(get("/login/oauth2/code/" + provider).session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false))
                    .param("code", "fake").param("state", "wrong")).andExpect(redirectedUrl("/?login=failed"));
        }
        assertEquals(0, backend.logins);
        mvc.perform(get("/oauth2/authorization/unknown")).andExpect(status().is4xxClientError());
    }
    @Test void userCannotReadAnotherPlayersRound() throws Exception {
        var started = mvc.perform(post("/api/games").with(browserCsrf()).cookie(cookie()).contentType("application/json").content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String gameId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(started).get("gameId").asText();
        backend.user = new AccountsBackend.User(UUID.randomUUID(), "Other", Instant.now());
        mvc.perform(post("/api/games/status").with(browserCsrf()).cookie(cookie()).contentType("application/json")
                .content("{\"gameId\":\"" + gameId + "\"}")).andExpect(status().isNotFound());
    }
    @Test void aSpringVerifiedLoginCreatesOnlyAnOpaqueGameSessionAndDiscardsProviderTokens() throws Exception {
        var accounts = context.getBean(AccountSessions.class);
        var clients = context.getBean(org.springframework.security.oauth2.client.OAuth2AuthorizedClientService.class);
        var registration = context.getBean(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class).findByRegistrationId("github");
        var principal = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")), Map.of("id", 123, "login", "Rai"), "id");
        var authentication = new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal, principal.getAuthorities(), "github");
        var access = new org.springframework.security.oauth2.core.OAuth2AccessToken(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER, "provider-access-secret", Instant.now(), Instant.now().plusSeconds(60));
        clients.saveAuthorizedClient(new org.springframework.security.oauth2.client.OAuth2AuthorizedClient(registration, authentication.getName(), access), authentication);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        var handshake = request.getSession();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new OAuthLoginSuccessHandler(accounts, clients).onAuthenticationSuccess(request, response, authentication);
        assertEquals("/", response.getRedirectedUrl()); assertEquals(1, backend.logins);
        String cookie = response.getHeaders("Set-Cookie").stream().filter(value -> value.startsWith("geoquest_session=")).findFirst().orElseThrow();
        String token = cookie.split(";", 2)[0].split("=", 2)[1];
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("SameSite=Lax"));
        assertEquals(AccountSessions.hash(token), backend.hash); assertNotEquals(token, backend.hash);
        assertTrue(((org.springframework.mock.web.MockHttpSession) handshake).isInvalid());
        assertNull(clients.loadAuthorizedClient("github", authentication.getName()));
        assertFalse(cookie.contains("provider-access-secret"));
    }
}
