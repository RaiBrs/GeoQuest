package com.raibrs.geoquest.ui;

import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.RankingEntry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleUiTest {

    // Ensures the menu retries invalid input and returns the selected action.
    @Test
    void asksAgainUntilThePlayerProvidesAValidMenuOption() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(
                new Scanner("maybe\n2\n"),
                new PrintStream(outputBytes));

        ConsoleUi.MainMenuOption option = ui.askMainMenuOption();

        assertEquals(ConsoleUi.MainMenuOption.VIEW_RANKING, option);
        assertTrue(outputBytes.toString().contains("Please choose an option from 1 to 4."));
    }

    // Ensures blank answers are retried instead of ending the current round as incorrect.
    @Test
    void asksAgainUntilThePlayerProvidesANonBlankAnswer() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(
                new Scanner("\n   \nTokyo\n"),
                new PrintStream(outputBytes));

        String answer = ui.askQuestion(new Question("What is the capital of Japan?", "Tokyo"));

        assertEquals("Tokyo", answer);
        assertTrue(outputBytes.toString().contains("Please enter an answer."));
    }

    // Ensures blank player names are retried and valid names are normalized before ranking use.
    @Test
    void asksAgainUntilThePlayerProvidesANonBlankName() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(
                new Scanner("\n   \n  Rai  \n"),
                new PrintStream(outputBytes));

        String playerName = ui.askPlayerName();

        assertEquals("Rai", playerName);
        assertTrue(outputBytes.toString().contains("Please enter a name."));
    }

    // Ensures the terminal displays the ranking order and score context provided by the service.
    @Test
    void displaysRankingEntriesWithTheirPositionsAndScores() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(new Scanner(""), new PrintStream(outputBytes));
        List<RankingEntry> ranking = List.of(
                new RankingEntry("Bia", 5, 5, 1_756_688_400_000L),
                new RankingEntry("Rai", 3, 5, 1_756_684_800_000L));

        ui.showRanking(ranking);

        String output = outputBytes.toString();
        assertTrue(output.contains("Top 10:"));
        assertTrue(output.contains("1. Bia - 5/5"));
        assertTrue(output.contains("2. Rai - 3/5"));
    }

    // Ensures an empty ranking gives the player useful feedback instead of a blank list.
    @Test
    void displaysAnEmptyRankingMessage() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(new Scanner(""), new PrintStream(outputBytes));

        ui.showRanking(List.of());

        assertTrue(outputBytes.toString().contains("No ranking entries yet."));
    }
}
