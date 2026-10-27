package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.support.TestCountriesClient;
import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GameControllerTest {
    private final TestCountriesClient countries = new TestCountriesClient();
    private final InMemoryRankingRepository repository = new InMemoryRankingRepository();
    private final GameService games = new GameService(countries, new QuestionService(),
            new RankingService(repository));
    private TestHttpServer http;

    @BeforeEach
    void startServer() throws Exception {
        http = TestHttpServer.start(new GameController(games));
    }

    @AfterEach
    void stopServer() {
        if (http != null) http.close();
    }

    @Test
    void startsARoundWithoutExposingTheAnswerKey() throws Exception {
        var response = http.post("/api/games", Map.of("playerName", "Rai"));
        assertEquals(201, response.statusCode());
        var state = http.readJson(response);
        assertDoesNotThrow(() -> UUID.fromString(state.get("gameId").asText()));
        assertEquals(1, state.get("number").asInt());
        assertEquals(0, state.get("score").asInt());
        assertEquals(4, state.get("question").get("alternatives").size());
        assertFalse(state.get("question").has("correctAnswer"));
    }

    @Test
    void rejectsMalformedMissingOversizedAndInvalidPlayerInput() throws Exception {
        for (String body : List.of("{", "null", "{}", "{\"playerName\":\" \"}", "x".repeat(4097))) {
            assertEquals(400, http.postRaw("/api/games", body, "application/json").statusCode());
        }
    }

    @Test
    void requiresJsonContentType() throws Exception {
        assertEquals(415, http.postRaw("/api/games", "{}", null).statusCode());
    }

    @Test
    void rejectsSavingUnfinishedUnknownAndClientControlledResults() throws Exception {
        var state = games.start("Rai");
        assertEquals(409, http.post("/api/results", Map.of("gameId", state.gameId())).statusCode());
        assertEquals(400, http.post("/api/results", Map.of("gameId", state.gameId(), "score", 10)).statusCode());
        assertEquals(404, http.post("/api/results", Map.of("gameId", UUID.randomUUID())).statusCode());
    }

    @Test
    void repeatedAnswerAndSaveRequestsKeepTheirOriginalResult() throws Exception {
        var state = games.start("Rai");
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream()
                .filter(choice -> !choice.equals(correct)).findFirst().orElseThrow();
        var submission = Map.of("gameId", state.gameId(), "number", 1, "choice", wrong);

        var response = http.post("/api/answers", submission);
        assertEquals(200, response.statusCode());
        assertEquals(response.body(), http.post("/api/answers", submission).body());
        assertTrue(http.readJson(response).get("state").get("finished").asBoolean());
        var save = Map.of("gameId", state.gameId());
        assertEquals(200, http.post("/api/results", save).statusCode());
        assertEquals(200, http.post("/api/results", save).statusCode());
        assertEquals(1, repository.allEntries().size());
        assertEquals(0, repository.allEntries().getFirst().getScore());
    }

    @Test
    void countryFailureIsRecoverableAndDoesNotExposeTechnicalDetails() throws Exception {
        countries.failing = true;
        var response = http.post("/api/games", Map.of("playerName", "Rai"));
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("Test country service"));
        countries.failing = false;
        assertEquals(201, http.post("/api/games", Map.of("playerName", "Rai")).statusCode());
    }

    @Test
    void rejectsWrongMethodsAndRouteSuffixes() throws Exception {
        var response = http.get("/api/games");
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(404, http.post("/api/games/extra", Map.of()).statusCode());
    }
}
