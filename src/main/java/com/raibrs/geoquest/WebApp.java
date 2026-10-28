package com.raibrs.geoquest;

import com.raibrs.geoquest.account.*;
import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.config.EnvironmentConfig;
import com.raibrs.geoquest.service.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class WebApp {
    public static void main(String[] args) {
        var environment = EnvironmentConfig.load();
        var address = EnvironmentConfig.serverAddress(environment);
        var defaults = new HashMap<String, Object>();
        defaults.putAll(environment);
        defaults.put("server.address", address.getHostString());
        defaults.put("server.port", address.getPort());
        defaults.put("server.servlet.session.cookie.secure", environment.getOrDefault("APP_URL", "").startsWith("https://"));
        var app = new SpringApplication(WebApp.class);
        app.setDefaultProperties(defaults);
        app.run(args);
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean Path frontend() { return Path.of("frontend").toAbsolutePath(); }
    @Bean CountriesClient countries(@org.springframework.beans.factory.annotation.Value("${API_KEY}") String key) {
        return new CountriesClient(key);
    }
    @Bean GameService games(Clock clock, CountriesClient countries) {
        return new GameService(countries, new QuestionService(), clock);
    }
    @Bean AccountSessions accountSessions(org.springframework.core.env.Environment environment) throws java.io.IOException {
        var values = new HashMap<String, String>();
        for (String key : java.util.List.of("ACCOUNTS_ENABLED", "APP_URL", "JDBC_DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD",
                "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GITHUB_CLIENT_ID", "GITHUB_CLIENT_SECRET")) {
            values.put(key, environment.getProperty(key, ""));
        }
        var config = AccountConfig.from(values);
        var backend = config == null ? null : new PostgresAccounts(config);
        if (backend != null) backend.verifySchema();
        return new AccountSessions(config, backend);
    }
}
