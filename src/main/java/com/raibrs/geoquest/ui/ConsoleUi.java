package com.raibrs.geoquest.ui;

import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.RankingEntry;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ConsoleUi {

    public enum MainMenuOption {
        PLAY,
        VIEW_RANKING,
        DELETE_RANKING,
        LOGOUT,
        EXIT
    }

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
        printAlternatives(question);

        while (true) {
            output.print("Your answer (A-D): ");
            String choice = readLine().strip().toUpperCase(Locale.ROOT);

            // The UI maps a letter to its alternative so game rules still compare answer text.
            if (choice.length() == 1 && choice.charAt(0) >= 'A' && choice.charAt(0) <= 'D') {
                return question.getAlternatives().get(choice.charAt(0) - 'A');
            }

            output.println("Please enter a letter from A to D.");
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

    public void showRanking(List<RankingEntry> ranking) {
        output.println();
        output.println("Top 10:");

        // RankingService already sorts and limits the list; the UI only formats its positions.
        if (ranking.isEmpty()) {
            output.println("No ranking entries yet.");
            return;
        }

        for (int index = 0; index < ranking.size(); index++) {
            RankingEntry entry = ranking.get(index);
            output.println((index + 1) + ". " + entry.getPlayerName() + " - "
                    + entry.getScore() + "/" + entry.getTotalQuestions());
        }
    }

    public MainMenuOption askMainMenuOption() {
        while (true) {
            output.println();
            output.println("1. Play a round");
            output.println("2. View ranking");
            output.println("3. Delete my ranking entry");
            output.println("4. Logout");
            output.println("5. Exit");
            output.print("Choose an option: ");
            String choice = readLine().strip();

            // Explicit commands keep the player's session predictable until logout or exit.
            switch (choice) {
                case "1" -> {
                    return MainMenuOption.PLAY;
                }
                case "2" -> {
                    return MainMenuOption.VIEW_RANKING;
                }
                case "3" -> {
                    return MainMenuOption.DELETE_RANKING;
                }
                case "4" -> {
                    return MainMenuOption.LOGOUT;
                }
                case "5" -> {
                    return MainMenuOption.EXIT;
                }
                default -> output.println("Please choose an option from 1 to 5.");
            }
        }
    }

    public boolean askToConfirmRankingDeletion() {
        while (true) {
            output.print("Delete your ranking entry? (y/n): ");
            String choice = readLine().strip();

            // Deletion is permanent, so only an explicit confirmation may continue.
            if (choice.equalsIgnoreCase("y") || choice.equalsIgnoreCase("yes")) {
                return true;
            }
            if (choice.equalsIgnoreCase("n") || choice.equalsIgnoreCase("no")) {
                return false;
            }

            output.println("Please enter y or n.");
        }
    }

    public void showRankingEntryDeleted() {
        output.println("Your ranking entry was deleted.");
    }

    public void showNoRankingEntryToDelete() {
        output.println("You do not have a ranking entry yet.");
    }

    public void showRankingDeletionCancelled() {
        output.println("Ranking entry deletion cancelled.");
    }

    public void showError(String message) {
        output.println("Error: " + message);
    }

    public void showGoodbye() {
        output.println("Thanks for playing GeoQuest!");
    }

    private void printAlternatives(Question question) {
        for (int index = 0; index < question.getAlternatives().size(); index++) {
            char letter = (char) ('A' + index);
            output.println(letter + ". " + question.getAlternatives().get(index));
        }
    }

    private String readLine() {
        // Stop the application flow when the input stream closes instead of returning invalid data.
        if (!scanner.hasNextLine()) {
            throw new IllegalStateException("Input is no longer available.");
        }

        return scanner.nextLine();
    }

    public String askPlayerName() {
        while (true) {
            output.print("Player name: ");
            String playerName = readLine().strip();

            // Normalize the name and retry so every future ranking entry identifies a player.
            if (!playerName.isEmpty()) {
                return playerName;
            }

            output.println("Please enter a name.");
        }
    }
}
