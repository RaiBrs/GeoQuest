package com.raibrs.geoquest.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public final class StaticFileHandler implements HttpHandler {
    // An explicit allowlist prevents exposing credentials or other project files.
    private static final Set<String> FILES = Set.of(
            "index.html", "privacy.html", "styles.css", "assets/geoquest-icon.svg",
            "styles/base.css", "styles/layout.css", "styles/components.css",
            "styles/pages/welcome.css", "styles/pages/game.css", "styles/pages/result.css",
            "src/controller/account-controller.js", "src/i18n.js", "src/app.js", "src/controller/game-controller.js", "src/service/game-service.js",
            "src/view/dom.js", "src/view/shell-view.js",
            "src/view/pages/welcome-view.js", "src/view/pages/hub-view.js",
            "src/view/pages/game-view.js", "src/view/pages/result-view.js");
    private final Path frontend;

    public StaticFileHandler(Path frontend) {
        this.frontend = Objects.requireNonNull(frontend);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JsonHttp.handle(exchange, () -> serve(exchange));
    }

    private void serve(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            JsonHttp.error(exchange, 405, "Method not allowed.");
            return;
        }
        String requestPath = exchange.getRequestURI().getPath();
        String relativePath = requestPath.equals("/") ? "index.html" : requestPath.substring(1);
        if (!FILES.contains(relativePath) || !Files.isRegularFile(frontend.resolve(relativePath))) {
            JsonHttp.error(exchange, 404, "File not found.");
            return;
        }
        String contentType = "text/html";
        if (relativePath.endsWith(".css")) contentType = "text/css";
        else if (relativePath.endsWith(".js")) contentType = "text/javascript";
        else if (relativePath.endsWith(".svg")) contentType = "image/svg+xml";

        byte[] body = Files.readAllBytes(frontend.resolve(relativePath));
        JsonHttp.sendBytes(exchange, 200, contentType + "; charset=UTF-8", body);
    }
}
