package com.raibrs.geoquest.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Offline place labels: country names from the JDK locale data, city labels from a versioned catalog. */
public final class PortugueseGeography {
    private static final Locale PORTUGUESE = Locale.forLanguageTag("pt-BR");
    private static final Map<String, String> CAPITALS = loadCapitals();
    private static final Map<String, String> COUNTRIES = loadCountries();
    private PortugueseGeography() {}

    public static String country(String englishName, String code) {
        if (code != null && code.matches("[A-Z]{2}")) {
            var locale = new Locale.Builder().setRegion(code).build();
            if (java.util.Set.of(Locale.getISOCountries()).contains(code)) return locale.getDisplayCountry(PORTUGUESE);
        }
        return COUNTRIES.get(key(englishName));
    }

    public static String capital(String englishName) { return CAPITALS.get(key(englishName)); }

    private static String key(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Map<String, String> loadCountries() {
        Map<String, String> result = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            var locale = new Locale.Builder().setRegion(code).build();
            result.put(key(locale.getDisplayCountry(Locale.ENGLISH)), locale.getDisplayCountry(PORTUGUESE));
        }
        result.put(key("Abkhazia"), "Abecásia");
        result.put(key("Northern Cyprus"), "Chipre do Norte");
        result.put(key("Somaliland"), "Somalilândia");
        result.put(key("South Ossetia"), "Ossétia do Sul");
        return Map.copyOf(result);
    }

    private static Map<String, String> loadCapitals() {
        try (var input = PortugueseGeography.class.getResourceAsStream("/localization/capitals-pt.json")) {
            if (input == null) throw new IllegalStateException("Missing Portuguese capital catalog.");
            Map<String, String> raw = new ObjectMapper().readValue(input, new TypeReference<Map<String, String>>() {});
            Map<String, String> result = new HashMap<>();
            raw.forEach((english, portuguese) -> result.put(key(english), portuguese));
            return Map.copyOf(result);
        } catch (IOException exception) { throw new IllegalStateException("Invalid Portuguese capital catalog.", exception); }
    }
}
