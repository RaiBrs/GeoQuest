package com.raibrs.geoquest.service;

import com.raibrs.geoquest.api.CountriesClient;
import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.AnswerView;
import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.dto.GameDtos.QuestionView;
import com.raibrs.geoquest.dto.GameDtos.MistakeView;
import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.QuestionType;
import com.raibrs.geoquest.model.GameLanguage;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates local rounds; scoring remains in the Java domain model. */
public final class GameService {
    private final CountriesClient countriesClient;
    private final QuestionService questionService;
    private final Map<UUID, Round> rounds = new ConcurrentHashMap<>();
    private List<Country> countryCatalog;
    private final Object catalogLock = new Object();
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);
    private static final Duration FINISHED_TTL = Duration.ofMinutes(5);
    private final Clock clock;

    public GameService(CountriesClient countriesClient, QuestionService questionService) {
        this(countriesClient, questionService, Clock.systemUTC());
    }

    public GameService(CountriesClient countriesClient, QuestionService questionService, Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.countriesClient = Objects.requireNonNull(countriesClient);
        this.questionService = Objects.requireNonNull(questionService);
    }

    public GameView start() throws IOException, InterruptedException {
        return start(GameMode.SURVIVAL);
    }

    public GameView start(GameMode mode) throws IOException, InterruptedException {
        return start(mode, QuestionType.CAPITAL, GameLanguage.EN);
    }

    public GameView start(GameMode mode, QuestionType type, GameLanguage language)
            throws IOException, InterruptedException {
        return start(mode, type, language, null);
    }

    public GameView start(GameMode mode, QuestionType type, GameLanguage language, UUID owner)
            throws IOException, InterruptedException {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(type);
        Objects.requireNonNull(language);
        List<Country> catalog = catalog();
        List<Question> questions;
        try {
            questions = questionService.createQuestions(catalog, 10, type, language);
        } catch (IllegalArgumentException exception) {
            synchronized (catalogLock) { countryCatalog = null; }
            throw new IOException("Country catalog cannot produce a round.", exception);
        }
        Round round = new Round(new GameSession(questions, mode, clock,
                () -> questionService.createQuestions(catalog, 10, type, language)), clock.instant(), owner, type);
        synchronized (round) {
            rounds.put(round.id, round);
            return view(round);
        }
    }

    public AnswerView answer(AnswerRequest request) { return answer(request, null); }

    public AnswerView answer(AnswerRequest request, UUID owner) {
        if (request == null) {
            throw new IllegalArgumentException("Provide an answer.");
        }
        Round round = findRound(request.gameId());
        synchronized (round) {
            requireActive(round);
            requireOwner(round, owner);
            round.lastActivity = clock.instant();
            return answer(round, request);
        }
    }

    private AnswerView answer(Round round, AnswerRequest request) {
        // A network retry returns the original response without scoring twice.
        if (request.equals(round.lastRequest)) {
            return round.lastAnswer;
        }
        if (round.session.isFinished()) {
            if (round.session.isTimedOut()) return new AnswerView(false, "", view(round));
            throw new IllegalStateException("This round has already ended.");
        }
        if (request.number() != round.session.getCurrentQuestionNumber()) {
            throw new IllegalStateException("This is not the current question.");
        }
        GameSession.AnswerResult result;
        try {
            result = round.session.answer(request.choice());
        } catch (IllegalStateException exception) {
            // The deadline may pass between the checks above and accepting this answer.
            if (round.session.isTimedOut()) return new AnswerView(false, "", view(round));
            throw exception;
        }
        round.lastRequest = request;
        round.lastAnswer = new AnswerView(result.correct(), result.correctAnswer(), view(round));
        return round.lastAnswer;
    }

    private Round findRound(UUID gameId) {
        Round round = gameId == null ? null : rounds.get(gameId);
        if (round == null) {
            throw new NoSuchElementException("Round not found. Start a new game.");
        }
        return round;
    }

    public GameView status(UUID gameId) { return status(gameId, null); }

    public GameView status(UUID gameId, UUID owner) {
        Round round = findRound(gameId);
        synchronized (round) {
            requireActive(round);
            requireOwner(round, owner);
            round.lastActivity = clock.instant();
            return view(round);
        }
    }

    public QuestionType questionType(UUID gameId) { return findRound(gameId).type; }

    private void requireOwner(Round round, UUID owner) {
        if (!Objects.equals(round.owner, owner)) throw new NoSuchElementException("Round not found. Start a new game.");
    }

    private GameView view(Round round) {
        QuestionView question = null;
        Question current = round.session.getCurrentQuestionOrNull();
        if (current != null) {
            // Never serialize the domain Question, which contains the answer key.
            question = new QuestionView(current.getText(), current.getAlternatives());
        }
        Integer total = round.session.getMode() == GameMode.TIMED ? null : round.session.getTotalQuestions();
        int score = round.session.getScore();
        boolean finished = round.session.isFinished();
        boolean timedOut = round.session.isTimedOut();
        if (finished && round.finishedAt == null) round.finishedAt = clock.instant();
        List<MistakeView> mistakes = finished ? round.session.getMistakes().stream()
                .map(m -> new MistakeView(m.number(), m.text(), m.selectedAnswer(), m.correctAnswer()))
                .toList() : List.of();
        return new GameView(round.id, round.session.getCurrentQuestionNumber(), total,
                score, finished, finished ? null : question, round.session.getMode(),
                round.session.getExpiresAt(), timedOut, mistakes, round.session.getAnsweredQuestions());
    }

    private List<Country> catalog() throws IOException, InterruptedException {
        synchronized (catalogLock) {
            if (countryCatalog == null) countryCatalog = List.copyOf(countriesClient.fetchAllCountries(80));
            return countryCatalog;
        }
    }

    private boolean expired(Round round) {
        Instant now = clock.instant();
        if (round.session.isFinished() && round.finishedAt == null) {
            Long deadline = round.session.getExpiresAt();
            round.finishedAt = round.session.isTimedOut() && deadline != null
                    ? Instant.ofEpochMilli(deadline) : now;
        }
        Instant expiry = round.finishedAt == null ? round.lastActivity.plus(IDLE_TTL)
                : round.finishedAt.plus(FINISHED_TTL);
        return !now.isBefore(expiry);
    }

    private void requireActive(Round round) {
        if (rounds.get(round.id) != round || expired(round)) {
            rounds.remove(round.id, round);
            throw new NoSuchElementException("Round not found. Start a new game.");
        }
    }

    public void removePlayerRounds(UUID owner) {
        rounds.forEach((id, round) -> {
            if (owner.equals(round.owner)) synchronized (round) { rounds.remove(id, round); }
        });
    }

    public int cleanupExpiredRounds() {
        int removed = 0;
        for (Round round : rounds.values()) {
            synchronized (round) {
                if (expired(round) && rounds.remove(round.id, round)) removed++;
            }
        }
        return removed;
    }

    private static final class Round {
        private final UUID id = UUID.randomUUID();
        private final GameSession session;
        private final UUID owner;
        private final QuestionType type;
        private AnswerRequest lastRequest;
        private AnswerView lastAnswer;

        private Instant lastActivity;
        private Instant finishedAt;

        private Round(GameSession session, Instant now, UUID owner, QuestionType type) {
            this.session = session;
            this.owner = owner;
            this.type = type;
            this.lastActivity = now;
        }
    }
}
