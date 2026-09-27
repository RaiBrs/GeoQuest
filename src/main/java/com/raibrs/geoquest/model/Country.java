package com.raibrs.geoquest.model;

public class Country {
    private final String name;
    private final String capital;

    public Country(String name, String capital) {
        // Normalize data at the boundary so every Country instance is usable by the game.
        this.name = requireNonBlank(name, "Country name");
        this.capital = requireNonBlank(capital, "Country capital");
    }

    public String getName() {
        return name;
    }

    public String getCapital() {
        return capital;
    }

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
