package com.raibrs.geoquest.http;

import com.raibrs.geoquest.support.TestHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StaticFileHandlerTest {
    private TestHttpServer http;

    @BeforeEach
    void startServer() throws Exception {
        http = TestHttpServer.start(new StaticFileHandler(Path.of("frontend")));
    }

    @AfterEach
    void stopServer() {
        if (http != null) http.close();
    }

    @Test
    void servesTheBrowserAssets() throws Exception {
        for (String asset : List.of("/", "/styles.css", "/src/app.js", "/src/service/game-service.js")) {
            assertEquals(200, http.get(asset).statusCode());
        }
        assertTrue(http.get("/").body().contains("Geo Quest"));
    }

    @Test
    void doesNotExposePrivateFilesOrTheRemovedDemoRepository() throws Exception {
        for (String path : List.of("/pom.xml", "/../pom.xml", "/.git/config",
                "/src/repository/question-repository.js")) {
            assertEquals(404, http.get(path).statusCode());
        }
    }

    @Test
    void onlyAllowsGetRequests() throws Exception {
        assertEquals(405, http.post("/", Map.of()).statusCode());
    }
}
