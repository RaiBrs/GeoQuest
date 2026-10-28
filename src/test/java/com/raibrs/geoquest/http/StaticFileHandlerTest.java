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
        for (String asset : List.of("/", "/index.html", "/styles.css", "/styles/base.css",
                "/styles/layout.css", "/styles/components.css", "/styles/pages/welcome.css",
                "/styles/pages/game.css", "/styles/pages/result.css", "/src/app.js",
                "/src/controller/game-controller.js", "/src/service/game-service.js",
                "/src/view/dom.js", "/src/view/shell-view.js", "/src/view/pages/welcome-view.js",
                "/src/view/pages/game-view.js", "/src/view/pages/result-view.js", "/assets/geoquest-icon.svg")) {
            var response = http.get(asset);
            assertEquals(200, response.statusCode(), asset);
            String expectedType = asset.endsWith(".css") ? "text/css"
                    : asset.endsWith(".js") ? "text/javascript"
                    : asset.endsWith(".svg") ? "image/svg+xml" : "text/html";
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith(expectedType), asset);
        }
        assertTrue(http.get("/").body().contains("GeoQuest"));
        assertTrue(http.get("/assets/geoquest-icon.svg").headers()
                .firstValue("Content-Type").orElseThrow().startsWith("image/svg+xml"));
    }

    @Test
    void doesNotExposePrivateFilesOrTheRemovedDemoRepository() throws Exception {
        for (String path : List.of("/pom.xml", "/../pom.xml", "/.git/config",
                "/src/repository/question-repository.js", "/assets/unlisted.svg", "/tests/app.test.js",
                "/src/view/private.js", "/styles/unknown.css", "/styles/../../.env")) {
            assertEquals(404, http.get(path).statusCode());
        }
    }

    @Test
    void onlyAllowsGetRequests() throws Exception {
        assertEquals(405, http.post("/", Map.of()).statusCode());
    }
}
