package com.raibrs.geoquest.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountryTest {

    // Ensures country data is normalized once when it enters the domain model.
    @Test
    void normalizesCountryFields() {
        Country country = new Country(" Brazil ", " Brasília ");

        assertEquals("Brazil", country.getName());
        assertEquals("Brasília", country.getCapital());
    }

    // Prevents an unusable country from being created without a name.
    @Test
    void rejectsMissingCountryName() {
        assertThrows(IllegalArgumentException.class, () -> new Country(null, "Brasília"));
        assertThrows(IllegalArgumentException.class, () -> new Country("   ", "Brasília"));
    }

    // Prevents an unusable country from being created without a capital.
    @Test
    void rejectsMissingCountryCapital() {
        assertThrows(IllegalArgumentException.class, () -> new Country("Brazil", null));
        assertThrows(IllegalArgumentException.class, () -> new Country("Brazil", "   "));
    }
}
