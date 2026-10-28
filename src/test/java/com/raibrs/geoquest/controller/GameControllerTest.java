package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.service.QuestionService;
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
    @Test
    void startsLocalizedCountryQuestionsAndRejectsUnknownPreferences() throws Exception {
        var response = http.post("/api/games", Map.of("mode", "CAPITALS",
                "questionType", "COUNTRY", "language", "PT_BR"));
        assertEquals(201, response.statusCode());
        var question = http.readJson(response).get("question");
        assertTrue(question.get("text").asText().startsWith("De qual país Capital "));
        assertFalse(question.has("correctAnswer"));
        assertEquals(400, http.post("/api/games", Map.of("language", "UNKNOWN")).statusCode());
        assertEquals(400, http.post("/api/games", Map.of("questionType", "UNKNOWN")).statusCode());
    }
    private final TestCountriesClient countries = new TestCountriesClient();
    private final GameService games = new GameService(countries, new QuestionService());
    private TestHttpServer http;

    @BeforeEach
    void startServer() throws Exception { http = TestHttpServer.start(new GameController(games)); }

    @AfterEach
    void stopServer() { if (http != null) http.close(); }

    @Test
    void startsAnAnonymousRoundWithoutExposingTheAnswerKey() throws Exception {
        var response = http.post("/api/games", Map.of());
        assertEquals(201, response.statusCode());
        var state = http.readJson(response);
        assertDoesNotThrow(() -> UUID.fromString(state.get("gameId").asText()));
        assertEquals(1, state.get("number").asInt());
        assertEquals(0, state.get("score").asInt());
        assertEquals(4, state.get("question").get("alternatives").size());
        assertFalse(state.get("question").has("correctAnswer"));
        assertFalse(state.has("playerName"));
    }

    @Test
    void acceptsEachModeAndRejectsUnknownModes() throws Exception {
        for (String mode : List.of("CAPITALS", "SURVIVAL", "TIMED")) {
            var response = http.post("/api/games", Map.of("mode", mode));
            assertEquals(201, response.statusCode());
            var state = http.readJson(response);
            assertEquals(mode, state.get("mode").asText());
            assertEquals(mode.equals("TIMED"), !state.get("expiresAt").isNull());
            var status = http.post("/api/games/status", Map.of("gameId", state.get("gameId").asText()));
            assertEquals(200, status.statusCode());
            assertEquals(state, http.readJson(status));
        }
        assertEquals(400, http.post("/api/games", Map.of("mode", "UNKNOWN")).statusCode());
    }

    @Test
    void rejectsMalformedMissingOversizedAndUnexpectedStartData() throws Exception {
        for (String body : List.of("{", "null", "{\"playerName\":\"Rai\"}",
                "{\"score\":10}", "x".repeat(4097))) {
            assertEquals(400, http.postRaw("/api/games", body, "application/json").statusCode());
        }
    }

    @Test
    void requiresJsonContentType() throws Exception {
        assertEquals(415, http.postRaw("/api/games", "{}", null).statusCode());
    }

    @Test
    void rejectsUnknownOutOfOrderAndClientControlledAnswers() throws Exception {
        var state = games.start();
        assertEquals(404, http.post("/api/answers",
                Map.of("gameId", UUID.randomUUID(), "number", 1, "choice", "invalid")).statusCode());
        assertEquals(409, http.post("/api/answers",
                Map.of("gameId", state.gameId(), "number", 2, "choice", "invalid")).statusCode());
        assertEquals(400, http.post("/api/answers",
                Map.of("gameId", state.gameId(), "number", 1, "choice", "invalid", "score", 10)).statusCode());
    }

    @Test
    void repeatedAnswerRequestsKeepTheirOriginalResult() throws Exception {
        var state = games.start();
        String correct = TestCountriesClient.answerFor(state.question().text());
        String wrong = state.question().alternatives().stream()
                .filter(choice -> !choice.equals(correct)).findFirst().orElseThrow();
        var submission = Map.of("gameId", state.gameId(), "number", 1, "choice", wrong);
        var response = http.post("/api/answers", submission);
        assertEquals(200, response.statusCode());
        assertEquals(response.body(), http.post("/api/answers", submission).body());
        assertTrue(http.readJson(response).get("state").get("finished").asBoolean());
        assertEquals(0, http.readJson(response).get("state").get("score").asInt());
    }

    @Test
    void countryFailureIsRecoverableAndDoesNotExposeTechnicalDetails() throws Exception {
        countries.failing = true;
        var response = http.post("/api/games", Map.of());
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("Test country service"));
        assertEquals("Unable to complete the operation. Please try again.",
                http.readJson(response).get("message").asText());
        countries.failing = false;
        assertEquals(201, http.post("/api/games", Map.of()).statusCode());
    }

    @Test
    void returnsEnglishValidationAndRoundErrors() throws Exception {
        assertEquals("Invalid JSON.", http.readJson(http.postRaw("/api/games", "{", "application/json"))
                .get("message").asText());
        assertEquals("Provide the request data.",
                http.readJson(http.postRaw("/api/games", "null", "application/json"))
                        .get("message").asText());
        assertEquals("Method not allowed.", http.readJson(http.get("/api/games"))
                .get("message").asText());
        assertEquals("Round not found. Start a new game.",
                http.readJson(http.post("/api/answers",
                        Map.of("gameId", UUID.randomUUID(), "number", 1, "choice", "invalid")))
                        .get("message").asText());
        var state = games.start();
        assertEquals("Invalid answer choice.", http.readJson(http.post("/api/answers",
                Map.of("gameId", state.gameId(), "number", 1, "choice", "invalid")))
                .get("message").asText());
    }

    @Test
    void rejectsWrongMethodsAndRouteSuffixes() throws Exception {
        var response = http.get("/api/games");
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(404, http.post("/api/games/extra", Map.of()).statusCode());
    }
}
