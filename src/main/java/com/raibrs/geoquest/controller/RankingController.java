package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.http.JsonHttp;
import com.raibrs.geoquest.service.RankingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;

public final class RankingController implements HttpHandler {
    public static final String ROUTE = "/api/ranking";
    private final RankingService ranking;

    public RankingController(RankingService ranking) {
        this.ranking = Objects.requireNonNull(ranking);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        JsonHttp.handle(exchange, () -> {
            if (!exchange.getRequestURI().getPath().equals(ROUTE)) {
                JsonHttp.error(exchange, 404, "Operação não encontrada.");
                return;
            }
            if (!JsonHttp.requireMethod(exchange, "GET")) return;
            // Administrative mutations deliberately have no web route.
            JsonHttp.send(exchange, 200, ranking.getTopRanking());
        });
    }
}
