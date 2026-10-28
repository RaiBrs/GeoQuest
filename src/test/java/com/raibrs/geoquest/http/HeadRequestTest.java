package com.raibrs.geoquest.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"API_KEY=test", "ACCOUNTS_ENABLED=false", "server.address=127.0.0.1"})
class HeadRequestTest {
    @LocalServerPort int port;

    @Test void servletContainerReturnsHeadHeadersWithoutBodiesIncludingErrors() throws Exception {
        // HEAD body suppression is a Servlet transport behavior, so exercise real Tomcat rather than MockMvc.
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            for (String path : List.of("/", "/styles.css", "/assets/geoquest-icon.svg")) {
                URI uri = URI.create("http://127.0.0.1:" + port + path);
                var get = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                var head = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, head.statusCode(), path);
                assertEquals(get.headers().firstValue("Content-Type"), head.headers().firstValue("Content-Type"), path);
                assertEquals(get.headers().firstValue("Content-Length"), head.headers().firstValue("Content-Length"), path);
                assertEquals("", head.body(), path);
            }
            for (String path : List.of("/missing.html", "/api/games")) {
                var head = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(path.equals("/api/games") ? 405 : 404, head.statusCode());
                assertEquals("", head.body(), path);
            }
        }
    }
}
