package com.raibrs.geoquest;

import com.fasterxml.jackson.databind.JsonNode;
import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.support.TestCountriesClient;
import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests application wiring; each handler's validation and failure cases live beside that component. */
class WebAppTest {
    private TestHttpServer http;

    @BeforeEach
    void startServer() throws Exception {
        var ranking = new RankingService(new InMemoryRankingRepository());
        var games = new GameService(new TestCountriesClient(), new QuestionService(), ranking);
        var server = new WebApp(games, ranking, Path.of("frontend"))
                .createServer(new InetSocketAddress("127.0.0.1", 0));
        http = new TestHttpServer(server);
    }

    @AfterEach
    void stopServer() {
        if (http != null) http.close();
    }

    @Test
    void servesTheBrowserAndConnectsGameResultsToTheRanking() throws Exception {
        assertEquals(200, http.get("/").statusCode());
        var started = http.post("/api/games", Map.of("playerName", "Rai"));
        assertEquals(201, started.statusCode());
        JsonNode state = http.readJson(started);
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
        assertEquals(200, http.post("/api/results", Map.of("gameId", gameId)).statusCode());

        var response = http.get("/api/ranking");
        assertEquals(200, response.statusCode());
        var ranking = http.readJson(response);
        assertEquals(1, ranking.size());
        assertEquals("Rai", ranking.get(0).get("playerName").asText());
        assertEquals(1, ranking.get(0).get("score").asInt());
    }

    @Test
    void unknownApiRoutesDoNotFallThroughToStaticFilesOrExposeAdministration() throws Exception {
        assertEquals(404, http.get("/api/unknown").statusCode());
        assertEquals(404, http.post("/api/admin/rename", Map.of()).statusCode());
    }
}
