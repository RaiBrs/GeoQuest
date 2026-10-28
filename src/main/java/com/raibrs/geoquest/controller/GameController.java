package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.StartRequest;
import com.raibrs.geoquest.dto.GameDtos.StatusRequest;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.GameLanguage;
import com.raibrs.geoquest.model.QuestionType;
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
    private static final String STATUS = "/api/games/status";
    // The server and controller share these paths so registration cannot drift from routing.
    public static final Set<String> ROUTES = Set.of(GAMES, ANSWERS, STATUS);
    private final GameService games;
    private final com.raibrs.geoquest.account.AccountController accounts;

    public GameController(GameService games) {
        this(games, new com.raibrs.geoquest.account.AccountController(null, null));
    }

    public GameController(GameService games, com.raibrs.geoquest.account.AccountController accounts) {
        this.games = Objects.requireNonNull(games);
        this.accounts = Objects.requireNonNull(accounts);
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
        if (!JsonHttp.requireMethod(exchange, "POST") || !accounts.requireOrigin(exchange) || !JsonHttp.requireJson(exchange)) return;
        var user = accounts.user(exchange);
        var owner = user == null ? null : user.id();

        switch (path) {
            case GAMES -> {
                var request = JsonHttp.read(exchange, StartRequest.class);
                JsonHttp.send(exchange, 201, games.start(
                        request.mode() == null ? GameMode.SURVIVAL : request.mode(),
                        request.questionType() == null ? QuestionType.CAPITAL : request.questionType(),
                        request.language() == null ? GameLanguage.EN : request.language(), owner));
            }
            case STATUS -> {
                var state = games.status(JsonHttp.read(exchange, StatusRequest.class).gameId(), owner);
                accounts.save(user, state, games.questionType(state.gameId()));
                JsonHttp.send(exchange, 200, state);
            }
            case ANSWERS -> {
                var request = JsonHttp.read(exchange, AnswerRequest.class);
                var answer = games.answer(request, owner);
                accounts.save(user, answer.state(), games.questionType(request.gameId()));
                JsonHttp.send(exchange, 200, answer);
            }
            default -> throw new IllegalStateException("Unexpected game route.");
        }
    }
}
