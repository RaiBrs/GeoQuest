package com.raibrs.geoquest;

import com.fasterxml.jackson.databind.JsonNode;
import com.raibrs.geoquest.support.TestCountriesClient;
import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.context.SpringBootTest(properties = {"API_KEY=test", "ACCOUNTS_ENABLED=false"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(WebAppTest.Fixtures.class)
class WebAppTest {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @org.springframework.boot.test.context.TestConfiguration static class Fixtures {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary com.raibrs.geoquest.api.CountriesClient fixtureCountries() { return new TestCountriesClient(); }
    }
    private TestHttpServer http;

    @BeforeEach
    void startServer() throws Exception {
        http = new TestHttpServer(mvc);
    }

    @AfterEach
    void stopServer() { if (http != null) http.close(); }

    @Test
    void servesTheBrowserAndComputesAnAnonymousRoundWithoutADatabase() throws Exception {
        assertEquals(200, http.get("/").statusCode());
        var started = http.post("/api/games", Map.of());
        assertEquals(201, started.statusCode());
        JsonNode state = http.readJson(started);
        assertFalse(state.has("playerName"));
        String gameId = state.get("gameId").asText();
        String correct = TestCountriesClient.answerFor(state.get("question").get("text").asText());
        var answered = http.post("/api/answers", Map.of("gameId", gameId, "number", 1, "choice", correct));
        assertEquals(200, answered.statusCode());
        state = http.readJson(answered).get("state");
        assertEquals(1, state.get("score").asInt());
        correct = TestCountriesClient.answerFor(state.get("question").get("text").asText());
        JsonNode choices = state.get("question").get("alternatives");
        String wrong = choices.get(0).asText();
        if (wrong.equals(correct)) wrong = choices.get(1).asText();
        var finished = http.post("/api/answers", Map.of("gameId", gameId, "number", 2, "choice", wrong));
        assertEquals(200, finished.statusCode());
        assertTrue(http.readJson(finished).get("state").get("finished").asBoolean());
        assertEquals(1, http.readJson(finished).get("state").get("score").asInt());
    }

    @Test
    void removedRankingAndSaveRoutesAreNotPublic() throws Exception {
        assertEquals(404, http.get("/api/ranking").statusCode());
        assertEquals(404, http.post("/api/results", Map.of("gameId", "old-round")).statusCode());
        assertEquals(404, http.get("/api/unknown").statusCode());
        assertEquals(404, http.post("/api/admin/rename", Map.of()).statusCode());
        assertEquals(405, http.get("/api/games").statusCode());
        assertEquals(401, http.get("/api/me").statusCode());
    }
}
