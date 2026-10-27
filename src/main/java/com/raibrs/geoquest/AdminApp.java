package com.raibrs.geoquest;

import com.raibrs.geoquest.config.DatabaseCheck;
import com.raibrs.geoquest.config.DatabaseConfig;
import com.raibrs.geoquest.repository.PostgresRankingRepository;
import com.raibrs.geoquest.service.RankingService;

import javax.sql.DataSource;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Trusted local maintenance with DB credentials, not authenticated player self-service. */
public final class AdminApp {
    private static final System.Logger LOGGER = System.getLogger(AdminApp.class.getName());

    private AdminApp() {}

    public static void main(String[] args) {
        try {
            var source = DatabaseConfig.fromEnvironment();
            var ranking = new RankingService(new PostgresRankingRepository(source));
            try (Scanner input = new Scanner(System.in)) {
                run(input, System.out, ranking, source);
            }
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        }
    }

    // Inject console I/O so confirmation, cancellation, and EOF can be tested without a real terminal.
    static void run(Scanner input, PrintStream output, RankingService ranking, DataSource source) {
        output.println("Administração local: requer acesso autorizado às credenciais do banco.");
        while (true) {
            output.println("\nGeo Quest — Administração local");
            output.println("1. Verificar banco de dados");
            output.println("2. Alterar nome no ranking");
            output.println("0. Sair");
            String option = ask(input, output, "Opção: ");
            if (option == null) return;
            try {
                switch (option.strip()) {
                    case "1" -> DatabaseCheck.check(source, null, output);
                    case "2" -> renamePlayer(input, output, ranking);
                    case "0" -> { return; }
                    default -> output.println("Opção inválida.");
                }
            } catch (SQLException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Administrative database operation failed.", exception);
                output.println("Não foi possível acessar o banco.");
            } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException exception) {
                output.println(exception.getMessage());
            }
        }
    }

    private static void renamePlayer(Scanner input, PrintStream output, RankingService ranking)
            throws SQLException {
        // Active rounds remember the old name and could recreate it on their next save.
        output.println("Execute esta manutenção com o servidor web parado.");
        String current = ask(input, output, "Nome atual: ");
        if (current == null) return;
        String replacement = ask(input, output, "Novo nome: ");
        if (replacement == null) return;
        String confirmation = ask(input, output, "Digite SIM para confirmar a alteração: ");
        if (confirmation == null) return;
        if (!"SIM".equals(confirmation.strip())) {
            output.println("Alteração cancelada.");
            return;
        }
        ranking.renamePlayer(current, replacement);
        output.println("Nome atualizado. Pontuação e data foram preservadas.");
    }

    private static String ask(Scanner input, PrintStream output, String prompt) {
        output.print(prompt);
        // EOF is different from a missing ranking row: it exits without another database operation.
        return input.hasNextLine() ? input.nextLine() : null;
    }
}
