package com.raibrs.geoquest.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.net.InetSocketAddress;

/** Local .env defaults with explicit process environment overrides. Never evaluates file contents. */
public final class EnvironmentConfig {
    private EnvironmentConfig() {}

    public static Map<String, String> load() {
        return load(Path.of(".env"), System.getenv());
    }

    static Map<String, String> load(Path file, Map<String, String> environment) {
        Map<String, String> values = new HashMap<>();
        if (!Files.notExists(file)) {
            try {
                int lineNumber = 0;
                for (String original : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    lineNumber++;
                    String line = original;
                    if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int separator = line.indexOf('=');
                    if (separator < 1) throw invalidLine(lineNumber);
                    String name = line.substring(0, separator).strip();
                    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || values.containsKey(name)) {
                        throw invalidLine(lineNumber);
                    }
                    String value = line.substring(separator + 1).strip();
                    if (value.startsWith("\"") || value.startsWith("'")) {
                        char quote = value.charAt(0);
                        if (value.length() < 2 || value.charAt(value.length() - 1) != quote) {
                            throw invalidLine(lineNumber);
                        }
                        value = value.substring(1, value.length() - 1);
                    }
                    // Preserve passwords literally: $, #, =, quotes, and backslashes are not executed or expanded.
                    values.put(name, value);
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read the local .env configuration.", exception);
            }
        }
        // Local defaults must never override an explicit deployment or IDE environment setting.
        values.putAll(environment);
        return Map.copyOf(values);
    }

    public static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configure " + name + " in .env or as an environment variable.");
        }
        return value;
    }

    public static InetSocketAddress serverAddress(Map<String, String> environment) {
        String host = environment.getOrDefault("HOST", "127.0.0.1").strip();
        String portValue = environment.getOrDefault("PORT", "8080").strip();
        if (host.isEmpty()) throw new IllegalArgumentException("HOST must not be blank.");
        try {
            if (!portValue.matches("[0-9]+")) throw new NumberFormatException();
            int port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) throw new NumberFormatException();
            return new InetSocketAddress(host, port);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PORT must be an integer between 1 and 65535.");
        }
    }

    private static IllegalArgumentException invalidLine(int number) {
        // Report only the location, never the potentially secret contents of a malformed line.
        return new IllegalArgumentException("Invalid .env entry at line " + number
                + ". Use one NAME=value per line; quoted values must close on the same line.");
    }
}
