package com.raibrs.geoquest.ui;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleUiTest {

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
}
