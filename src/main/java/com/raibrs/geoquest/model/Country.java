package com.raibrs.geoquest.model;

public class Country {
    private final String name;
    private final String capital;
    private final String portugueseName;
    private final String portugueseCapital;

    public Country(String name, String capital) {
        this(name, capital, PortugueseGeography.country(name, null), PortugueseGeography.capital(capital));
    }

    public Country(String name, String capital, String portugueseName, String portugueseCapital) {
        // Normalize data at the boundary so every Country instance is usable by the game.
        this.name = requireNonBlank(name, "Country name");
        this.capital = requireNonBlank(capital, "Country capital");
        this.portugueseName = optionalName(portugueseName);
        this.portugueseCapital = optionalName(portugueseCapital);
    }

    public String getName() {
        return name;
    }

    public String getCapital() {
        return capital;
    }

    public boolean supports(GameLanguage language) {
        return language == GameLanguage.EN || (portugueseName != null && portugueseCapital != null);
    }

    public String getName(GameLanguage language) { return language == GameLanguage.EN ? name : portugueseName; }
    public String getCapital(GameLanguage language) { return language == GameLanguage.EN ? capital : portugueseCapital; }

    private static String optionalName(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    @Override
    public String toString() {
        return "Country{name=" + name + ", capital=" + capital + "}";
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }

        return value.strip();
    }
}
