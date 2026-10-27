package com.raibrs.geoquest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.support.TestCountriesClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebAppTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final TestCountriesClient countries = new TestCountriesClient();
    private final InMemoryRankingRepository repository = new InMemoryRankingRepository();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        start(repository);
    }

    private void start(InMemoryRankingRepository target) throws Exception {
        var ranking = new RankingService(target);
        var games = new GameService(countries, new QuestionService(), ranking);
        server = new WebApp(games, ranking, Path.of("frontend"))
                .createServer(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        http.close();
    }

    @Test
    void servesOnlyPublicAssetsFromTheSameOrigin() throws Exception {
        for (String asset : List.of("/", "/styles.css", "/src/app.js", "/src/service/game-service.js")) {
            assertEquals(200, get(asset).statusCode());
        }
        assertTrue(get("/").body().contains("Geo Quest"));
        for (String path : List.of("/pom.xml", "/../pom.xml", "/.git/config",
                "/src/repository/question-repository.js")) {
            assertEquals(404, get(path).statusCode());
        }
        assertEquals(405, post("/", "{}").statusCode());
    }

    @Test
    void playsAndPersistsARealHttpRoundWithoutAcceptingClientScores() throws Exception {
        var started = post("/api/games", "{\"playerName\":\"Rai\"}");
        assertEquals(201, started.statusCode());
        JsonNode state = json.readTree(started.body());
        assertFalse(state.get("question").has("correctAnswer"));
        String gameId = state.get("gameId").asText();
        assertEquals(409, post("/api/results", json.writeValueAsString(Map.of("gameId", gameId))).statusCode());

        String correct = TestCountriesClient.answerFor(state.get("question").get("text").asText());
        String wrong = "";
        for (JsonNode choice : state.get("question").get("alternatives")) {
            if (!choice.asText().equals(correct)) wrong = choice.asText();
        }
        String submission = json.writeValueAsString(Map.of("gameId", gameId, "number", 1, "choice", wrong));
        var response = post("/api/answers", submission);
        assertEquals(200, response.statusCode());
        assertEquals(response.body(), post("/api/answers", submission).body());
        assertTrue(json.readTree(response.body()).get("state").get("finished").asBoolean());
        assertEquals(400, post("/api/results", json.writeValueAsString(
                Map.of("gameId", gameId, "score", 10))).statusCode());
        String resultRequest = json.writeValueAsString(Map.of("gameId", gameId));
        assertEquals(200, post("/api/results", resultRequest).statusCode());
        assertEquals(200, post("/api/results", resultRequest).statusCode());
        JsonNode ranking = json.readTree(get("/api/ranking").body());
        assertEquals(1, ranking.size());
        assertEquals("Rai", ranking.get(0).get("playerName").asText());
        assertEquals(0, ranking.get(0).get("score").asInt());
    }

    @Test
    void rejectsMalformedMissingOversizedAndNonJsonInput() throws Exception {
        for (String body : List.of("{", "null", "{}", "{\"playerName\":\" \"}", "x".repeat(4097))) {
            assertEquals(400, post("/api/games", body).statusCode());
        }
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/games"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertEquals(415, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, post("/api/results", "{\"gameId\":\"00000000-0000-0000-0000-000000000000\"}").statusCode());
        assertEquals(404, get("/api/unknown").statusCode());
    }

    @Test
    void countryFailureIsRecoverableAndDoesNotExposeTechnicalDetails() throws Exception {
        countries.failing = true;
        var response = post("/api/games", "{\"playerName\":\"Rai\"}");
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("Test country service"));
        countries.failing = false;
        assertEquals(201, post("/api/games", "{\"playerName\":\"Rai\"}").statusCode());
    }

    @Test
    void validatesExactRoutesAndMethodsWithoutExposingAdministration() throws Exception {
        var response = get("/api/games");
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(405, post("/api/ranking", "{}").statusCode());
        assertEquals(404, post("/api/games/extra", "{}").statusCode());
        assertEquals(404, get("/api/ranking/extra").statusCode());
        assertEquals(404, post("/api/ranking/rename", "{}").statusCode());
        assertEquals(404, post("/api/admin/rename", "{}").statusCode());
    }

    @Test
    void databaseFailureReturnsServiceUnavailable() throws Exception {
        server.stop(0);
        start(new InMemoryRankingRepository() {
            @Override
            public List<RankingEntry> findTop(int limit) throws SQLException {
                throw new SQLException("private test database details");
            }
        });
        var response = get("/api/ranking");
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("private test database"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
