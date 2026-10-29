package com.raibrs.geoquest.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.*;

@RestController
public final class StaticFileHandler {
    private final Path frontend;
    public StaticFileHandler(Path frontend) { this.frontend = frontend; }
    // The MVC mappings are the allowlist; unmapped requests retain normal 404/405 behavior.
    @GetMapping({"/", "/index.html", "/privacy.html", "/styles.css", "/assets/geoquest-icon.svg",
            "/styles/tokens.css", "/styles/base.css", "/styles/layout.css", "/styles/components.css",
            "/styles/pages/hub.css", "/src/view/pages/account-view.js",
            "/styles/pages/welcome.css", "/styles/pages/game.css", "/styles/pages/result.css",
            "/src/controller/account-controller.js", "/src/i18n.js", "/src/app.js", "/src/controller/game-controller.js",
            "/src/service/game-service.js", "/src/service/http-client.js", "/src/view/dom.js", "/src/view/shell-view.js",
            "/src/view/pages/welcome-view.js", "/src/view/pages/hub-view.js", "/src/view/pages/game-view.js", "/src/view/pages/result-view.js"})
    public ResponseEntity<?> serve(HttpServletRequest request) throws IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI();
        String relative = path.equals("/") ? "index.html" : path.substring(1);
        if (!Files.isRegularFile(frontend.resolve(relative))) return ApiErrors.response(404, "File not found.");
        String type = relative.endsWith(".css") ? "text/css" : relative.endsWith(".js") ? "text/javascript"
                : relative.endsWith(".svg") ? "image/svg+xml" : "text/html";
        return ResponseEntity.ok().header("Content-Type", type + "; charset=UTF-8").header("Cache-Control", "no-store")
                .body(Files.readAllBytes(frontend.resolve(relative)));
    }
}
