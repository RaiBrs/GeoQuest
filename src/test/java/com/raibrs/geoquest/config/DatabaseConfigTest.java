package com.raibrs.geoquest.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {
    @Test
    void configuresPostgresWithoutOpeningAConnection() {
        var source = DatabaseConfig.from(environment());

        assertEquals("localhost", source.getServerNames()[0]);
        assertEquals(5432, source.getPortNumbers()[0]);
        assertEquals("GeoQuestDB", source.getDatabaseName());
        assertEquals("geoquest", source.getUser());
        assertEquals("test-password", source.getPassword());
        assertEquals(5, source.getConnectTimeout());
        assertEquals(10, source.getSocketTimeout());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD"})
    void rejectsMissingSettingsWithoutPrintingCredentials(String setting) {
        Map<String, String> environment = new HashMap<>(environment());
        environment.remove(setting);

        var exception = assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.from(environment));

        assertEquals("Set the " + setting + " environment variable.", exception.getMessage());
        assertFalse(exception.getMessage().contains("test-password"));
    }

    @Test
    void rejectsOtherDatabaseProtocols() {
        Map<String, String> environment = new HashMap<>(environment());
        environment.put("DATABASE_URL", "jdbc:mysql://localhost/geoquest");

        assertThrows(IllegalArgumentException.class, () -> DatabaseConfig.from(environment));
    }

    private Map<String, String> environment() {
        return Map.of("DATABASE_URL", "jdbc:postgresql://localhost:5432/GeoQuestDB",
                "DATABASE_USER", "geoquest", "DATABASE_PASSWORD", "test-password");
    }
}
