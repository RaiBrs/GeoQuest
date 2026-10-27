package com.raibrs.geoquest.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class StaticFileHandler implements HttpHandler {
    // An explicit allowlist prevents exposing credentials or other project files.
    private static final Map<String, String> FILES = Map.of(
            "/", "index.html", "/index.html", "index.html",
            "/styles.css", "styles.css", "/src/app.js", "src/app.js",
            "/src/service/game-service.js", "src/service/game-service.js");
    private final Path frontend;

    public StaticFileHandler(Path frontend) {
        this.frontend = Objects.requireNonNull(frontend);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JsonHttp.handle(exchange, () -> serve(exchange));
    }

    private void serve(HttpExchange exchange) throws IOException {
        if (!JsonHttp.requireMethod(exchange, "GET")) return;
        String relativePath = FILES.get(exchange.getRequestURI().getPath());
        if (relativePath == null || !Files.isRegularFile(frontend.resolve(relativePath))) {
            JsonHttp.error(exchange, 404, "Arquivo não encontrado.");
            return;
        }
        String contentType = "text/html";
        if (relativePath.endsWith(".css")) contentType = "text/css";
        else if (relativePath.endsWith(".js")) contentType = "text/javascript";

        byte[] body = Files.readAllBytes(frontend.resolve(relativePath));
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
    }
}
