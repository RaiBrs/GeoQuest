package com.raibrs.geoquest.config;

import org.postgresql.ds.PGSimpleDataSource;

import java.util.Map;

public final class DatabaseConfig {
    private DatabaseConfig() {
    }

    public static PGSimpleDataSource fromEnvironment() {
        return from(System.getenv());
    }

    static PGSimpleDataSource from(Map<String, String> environment) {
        String url = required(environment, "DATABASE_URL");
        String user = required(environment, "DATABASE_USER");
        String password = required(environment, "DATABASE_PASSWORD");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must be a PostgreSQL JDBC URL.");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setConnectTimeout(5);
        dataSource.setSocketTimeout(10);
        dataSource.setApplicationName("GeoQuest");
        return dataSource;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set the " + name + " environment variable.");
        }
        return value;
    }
}
