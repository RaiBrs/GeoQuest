package com.raibrs.geoquest.api;

import com.raibrs.geoquest.model.Country;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountryJsonParserTest {

    private final CountryJsonParser parser = new CountryJsonParser();

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

    @Test
    void rejectsAnInvalidJsonResponse() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("not json"));
    }

    @Test
    void rejectsAnEmptyResponse() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }
}
