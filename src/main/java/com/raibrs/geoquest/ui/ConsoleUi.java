package com.raibrs.geoquest.ui;

import com.raibrs.geoquest.model.Question;

import java.io.PrintStream;
import java.util.Scanner;

public class ConsoleUi {

    private final Scanner scanner;
    private final PrintStream output;

    public ConsoleUi() {
        this(new Scanner(System.in), System.out);
    }

    ConsoleUi(Scanner scanner, PrintStream output) {
        this.scanner = scanner;
        this.output = output;
    }

    public String askQuestion(Question question) {
        output.println();
        output.println(question.getText());

        while (true) {
            output.print("Your answer: ");
            String answer = readLine().strip();

            // A blank line is not an attempt and must not end the player's round.
            if (!answer.isEmpty()) {
                return answer;
            }

            output.println("Please enter an answer.");
        }
    }

    public void showCorrectAnswer(int score) {
        output.println("Correct! Score: " + score);
    }

    public void showIncorrectAnswer(Question question) {
        output.println("Incorrect. The correct answer is "
                + question.getCorrectAnswer() + ".");
    }

    public void showRoundSummary(int score, int totalQuestions) {
        output.println("Round over. Final score: " + score + "/" + totalQuestions + ".");
    }

    public boolean askToPlayAgain() {
        while (true) {
            output.print("Try again? (y/n): ");
            String choice = readLine().trim();

            if (choice.equalsIgnoreCase("y") || choice.equalsIgnoreCase("yes")) {
                return true;
            }
            if (choice.equalsIgnoreCase("n") || choice.equalsIgnoreCase("no")) {
                return false;
            }

            output.println("Please enter y or n.");
        }
    }

    public void showError(String message) {
        output.println("Error: " + message);
    }

    public void showGoodbye() {
        output.println("Thanks for playing GeoQuest!");
    }

    private String readLine() {
        if (!scanner.hasNextLine()) {
            throw new IllegalStateException("Input is no longer available.");
        }

        return scanner.nextLine();
    }
}
