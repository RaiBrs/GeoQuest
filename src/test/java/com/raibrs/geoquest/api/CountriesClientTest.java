package com.raibrs.geoquest.api;

import com.raibrs.geoquest.model.Country;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountriesClientTest {

    private HttpServer server;
    private HttpClient httpClient;
    private CountriesClient client;
    private URI baseUri;
    private final List<String> queries = new CopyOnWriteArrayList<>();
    private final List<String> authorizationHeaders = new CopyOnWriteArrayList<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private volatile int status = 200;
    private volatile boolean holdResponse;
    private volatile boolean failSecondPage;
    private volatile Function<String, String> responseBody = query ->
            "{\"data\":{\"objects\":[],\"meta\":{\"more\":false}}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/countries/v5", exchange -> {
            try {
                String query = exchange.getRequestURI().getRawQuery();
                queries.add(query);
                authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
                if (holdResponse) {
                    try {
                        releaseResponse.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                byte[] body = responseBody.apply(query).getBytes(StandardCharsets.UTF_8);
                int responseStatus = failSecondPage && query.endsWith("offset=2") ? 500 : status;
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/countries/v5");
        httpClient = HttpClient.newHttpClient();
        client = new CountriesClient("test-key", httpClient, baseUri, Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        releaseResponse.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    // Protects the API contract by rejecting limits outside the supported range before a request is sent.
    @Test
    void rejectsALimitOutsideTheApiRange() {
        assertThrows(IllegalArgumentException.class, () -> client.fetchCountries(0));
        assertThrows(IllegalArgumentException.class, () -> client.fetchCountries(101));
        assertTrue(queries.isEmpty());
    }

    @Test
    void sendsTheRequestedFieldsLimitOffsetAndBearerHeader() throws Exception {
        String expected = responseBody.apply("");

        assertEquals(expected, client.fetchCountries(80));

        assertEquals(List.of("response_fields=names.common,names.translations.por,codes.alpha_2,capitals&limit=80&offset=0"), queries);
        assertEquals(List.of("Bearer test-key"), authorizationHeaders);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500})
    void reportsHttpErrorsWithoutReturningTheirBody(int statusCode) {
        status = statusCode;
        responseBody = query -> "private error body";

        IOException exception = assertThrows(IOException.class, () -> client.fetchCountries(80));

        assertTrue(exception.getMessage().contains(Integer.toString(statusCode)));
        assertFalse(exception.getMessage().contains("private error body"));
        assertFalse(exception.getMessage().contains("test-key"));
    }

    @Test
    void timesOutWhenTheServerDoesNotRespond() {
        holdResponse = true;
        CountriesClient impatientClient = new CountriesClient(
                "test-key", httpClient, baseUri, Duration.ofMillis(100));

        assertThrows(HttpTimeoutException.class, () -> impatientClient.fetchCountries(80));
    }

    @Test
    void loadsEveryPageAndAdvancesByRawRecordsIncludingDiscardedCountries() throws Exception {
        useTwoPages();

        List<Country> countries = client.fetchAllCountries(2);

        assertEquals(List.of("Brazil", "Japan"), countries.stream().map(Country::getName).toList());
        assertEquals(2, queries.size());
        assertTrue(queries.get(0).endsWith("limit=2&offset=0"));
        assertTrue(queries.get(1).endsWith("limit=2&offset=2"));
    }

    @Test
    void failsAnIncompleteCatalogAndStartsFromTheFirstPageOnAnotherAttempt() throws Exception {
        useTwoPages();
        failSecondPage = true;

        assertThrows(IOException.class, () -> client.fetchAllCountries(2));
        failSecondPage = false;
        List<Country> countries = client.fetchAllCountries(2);

        assertEquals(2, countries.size());
        assertEquals(4, queries.size());
        assertTrue(queries.get(2).endsWith("offset=0"));
    }

    @Test
    void rejectsPaginationWithoutProgressRatherThanRequestingForever() {
        responseBody = query -> "{\"data\":{\"objects\":[],\"meta\":{\"more\":true}}}";

        IOException exception = assertThrows(IOException.class, () -> client.fetchAllCountries(2));

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(1, queries.size());
    }

    @Test
    void rejectsAnInvalidPageWithoutReturningPartialData() {
        responseBody = query -> "not json";

        IOException exception = assertThrows(IOException.class, () -> client.fetchAllCountries(2));

        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(1, queries.size());
    }

    @Test
    void rejectsNegativeOffsetsBeforeSendingARequest() {
        assertThrows(IllegalArgumentException.class, () -> client.fetchCountries(2, -1));
        assertTrue(queries.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "secret\nkey", "secret\u0000key"})
    void rejectsInvalidCredentialsWithoutIncludingThemInTheError(String apiKey) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new CountriesClient(apiKey, httpClient, baseUri, Duration.ofSeconds(5)));

        assertEquals("API key must contain only visible ASCII characters.", exception.getMessage());
        assertTrue(queries.isEmpty());
    }

    private void useTwoPages() {
        responseBody = query -> query.endsWith("offset=0")
                ? """
                  {"data":{"objects":[
                    {"names":{"common":"Brazil"},"capitals":[{"name":"Brasília"}]},
                    {"names":{"common":"Antarctica"},"capitals":[]}
                  ],"meta":{"more":true}}}
                  """
                : """
                  {"data":{"objects":[
                    {"names":{"common":"Japan"},"capitals":[{"name":"Tokyo"}]}
                  ],"meta":{"more":false}}}
                  """;
    }
}
