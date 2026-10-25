package com.raibrs.geoquest.config;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.sql.SQLException;

public final class DatabaseCheck {
    private DatabaseCheck() {
    }

    public static void main(String[] args) throws SQLException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Provide at most one expected database name.");
        }
        check(DatabaseConfig.fromEnvironment(), args.length == 1 ? args[0] : null, System.out);
    }

    public static void check(DataSource source, String expectedDatabase, PrintStream output) throws SQLException {
        try (var connection = source.getConnection()) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("""
                         SELECT current_database() AS database_name, current_user AS username,
                                inet_server_addr()::text AS server_address,
                                inet_server_port() AS server_port,
                                to_regclass('ranking_entries') AS ranking_table
                         """)) {
                rows.next();
                String actualDatabase = rows.getString("database_name");
                output.println("Connected database: " + actualDatabase);
                output.println("Database user: " + rows.getString("username"));
                output.println("Server: " + rows.getString("server_address") + ":" + rows.getInt("server_port"));
                if (expectedDatabase != null && !actualDatabase.equals(expectedDatabase)) {
                    throw new IllegalStateException("Connected database differs from the expected database: "
                            + expectedDatabase);
                }
                if (rows.getString("ranking_table") == null) {
                    throw new IllegalStateException("ranking_entries is missing. Apply database migrations V1 and V2.");
                }
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("""
                         SELECT count(*) FROM pg_constraint
                         WHERE conrelid = to_regclass('ranking_entries') AND conname IN
                           ('ranking_player_name_length', 'ranking_player_key_length',
                            'ranking_player_name_no_controls', 'ranking_player_key_no_controls')
                         """)) {
                rows.next();
                if (rows.getInt(1) != 4) {
                    throw new IllegalStateException("Player name constraints are missing. Apply database migration V2.");
                }
                output.println("Ranking schema: ready (V1 and V2).");
            }
            connection.rollback();
        }
    }
}
