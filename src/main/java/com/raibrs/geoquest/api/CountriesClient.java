package com.raibrs.geoquest.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CountriesClient {

    private static final String BASE_URL = "https://api.restcountries.com/countries/v5";
    private static final int MAX_COUNTRIES_PER_REQUEST = 100;

    private final HttpClient httpClient;
    private final String apiKey;

    public CountriesClient(String apiKey) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = apiKey;
    }

    public String fetchCountries(int limit) throws IOException, InterruptedException {
        if (limit < 1 || limit > MAX_COUNTRIES_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "The number of countries must be between 1 and "
                            + MAX_COUNTRIES_PER_REQUEST + ".");
        }

        HttpRequest request = HttpRequest.newBuilder()
                // Ask only for the fields we need to keep the response small.
                .uri(URI.create(BASE_URL + "?response_fields=names.common,capitals&limit=" + limit))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Never treat an error body (e.g. 401 for an invalid key) as country data.
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "REST Countries API returned status " + response.statusCode());
        }

        return response.body();
    }
}
