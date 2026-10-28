package com.raibrs.geoquest.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

/** Shared HTTP mechanics; controllers choose operations and services apply rules. */
public final class JsonHttp {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final System.Logger LOGGER = System.getLogger(JsonHttp.class.getName());
    private static final int MAX_BODY_BYTES = 4096;

    private JsonHttp() {}

    @FunctionalInterface
    public interface Action {
        void run() throws IOException, InterruptedException;
    }

    public static void handle(HttpExchange exchange, Action action) throws IOException {
        // Every handler uses the same exception translation and resource cleanup.
        try {
            action.run();
        } catch (JsonProcessingException exception) {
            error(exchange, 400, "Invalid JSON.");
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, exception.getMessage());
        } catch (NoSuchElementException exception) {
            error(exchange, 404, exception.getMessage());
        } catch (IllegalStateException exception) {
            error(exchange, 409, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            error(exchange, 503, "The operation was interrupted. Please try again.");
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "HTTP or country catalog operation failed.", exception);
            error(exchange, 503, "Unable to complete the operation. Please try again.");
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.ERROR, "Unexpected HTTP failure.", exception);
            error(exchange, 500, "An internal error occurred. Please try again.");
        } finally {
            exchange.close();
        }
    }

    public static boolean requireJson(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json")) {
            error(exchange, 415, "Send the request body as application/json.");
            return false;
        }
        return true;
    }

    public static boolean requireMethod(HttpExchange exchange, String expected) throws IOException {
        if (!expected.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", expected);
            error(exchange, 405, "Method not allowed.");
            return false;
        }
        return true;
    }

    public static <T> T read(HttpExchange exchange, Class<T> type) throws IOException {
        // The extra byte detects oversized input without buffering an unlimited body.
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        T value = JSON.readValue(body, type);
        if (value == null) {
            throw new IllegalArgumentException("Provide the request data.");
        }
        return value;
    }

    public static void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    public static void error(HttpExchange exchange, int status, String message) throws IOException {
        // Never start a second response if a write failed after sending headers.
        if (exchange.getResponseCode() == -1) {
            send(exchange, status, Map.of("message", message));
        }
    }
}
