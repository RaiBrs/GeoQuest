package com.raibrs.geoquest;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.config.DatabaseCheck;
import com.raibrs.geoquest.config.DatabaseConfig;
import com.raibrs.geoquest.config.EnvironmentConfig;
import com.raibrs.geoquest.controller.GameController;
import com.raibrs.geoquest.controller.RankingController;
import com.raibrs.geoquest.http.JsonHttp;
import com.raibrs.geoquest.http.StaticFileHandler;
import com.raibrs.geoquest.repository.PostgresRankingRepository;
import com.raibrs.geoquest.service.GameService;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;

/** Application wiring and server lifecycle; HTTP behavior lives in handlers. */
public final class WebApp {
    private final GameService games;
    private final RankingService ranking;
    private final Path frontend;

    public WebApp(GameService games, RankingService ranking, Path frontend) {
        this.games = Objects.requireNonNull(games);
        this.ranking = Objects.requireNonNull(ranking);
        this.frontend = Objects.requireNonNull(frontend);
    }

    public static void main(String[] args) throws Exception {
        var environment = EnvironmentConfig.load();
        var source = DatabaseConfig.from(environment);
        DatabaseCheck.check(source, null, System.out);
        var ranking = new RankingService(new PostgresRankingRepository(source));
        var games = new GameService(new CountriesClient(EnvironmentConfig.required(environment, "API_KEY")),
                new QuestionService(), ranking);
        Path frontend = Path.of("frontend").toAbsolutePath();
        if (!Files.isRegularFile(frontend.resolve("index.html"))) {
            throw new IllegalStateException(
                    "Execute na pasta do pom.xml: frontend/index.html não encontrado.");
        }
        var server = new WebApp(games, ranking, frontend)
                .createServer(new InetSocketAddress("127.0.0.1", 8080));
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(1);
            executor.close();
        }));
        server.start();
        System.out.println("Geo Quest: http://localhost:8080");
    }

    // OS-selected ports keep integration tests independent of the user's running server.
    HttpServer createServer(InetSocketAddress address) throws IOException {
        HttpServer server = HttpServer.create(address, 0);
        var gameController = new GameController(games);
        for (String path : GameController.ROUTES) {
            server.createContext(path, gameController);
        }
        server.createContext(RankingController.ROUTE, new RankingController(ranking));
        server.createContext("/api/", exchange -> JsonHttp.handle(exchange,
                () -> JsonHttp.error(exchange, 404, "Endpoint not found.")));
        server.createContext("/", new StaticFileHandler(frontend));
        return server;
    }
}
