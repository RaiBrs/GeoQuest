package com.raibrs.geoquest.api;

import com.raibrs.geoquest.model.Country;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountryJsonParserTest {

    private final CountryJsonParser parser = new CountryJsonParser();

    // Ensures countries without a usable capital are excluded from the game question pool.
    @Test
    void parsesCountriesWithOneCapital() {
        String responseBody = """
                {
                  "data": {
                    "objects": [
                      {
                        "names": { "common": "Brazil" },
                        "capitals": [{ "name": "Brasília" }]
                      },
                      {
                        "names": { "common": "Antarctica" },
                        "capitals": []
                      }
                    ]
                  }
                }
                """;

        List<Country> countries = parser.parse(responseBody);

        assertEquals(1, countries.size());
        Country country = countries.getFirst();
        assertEquals("Brazil", country.getName());
        assertEquals("Brasília", country.getCapital());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":{\"name\":\"Capital\"}}",
            "{\"names\":{\"common\":\"Invalid\"}}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[]}",
            "{\"names\":{\"common\":null},\"capitals\":[{\"name\":\"Capital\"}]}",
            "{\"names\":{\"common\":123},\"capitals\":[{\"name\":\"Capital\"}]}",
            "{\"names\":{\"common\":\"   \"},\"capitals\":[{\"name\":\"Capital\"}]}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[{\"name\":null}]}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[{\"name\":true}]}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[{\"name\":\"   \"}]}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[null]}",
            "{\"capitals\":[{\"name\":\"Capital\"}]}",
            "{\"names\":{\"common\":\"Invalid\"},\"capitals\":[{}]}"
    })
    void skipsInvalidCountriesAndPreservesValidOnes(String invalidCountry) {
        String responseBody = """
                {
                  "data": {
                    "objects": [
                      %s,
                      {
                        "names": { "common": "Brazil" },
                        "capitals": [{ "name": "Brasília" }]
                      }
                    ]
                  }
                }
                """.formatted(invalidCountry);

        List<Country> countries = parser.parse(responseBody);

        assertEquals(1, countries.size());
        assertEquals("Brazil", countries.getFirst().getName());
        assertEquals("Brasília", countries.getFirst().getCapital());
    }

    // Prevents malformed API payloads from being mistaken for valid country data.
    @Test
    void rejectsAnInvalidJsonResponse() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("not json"));
    }

    // Prevents an empty API response from reaching the question creation flow.
    @Test
    void rejectsAnEmptyResponse() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }

    @Test
    void countsDiscardedRecordsWhenParsingPagination() {
        CountryJsonParser.CountryPage page = parser.parsePage("""
                {"data":{"objects":[
                  {"names":{"common":"Brazil"},"capitals":[{"name":"Brasília"}]},
                  {"names":{"common":"Antarctica"},"capitals":[]}
                ],"meta":{"more":true}}}
                """);

        assertEquals(2, page.receivedCount());
        assertEquals(1, page.countries().size());
        assertEquals("Brazil", page.countries().getFirst().getName());
        assertTrue(page.hasMore());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"data\":{\"objects\":[]}}",
            "{\"data\":{\"objects\":[],\"meta\":{\"more\":\"false\"}}}",
            "{\"data\":{\"objects\":[],\"meta\":{\"more\":true}}}"
    })
    void rejectsInvalidPaginationOrPagesWithoutProgress(String responseBody) {
        assertThrows(IllegalArgumentException.class, () -> parser.parsePage(responseBody));
    }
}
