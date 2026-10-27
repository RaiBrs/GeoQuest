package com.raibrs.geoquest.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Shared transport setup for component tests and application-wiring tests, without business assertions. */
public final class TestHttpServer implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final HttpServer server;
    private final HttpClient client;
    private final String baseUrl;

    public TestHttpServer(HttpServer server) {
        this.server = server;
        this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        server.start();
        this.baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public static TestHttpServer start(HttpHandler handler) throws IOException {
        // Bind only to loopback on a free port; component tests register just their target handler.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        return new TestHttpServer(server);
    }

    public HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> post(String path, Object body) throws IOException, InterruptedException {
        return postRaw(path, JSON.writeValueAsString(body), "application/json");
    }

    public HttpResponse<String> postRaw(String path, String body, String contentType)
            throws IOException, InterruptedException {
        var request = request(path).POST(HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) request.header("Content-Type", contentType);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    public JsonNode readJson(HttpResponse<String> response) throws IOException {
        return JSON.readTree(response.body());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(TIMEOUT);
    }

    @Override
    public void close() {
        server.stop(0);
        client.close();
    }
}
