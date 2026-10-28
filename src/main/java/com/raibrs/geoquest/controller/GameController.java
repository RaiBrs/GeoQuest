package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.StartRequest;
import com.raibrs.geoquest.http.JsonHttp;
import com.raibrs.geoquest.service.GameService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

public final class GameController implements HttpHandler {
    private static final String GAMES = "/api/games";
    private static final String ANSWERS = "/api/answers";
    // The server and controller share these paths so registration cannot drift from routing.
    public static final Set<String> ROUTES = Set.of(GAMES, ANSWERS);
    private final GameService games;

    public GameController(GameService games) {
        this.games = Objects.requireNonNull(games);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JsonHttp.handle(exchange, () -> route(exchange));
    }

    private void route(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        // HttpServer contexts match prefixes; reject suffixes instead of treating them as real routes.
        if (!ROUTES.contains(path)) {
            JsonHttp.error(exchange, 404, "Endpoint not found.");
            return;
        }
        if (!JsonHttp.requireMethod(exchange, "POST") || !JsonHttp.requireJson(exchange)) return;

        switch (path) {
            case GAMES -> {
                JsonHttp.read(exchange, StartRequest.class);
                JsonHttp.send(exchange, 201, games.start());
            }
            case ANSWERS -> {
                var request = JsonHttp.read(exchange, AnswerRequest.class);
                JsonHttp.send(exchange, 200, games.answer(request));
            }
            default -> throw new IllegalStateException("Unexpected game route.");
        }
    }
}
