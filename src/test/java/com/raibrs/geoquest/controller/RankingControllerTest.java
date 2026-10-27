package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RankingControllerTest {
    @Test
    void exposesTheRankingAsJson() throws Exception {
        var repository = new InMemoryRankingRepository();
        repository.saveBest(new RankingEntry("Rai", 3, 10, 1234));
        try (var http = start(repository)) {
            var response = http.get("/api/ranking");
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow()
                    .startsWith("application/json"));
            var ranking = http.readJson(response);
            assertEquals(1, ranking.size());
            assertEquals("Rai", ranking.get(0).get("playerName").asText());
            assertEquals(3, ranking.get(0).get("score").asInt());
            assertEquals(10, ranking.get(0).get("totalQuestions").asInt());
            assertEquals(1234, ranking.get(0).get("playedAtEpochMillis").asLong());
        }
    }

    @Test
    void rejectsWrongMethodsAndAdministrativeRouteSuffixes() throws Exception {
        try (var http = start(new InMemoryRankingRepository())) {
            assertEquals(405, http.post("/api/ranking", Map.of()).statusCode());
            assertEquals(404, http.get("/api/ranking/extra").statusCode());
            assertEquals(404, http.post("/api/ranking/rename", Map.of()).statusCode());
        }
    }

    @Test
    void databaseFailureReturnsServiceUnavailableWithoutPrivateDetails() throws Exception {
        var repository = new InMemoryRankingRepository() {
            @Override
            public List<RankingEntry> findTop(int limit) throws SQLException {
                throw new SQLException("private test database details");
            }
        };
        try (var http = start(repository)) {
            var response = http.get("/api/ranking");
            assertEquals(503, response.statusCode());
            assertFalse(response.body().contains("private test database"));
        }
    }

    private TestHttpServer start(RankingRepository repository) throws Exception {
        return TestHttpServer.start(new RankingController(new RankingService(repository)));
    }
}
