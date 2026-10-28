package com.raibrs.geoquest.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentConfigTest {
    @TempDir
    Path directory;

    @Test
    void worksWithOnlyProcessSettingsWhenTheLocalFileIsMissing() {
        var environment = Map.of("EXAMPLE_NAME", "test-user");
        assertEquals(environment, EnvironmentConfig.load(directory.resolve("missing"), environment));
    }

    @Test
    void readsUtf8BomCommentsAndWhitespace() throws Exception {
        Path file = file("\uFEFF# Local settings\n\n EXAMPLE_NAME = Jogador \nEXAMPLE_URL=https://example.test/api\n");
        var environment = EnvironmentConfig.load(file, Map.of());
        assertEquals("Jogador", environment.get("EXAMPLE_NAME"));
        assertEquals("https://example.test/api", environment.get("EXAMPLE_URL"));
    }

    @Test
    void preservesQuotedSecretsAndTreatsTheirContentsAsLiteralData() throws Exception {
        Path file = file("EXAMPLE_SECRET=\"  fake#password=with\\slashes$HOME\"inside  \"\n"
                + "API_KEY='fake=key#literal'\n");
        var environment = EnvironmentConfig.load(file, Map.of());
        assertEquals("  fake#password=with\\slashes$HOME\"inside  ", environment.get("EXAMPLE_SECRET"));
        assertEquals("fake=key#literal", environment.get("API_KEY"));
    }

    @Test
    void preservesUnquotedHashesEqualsAndBackslashes() throws Exception {
        var environment = EnvironmentConfig.load(file("EXAMPLE_SECRET=fake#secret=a\\b\n"), Map.of());
        assertEquals("fake#secret=a\\b", environment.get("EXAMPLE_SECRET"));
    }

    @Test
    void explicitEnvironmentOverridesLocalDefaults() throws Exception {
        var environment = EnvironmentConfig.load(file("EXAMPLE_NAME=local\nAPI_KEY=local-key\n"),
                Map.of("EXAMPLE_NAME", "deployment", "EXTRA", "external"));
        assertEquals("deployment", environment.get("EXAMPLE_NAME"));
        assertEquals("local-key", environment.get("API_KEY"));
        assertEquals("external", environment.get("EXTRA"));
    }

    @Test
    void emptyValuesAreValidatedWithoutExposingSecrets() throws Exception {
        var environment = EnvironmentConfig.load(file("EXAMPLE_SECRET=\nAPI_KEY=\"\"\n"), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> EnvironmentConfig.required(environment, "EXAMPLE_SECRET"));
        assertThrows(IllegalArgumentException.class,
                () -> EnvironmentConfig.required(environment, "API_KEY"));
    }

    @Test
    void malformedOrDuplicateEntriesReportOnlyTheLineNumber() throws Exception {
        for (String invalid : new String[]{"fake-secret-without-separator", "BAD NAME=fake-secret",
                "API_KEY=\"fake-secret", "API_KEY=fake-secret\nAPI_KEY=duplicate"}) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> EnvironmentConfig.load(file(invalid), Map.of()));
            assertTrue(exception.getMessage().contains("line "));
            assertFalse(exception.getMessage().contains("fake-secret"));
            assertFalse(exception.getMessage().contains("duplicate"));
        }
    }

    @Test
    void unreadableConfigurationFailsClearly() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> EnvironmentConfig.load(directory, Map.of()));
        assertEquals("Could not read the local .env configuration.", exception.getMessage());
    }

    private Path file(String contents) throws Exception {
        return Files.writeString(directory.resolve(".env"), contents);
    }
}
