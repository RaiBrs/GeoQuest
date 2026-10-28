package com.raibrs.geoquest.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Map;
import java.util.Arrays;
import java.util.NoSuchElementException;

@RestControllerAdvice
public final class ApiErrors {
    private static final System.Logger LOG = System.getLogger(ApiErrors.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    public static ResponseEntity<Map<String, String>> response(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        JSON.writeValue(response.getOutputStream(), Map.of("message", message));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> invalidJson(HttpMessageNotReadableException error) {
        return response(400, error.getCause() == null ? "Provide the request data." : "Invalid JSON.");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException error) { return response(400, error.getMessage()); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException error) { return response(error.getStatusCode().value(), "Sign in to continue."); }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> argument() { return response(400, "Invalid request data."); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> missing(NoSuchElementException error) { return response(404, error.getMessage()); }
    @ExceptionHandler({org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class})
    ResponseEntity<?> noRoute() { return response(404, "Endpoint not found."); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException error) { return response(409, error.getMessage()); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> contentType() { return response(415, "Send the request body as application/json."); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(HttpRequestMethodNotSupportedException error) {
        return ResponseEntity.status(405).header("Allow", String.join(", ", error.getSupportedMethods()))
                .body(Map.of("message", "Method not allowed."));
    }
    @ExceptionHandler(IOException.class)
    ResponseEntity<?> unavailable(IOException error) {
        LOG.log(System.Logger.Level.WARNING, "External operation failed: " + error.getClass().getSimpleName());
        return response(503, "Unable to complete the operation. Please try again.");
    }
    @ExceptionHandler(InterruptedException.class)
    ResponseEntity<?> interrupted() { Thread.currentThread().interrupt(); return response(503, "The operation was interrupted. Please try again."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error) {
        String locations = Arrays.stream(error.getStackTrace()).limit(12).map(StackTraceElement::toString)
                .collect(java.util.stream.Collectors.joining("\n  at "));
        LOG.log(System.Logger.Level.ERROR, "Unexpected request failure: " + error.getClass().getSimpleName() + "\n  at " + locations);
        return response(500, "An internal error occurred. Please try again.");
    }
}
