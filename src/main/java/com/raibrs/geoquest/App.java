package com.raibrs.geoquest;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.api.CountryJsonParser;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.ui.ConsoleUi;

import java.io.IOException;
import java.util.List;

public class App {

    private static final int QUESTIONS_PER_ROUND = 5;
    private static final int COUNTRIES_PER_REQUEST = 100;

    public static void main(String[] args) {
        ConsoleUi ui = new ConsoleUi();

        // Read the key from the environment so the secret never ends up in Git.
        String apiKey = System.getenv("API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            ui.showError("Set the API_KEY environment variable.");
            return;
        }

        CountriesClient client = new CountriesClient(apiKey);
        CountryJsonParser parser = new CountryJsonParser();
        QuestionService questionService = new QuestionService();

        // A new session is created on each round so questions are fetched and shuffled again.
        boolean playAgain = true;
        while (playAgain) {
            try {
                String responseBody = client.fetchCountries(COUNTRIES_PER_REQUEST);
                List<Country> countries = parser.parse(responseBody);
                List<Question> questions = questionService
                        .createCapitalQuestions(countries, QUESTIONS_PER_ROUND);
                GameSession session = new GameSession(questions);

                playRound(ui, session);
                playAgain = ui.askToPlayAgain();
            } catch (IOException exception) {
                ui.showError("Could not reach the REST Countries API. Please try again later.");
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                ui.showError("The request was interrupted.");
                return;
            } catch (IllegalArgumentException | IllegalStateException exception) {
                ui.showError(exception.getMessage());
                return;
            }
        }

        ui.showGoodbye();
    }

    private static void playRound(ConsoleUi ui, GameSession session) {
        while (session.hasNextQuestion()) {
            Question question = session.getCurrentQuestion();
            String answer = ui.askQuestion(question);

            if (question.isCorrectAnswer(answer)) {
                session.registerCorrectAnswer();
                ui.showCorrectAnswer(session.getScore());
                continue;
            }

            ui.showIncorrectAnswer(question);
            break;
        }

        ui.showRoundSummary(session.getScore(), session.getTotalQuestions());
    }
}
