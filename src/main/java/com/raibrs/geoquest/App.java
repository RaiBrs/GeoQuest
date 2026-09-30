package com.raibrs.geoquest;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.api.CountryJsonParser;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;
import com.raibrs.geoquest.service.QuestionService;
import com.raibrs.geoquest.service.RankingService;
import com.raibrs.geoquest.ui.ConsoleUi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class App {

    private static final int QUESTIONS_PER_ROUND = 5;
    private static final int COUNTRIES_PER_REQUEST = 100;
    // A relative path keeps the ranking beside the application when it is run locally.
    private static final Path RANKING_FILE = Path.of("data", "ranking.json");

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
        // One shared service preserves the ranking across every round of this game session.
        RankingService rankingService = new RankingService(new RankingRepository(RANKING_FILE));

        // The selected name remains in memory until the player explicitly chooses logout.
        String playerName = ui.askPlayerName();
        boolean isRunning = true;

        while (isRunning) {
            try {
                switch (ui.askMainMenuOption()) {
                    case PLAY -> playAndSaveRound(
                            ui, client, parser, questionService, rankingService, playerName);
                    case VIEW_RANKING -> ui.showRanking(rankingService.getTopRanking());
                    // Changing identity is explicit, so menu actions reuse the current player name.
                    case LOGOUT -> playerName = ui.askPlayerName();
                    case EXIT -> isRunning = false;
                }
            } catch (IOException exception) {
                // Local storage failures must not be reported as API connection failures.
                ui.showError("Could not read or save the local ranking.");
                return;
            } catch (IllegalStateException exception) {
                ui.showError(exception.getMessage());
                return;
            }
        }

        ui.showGoodbye();
    }

    private static void playAndSaveRound(
            ConsoleUi ui,
            CountriesClient client,
            CountryJsonParser parser,
            QuestionService questionService,
            RankingService rankingService,
            String playerName
    ) throws IOException {
        GameSession session;

        try {
            // A new session fetches and shuffles questions without changing the logged-in player.
            String responseBody = client.fetchCountries(COUNTRIES_PER_REQUEST);
            List<Country> countries = parser.parse(responseBody);
            List<Question> questions = questionService
                    .createCapitalQuestions(countries, QUESTIONS_PER_ROUND);
            session = new GameSession(questions);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not reach the REST Countries API. Please try again later.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The request was interrupted.", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }

        playRound(ui, session);
        List<RankingEntry> ranking = rankingService.registerResult(
                playerName,
                session.getScore(),
                session.getTotalQuestions());
        ui.showRanking(ranking);
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
            // A round ends on the first incorrect answer; this is the game's current scoring rule.
            break;
        }

        ui.showRoundSummary(session.getScore(), session.getTotalQuestions());
    }
}
