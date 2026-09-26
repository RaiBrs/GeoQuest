package com.raibrs.geoquest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.raibrs.geoquest.model.Country;
import java.util.ArrayList;
import java.util.List;

public class CountryJsonParser {

    private final ObjectMapper objectMapper;

    public CountryJsonParser() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Country> parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("REST Countries response is empty.");
        }

        try {
            JsonNode countries = objectMapper.readTree(responseBody)
                    .path("data")
                    .path("objects");

            if (!countries.isArray()) {
                throw new IllegalArgumentException(
                        "REST Countries response does not contain a country list.");
            }

            List<Country> parsedCountries = new ArrayList<>();
            for (JsonNode countryNode : countries) {
                String name = countryNode.path("names").path("common").asText();
                JsonNode capitals = countryNode.path("capitals");

                // Countries without a capital cannot produce a valid question.
                if (name.isBlank() || capitals.isEmpty()) {
                    continue;
                }

                String capital = capitals.get(0).path("name").asText();
                if (!capital.isBlank()) {
                    parsedCountries.add(new Country(name, capital));
                }
            }

            return parsedCountries;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse the REST Countries response.", exception);
        }
    }
}
