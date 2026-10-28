package com.raibrs.geoquest.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.PortugueseGeography;
import java.util.ArrayList;
import java.util.List;

public class CountryJsonParser {

    private final ObjectMapper objectMapper;

    public CountryJsonParser() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Country> parse(String responseBody) {
        return parseCountries(readData(responseBody).path("objects"));
    }

    public CountryPage parsePage(String responseBody) {
        JsonNode data = readData(responseBody);
        JsonNode countries = data.path("objects");
        List<Country> parsedCountries = parseCountries(countries);
        JsonNode more = data.path("meta").path("more");

        if (!more.isBoolean()) {
            throw new IllegalArgumentException("REST Countries response has invalid pagination metadata.");
        }
        if (more.booleanValue() && countries.isEmpty()) {
            throw new IllegalArgumentException("REST Countries pagination did not advance.");
        }

        // Offsets count raw records, including countries discarded for invalid capital data.
        return new CountryPage(List.copyOf(parsedCountries), countries.size(), more.booleanValue());
    }

    public record CountryPage(List<Country> countries, int receivedCount, boolean hasMore) {
    }

    private JsonNode readData(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("REST Countries response is empty.");
        }

        try {
            return objectMapper.readTree(responseBody).path("data");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not parse the REST Countries response.", exception);
        }
    }

    private List<Country> parseCountries(JsonNode countries) {
        if (!countries.isArray()) {
            throw new IllegalArgumentException(
                    "REST Countries response does not contain a country list.");
        }

        List<Country> parsedCountries = new ArrayList<>();
        for (JsonNode countryNode : countries) {
            JsonNode nameNode = countryNode.path("names").path("common");
            JsonNode capitalsNode = countryNode.path("capitals");

            if (!nameNode.isTextual()
                    || !capitalsNode.isArray()
                    || capitalsNode.isEmpty()) {
                continue;
            }

            JsonNode capitalNameNode = capitalsNode.get(0).path("name");

            if (!capitalNameNode.isTextual()) {
                continue;
            }

            String name = nameNode.asText();
            String capital = capitalNameNode.asText();

            if (name.isBlank() || capital.isBlank()) {
                continue;
            }

            JsonNode translatedName = countryNode.path("names").path("translations").path("por").path("common");
            String portugueseName = translatedName.isTextual() ? translatedName.asText() : "";
            if (portugueseName.isBlank()) {
                portugueseName = PortugueseGeography.country(name, countryNode.path("codes").path("alpha_2").asText(""));
            }
            parsedCountries.add(new Country(name, capital, portugueseName, PortugueseGeography.capital(capital)));
        }

        return parsedCountries;
    }
}
