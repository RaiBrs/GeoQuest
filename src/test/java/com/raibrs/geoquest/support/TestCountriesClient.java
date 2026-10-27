package com.raibrs.geoquest.support;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.model.Country;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

/** Deterministic catalog: capital names can be derived without exposing real API keys. */
public class TestCountriesClient extends CountriesClient {
    public int calls;
    public boolean failing;

    public TestCountriesClient() {
        super("fake-test-key");
    }

    @Override
    public List<Country> fetchAllCountries(int pageSize) throws IOException {
        calls++;
        if (failing) {
            throw new IOException("Test country service unavailable.");
        }
        return IntStream.range(0, 40)
                .mapToObj(index -> new Country("Country " + index, "Capital " + index))
                .toList();
    }

    public static String answerFor(String question) {
        return question.replace("What is the capital of Country ", "Capital ")
                .replace("?", "");
    }
}
