package com.raibrs.geoquest;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.support.InMemoryRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class AdminAppTest {
    private final InMemoryRankingRepository repository = new InMemoryRankingRepository();

    @BeforeEach
    void seed() throws Exception {
        repository.saveBest(new RankingEntry("Rai", 7, 10, 1234));
        repository.saveBest(new RankingEntry("Other", 2, 10, 5678));
    }

    @Test
    void confirmationRenamesOnlyTheSpecifiedRowWithoutListingPlayers() {
        String output = run("2\nRai\nRaibr\nSIM\n0\n");
        var saved = repository.allEntries().stream()
                .filter(entry -> entry.getPlayerName().equals("Raibr")).findFirst().orElseThrow();
        assertEquals(7, saved.getScore());
        assertEquals(1234, saved.getPlayedAtEpochMillis());
        assertEquals(2, repository.allEntries().size());
        assertFalse(output.contains("Other"));
        assertTrue(output.contains("Nome atualizado"));
    }

    @Test
    void cancellationAndClosedInputNeverRename() {
        var original = repository.allEntries();
        assertTrue(run("2\nRai\nRaibr\nnão\n0\n").contains("cancelada"));
        for (String input : new String[]{"", "2\n", "2\nRai\n", "2\nRai\nRaibr\n"}) {
            run(input);
            assertEquals(original, repository.allEntries());
        }
    }

    @Test
    void collisionAndMissingPlayerReportErrorsAndKeepTheMenuAvailable() {
        var original = repository.allEntries();
        assertTrue(run("2\nRai\nOTHER\nSIM\n0\n").contains("Já existe"));
        assertTrue(run("2\nMissing\nNew\nSIM\n0\n").contains("Não foi encontrado"));
        assertEquals(original, repository.allEntries());
    }

    @Test
    void invalidNameIsRejectedAndMenuAcceptsAnotherCommand() {
        var original = repository.allEntries();
        assertTrue(run("2\nRai\n   \nSIM\n0\n").contains("must not be blank"));
        assertEquals(original, repository.allEntries());
    }

    private String run(String commands) {
        var bytes = new ByteArrayOutputStream();
        try (var input = new Scanner(commands);
             var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            // These menu tests never choose DB diagnostics or open a JDBC connection.
            AdminApp.run(input, output, new RankingService(repository), new PGSimpleDataSource());
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
