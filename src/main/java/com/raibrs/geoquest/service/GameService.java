package com.raibrs.geoquest.service;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.AnswerView;
import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.dto.GameDtos.QuestionView;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.Question;

import java.io.IOException;
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
    private final Map<UUID, Round> rounds = new HashMap<>();
    private List<Country> countryCatalog;

    public GameService(CountriesClient countriesClient, QuestionService questionService) {
        this.countriesClient = Objects.requireNonNull(countriesClient);
        this.questionService = Objects.requireNonNull(questionService);
    }

    public synchronized GameView start() throws IOException, InterruptedException {
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
        Round round = new Round(new GameSession(questions));
        rounds.put(round.id, round);
        return view(round);
    }

    public synchronized AnswerView answer(AnswerRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Provide an answer.");
        }
        Round round = findRound(request.gameId());
        // A network retry returns the original response without scoring twice.
        if (request.equals(round.lastRequest)) {
            return round.lastAnswer;
        }
        if (round.session.isFinished()) {
            throw new IllegalStateException("This round has already ended.");
        }
        if (request.number() != round.session.getCurrentQuestionNumber()) {
            throw new IllegalStateException("This is not the current question.");
        }
        GameSession.AnswerResult result = round.session.answer(request.choice());
        round.lastRequest = request;
        round.lastAnswer = new AnswerView(result.correct(), result.correctAnswer(), view(round));
        return round.lastAnswer;
    }

    private Round findRound(UUID gameId) {
        Round round = rounds.get(gameId);
        if (round == null) {
            throw new NoSuchElementException("Round not found. Start a new game.");
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
        private final GameSession session;
        private AnswerRequest lastRequest;
        private AnswerView lastAnswer;

        private Round(GameSession session) {
            this.session = session;
        }
    }
}
