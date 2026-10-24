package com.raibrs.geoquest;

import com.raibrs.geoquest.api.CountriesClient;
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
import java.util.NoSuchElementException;

public class App {

    private static final int QUESTIONS_PER_ROUND = 10;
    private static final int COUNTRIES_PER_REQUEST = 80;
    // Relative to the working directory from which the application is launched.
    private static final Path RANKING_FILE = Path.of("data", "ranking.json");
    private static final System.Logger LOGGER = System.getLogger(App.class.getName());

    private final ConsoleUi ui;
    private final CountriesClient client;
    private final QuestionService questionService;
    private final RankingService rankingService;
    private List<Country> countryCatalog;

    App(ConsoleUi ui, CountriesClient client, QuestionService questionService, RankingService rankingService) {
        this.ui = ui;
        this.client = client;
        this.questionService = questionService;
        this.rankingService = rankingService;
    }

    public static void main(String[] args) {
        ConsoleUi ui = new ConsoleUi();

        // Read the key from the environment so the secret never ends up in Git.
        String apiKey = System.getenv("API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            ui.showError("Set the API_KEY environment variable.");
            return;
        }

        try {
            CountriesClient client = new CountriesClient(apiKey);
            RankingService rankingService = new RankingService(new RankingRepository(RANKING_FILE));
            new App(ui, client, new QuestionService(), rankingService).run();
        } catch (IllegalArgumentException exception) {
            ui.showError(exception.getMessage());
        }
    }

    void run() {
        try {
            String playerName = ui.askPlayerName();
            boolean isRunning = true;

            while (isRunning) {
                try {
                    switch (ui.askMainMenuOption()) {
                        case PLAY -> playAndSaveRound(playerName);
                        case VIEW_RANKING -> ui.showRanking(rankingService.getTopRanking());
                        case DELETE_RANKING -> deleteRankingEntry(playerName);
                        case LOGOUT -> playerName = ui.askPlayerName();
                        case EXIT -> isRunning = false;
                    }
                } catch (IOException exception) {
                    LOGGER.log(System.Logger.Level.WARNING, "Local ranking operation failed.", exception);
                    ui.showError("Could not read or save the local ranking. Check the ranking file and permissions.");
                }
            }

            ui.showGoodbye();
        } catch (NoSuchElementException exception) {
            ui.showError(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ui.showError("The request was interrupted.");
        }
    }

    private void deleteRankingEntry(String playerName) throws IOException {
        if (!ui.askToConfirmRankingDeletion()) {
            ui.showRankingDeletionCancelled();
            return;
        }

        // The current session name is the only identity allowed to delete a ranking entry.
        if (rankingService.deleteResultFor(playerName)) {
            ui.showRankingEntryDeleted();
            return;
        }

        ui.showNoRankingEntryToDelete();
    }

    private void playAndSaveRound(String playerName) throws IOException, InterruptedException {
        GameSession session;

        try {
            // Publish the cached catalog only after every page has been loaded successfully.
            if (countryCatalog == null) {
                countryCatalog = client.fetchAllCountries(COUNTRIES_PER_REQUEST);
            }
            List<Question> questions = questionService
                    .createCapitalQuestions(countryCatalog, QUESTIONS_PER_ROUND);
            session = new GameSession(questions);
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Country catalog request failed.", exception);
            ui.showError("Could not load countries from the REST Countries API. Please try again later.");
            return;
        } catch (IllegalArgumentException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Country catalog cannot produce a round.", exception);
            countryCatalog = null;
            ui.showError("Country data cannot produce a round. Please try again later.");
            return;
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
