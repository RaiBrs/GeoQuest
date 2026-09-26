package com.raibrs.geoquest.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountriesClientTest {

    private final CountriesClient client = new CountriesClient("test-key");

    @Test
    void rejectsALimitOutsideTheApiRange() {
        assertThrows(IllegalArgumentException.class, () -> client.fetchCountries(0));
        assertThrows(IllegalArgumentException.class, () -> client.fetchCountries(101));
    }
}
