package com.raibrs.geoquest.service;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.AnswerView;
import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.dto.GameDtos.QuestionView;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.RankingEntry;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/** Coordinates local rounds; scoring remains in the Java domain model. */
public final class GameService {
    private final CountriesClient countriesClient;
    private final QuestionService questionService;
    private final RankingService rankingService;
    private final Map<UUID, Round> rounds = new HashMap<>();
    private List<Country> countryCatalog;

    public GameService(CountriesClient countriesClient, QuestionService questionService,
                       RankingService rankingService) {
        this.countriesClient = Objects.requireNonNull(countriesClient);
        this.questionService = Objects.requireNonNull(questionService);
        this.rankingService = Objects.requireNonNull(rankingService);
    }

    public synchronized GameView start(String playerName) throws IOException, InterruptedException {
        String normalizedName = RankingEntry.normalizePlayerName(playerName);
        if (countryCatalog == null) {
            countryCatalog = countriesClient.fetchAllCountries(80);
        }
        List<Question> questions;
        try {
            questions = questionService.createCapitalQuestions(countryCatalog, 10);
        } catch (IllegalArgumentException exception) {
            countryCatalog = null;
            throw new IOException("Country catalog cannot produce a round.", exception);
        }
        Round round = new Round(normalizedName, new GameSession(questions));
        rounds.put(round.id, round);
        return view(round);
    }

    public synchronized AnswerView answer(AnswerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Informe a resposta.");
        }
        Round round = findRound(request.gameId());
        // A network retry returns the original response without scoring twice.
        if (request.equals(round.lastRequest)) {
            return round.lastAnswer;
        }
        if (round.session.isFinished()) {
            throw new IllegalStateException("A rodada já terminou.");
        }
        if (request.number() != round.session.getCurrentQuestionNumber()) {
            throw new IllegalStateException("A pergunta não é a atual.");
        }
        GameSession.AnswerResult result = round.session.answer(request.choice());
        if (round.session.isFinished()) {
            round.result = rankingService.createResult(round.playerName,
                    round.session.getScore(), round.session.getTotalQuestions());
        }
        round.lastRequest = request;
        round.lastAnswer = new AnswerView(result.correct(), result.correctAnswer(), view(round));
        return round.lastAnswer;
    }

    public synchronized void saveResult(UUID gameId) throws SQLException {
        Round round = findRound(gameId);
        if (!round.session.isFinished()) {
            throw new IllegalStateException("A rodada ainda não terminou.");
        }
        if (!round.saved) {
            // Keep the same result and timestamp if persistence needs a retry.
            rankingService.saveResult(round.result);
            round.saved = true;
        }
    }

    private Round findRound(UUID gameId) {
        Round round = rounds.get(gameId);
        if (round == null) {
            throw new NoSuchElementException("Rodada não encontrada. Inicie uma nova partida.");
        }
        return round;
    }

    private GameView view(Round round) {
        QuestionView question = null;
        if (!round.session.isFinished()) {
            Question current = round.session.getCurrentQuestion();
            // Never serialize the domain Question, which contains the answer key.
            question = new QuestionView(current.getText(), current.getAlternatives());
        }
        int total = round.session.getTotalQuestions();
        int score = round.session.getScore();
        return new GameView(round.id, round.session.getCurrentQuestionNumber(), total,
                score, round.session.isFinished(), question);
    }

    private static final class Round {
        private final UUID id = UUID.randomUUID();
        private final String playerName;
        private final GameSession session;
        private boolean saved;
        private RankingEntry result;
        private AnswerRequest lastRequest;
        private AnswerView lastAnswer;

        private Round(String playerName, GameSession session) {
            this.playerName = playerName;
            this.session = session;
        }
    }
}
