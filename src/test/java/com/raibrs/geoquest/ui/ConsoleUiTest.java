package com.raibrs.geoquest.ui;

import com.raibrs.geoquest.model.Question;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleUiTest {

    // Ensures the replay prompt keeps asking until the player provides a supported choice.
    @Test
    void asksAgainUntilThePlayerProvidesAValidChoice() {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ConsoleUi ui = new ConsoleUi(
                new Scanner("maybe\ny\n"),
                new PrintStream(outputBytes));

        boolean playAgain = ui.askToPlayAgain();

        assertTrue(playAgain);
        assertTrue(outputBytes.toString().contains("Please enter y or n."));
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
}
