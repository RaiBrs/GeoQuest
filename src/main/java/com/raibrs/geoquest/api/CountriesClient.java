package com.raibrs.geoquest.api;

import com.raibrs.geoquest.model.Country;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CountriesClient {

    private static final String BASE_URL = "https://api.restcountries.com/countries/v5";
    private static final int MAX_COUNTRIES_PER_REQUEST = 100;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String apiKey;
    private final URI baseUri;
    private final Duration requestTimeout;

    public CountriesClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                URI.create(BASE_URL), REQUEST_TIMEOUT);
    }

    CountriesClient(String apiKey, HttpClient httpClient, URI baseUri, Duration requestTimeout) {
        if (apiKey == null || apiKey.isBlank()
                || apiKey.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new IllegalArgumentException("API key must contain only visible ASCII characters.");
        }
        this.httpClient = Objects.requireNonNull(httpClient);
        this.apiKey = apiKey;
        this.baseUri = Objects.requireNonNull(baseUri);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
    }

    public String fetchCountries(int limit) throws IOException, InterruptedException {
        return fetchCountries(limit, 0);
    }

    public List<Country> fetchAllCountries(int pageSize) throws IOException, InterruptedException {
        CountryJsonParser parser = new CountryJsonParser();
        List<Country> countries = new ArrayList<>();
        int offset = 0;

        while (true) {
            String responseBody = fetchCountries(pageSize, offset);
            CountryJsonParser.CountryPage page;
            try {
                page = parser.parsePage(responseBody);
            } catch (IllegalArgumentException exception) {
                throw new IOException("REST Countries API returned invalid country data.", exception);
            }
            countries.addAll(page.countries());
            if (!page.hasMore()) {
                return List.copyOf(countries);
            }
            if (offset > Integer.MAX_VALUE - page.receivedCount()) {
                throw new IOException("REST Countries pagination offset is too large.");
            }
            offset += page.receivedCount();
        }
    }

    String fetchCountries(int limit, int offset) throws IOException, InterruptedException {
        if (limit < 1 || limit > MAX_COUNTRIES_PER_REQUEST) {
            throw new IllegalArgumentException(
                    "The number of countries must be between 1 and "
                            + MAX_COUNTRIES_PER_REQUEST + ".");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Country offset must not be negative.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                // Ask only for the fields we need to keep the response small.
                .uri(URI.create(baseUri + "?response_fields=names.common,capitals&limit=" + limit
                        + "&offset=" + offset))
                .header("Authorization", "Bearer " + apiKey)
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Never treat an error body (e.g. 401 for an invalid key) as country data.
        if (response.statusCode() != 200) {
            throw new IOException(
                    "REST Countries API returned status " + response.statusCode());
        }

        return response.body();
    }
}
