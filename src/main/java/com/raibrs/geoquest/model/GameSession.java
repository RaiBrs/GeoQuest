package com.raibrs.geoquest.model;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.ArrayList;
import java.util.function.Supplier;

public final class GameSession {

    private List<Question> questions;
    private final Supplier<List<Question>> nextQuestions;
    private int batchIndex;
    private int currentQuestionIndex;
    private boolean finished;
    private int score;
    private final GameMode mode;
    private final Clock clock;
    private final Instant expiresAt;
    private boolean timedOut;
    private final List<Mistake> mistakes = new ArrayList<>();

    public GameSession(List<Question> questions) {
        this(questions, GameMode.SURVIVAL, Clock.systemUTC());
    }

    public GameSession(List<Question> questions, GameMode mode, Clock clock) {
        this(questions, mode, clock, repeatQuestions(questions));
    }

    public GameSession(List<Question> questions, GameMode mode, Clock clock, Supplier<List<Question>> nextQuestions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("A game session needs at least one question.");
        }

        // Keep the session stable even if the caller later changes its original list.
        this.questions = List.copyOf(questions);
        this.nextQuestions = Objects.requireNonNull(nextQuestions);
        this.mode = Objects.requireNonNull(mode);
        this.clock = Objects.requireNonNull(clock);
        this.expiresAt = mode == GameMode.TIMED ? clock.instant().plusSeconds(60) : null;
    }

    public boolean hasNextQuestion() {
        return !isFinished();
    }

    public Question getCurrentQuestion() {
        Question question = getCurrentQuestionOrNull();
        if (question == null) {
            throw new IllegalStateException("There are no questions left in this session.");
        }
        return question;
    }

    public Question getCurrentQuestionOrNull() {
        if (isFinished()) return null;
        if (mode == GameMode.TIMED && batchIndex == questions.size()) {
            List<Question> next = List.copyOf(nextQuestions.get());
            if (next.isEmpty()) throw new IllegalStateException("A timed round needs more questions.");
            questions = next;
            batchIndex = 0;
            // Generation uses the existing deadline; a new batch never grants more time.
            if (isFinished()) return null;
        }
        return questions.get(batchIndex);
    }

    public AnswerResult answer(String choice) {
        Question question = getCurrentQuestion();
        if (choice == null || !question.getAlternatives().contains(choice)) {
            throw new IllegalArgumentException("Invalid answer choice.");
        }
        boolean correct = question.isCorrectAnswer(choice);
        if (!correct) {
            mistakes.add(new Mistake(currentQuestionIndex + 1, question.getText(), choice,
                    question.getCorrectAnswer()));
        }
        // One state transition owns both scoring and termination, independent of any UI.
        if (correct) score++;
        currentQuestionIndex++;
        batchIndex++;
        finished = (mode != GameMode.TIMED && currentQuestionIndex == questions.size())
                || (!correct && mode == GameMode.SURVIVAL);
        return new AnswerResult(correct, question.getCorrectAnswer());
    }

    public boolean isFinished() {
        if (!finished && expiresAt != null && !clock.instant().isBefore(expiresAt)) {
            finished = true;
            timedOut = true;
        }
        return finished;
    }

    public boolean isTimedOut() { return timedOut; }
    public GameMode getMode() { return mode; }
    public Long getExpiresAt() { return expiresAt == null ? null : expiresAt.toEpochMilli(); }

    public int getCurrentQuestionNumber() {
        return mode == GameMode.TIMED ? currentQuestionIndex + 1
                : Math.min(currentQuestionIndex + 1, questions.size());
    }

    public int getAnsweredQuestions() { return currentQuestionIndex; }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public List<Mistake> getMistakes() { return List.copyOf(mistakes); }

    /** Internal persistence state; never use this as an HTTP response (it contains answer keys). */
    public Snapshot snapshot() {
        isFinished();
        return new Snapshot(questions.stream().map(q -> new StoredQuestion(q.getText(),
                q.getCorrectAnswer(), q.getAlternatives())).toList(), batchIndex,
                currentQuestionIndex, finished, score, mode, getExpiresAt(), timedOut, getMistakes());
    }

    public static GameSession restore(Snapshot state, Clock clock, Supplier<List<Question>> nextQuestions) {
        return new GameSession(state, clock, nextQuestions);
    }

    private GameSession(Snapshot state, Clock clock, Supplier<List<Question>> nextQuestions) {
        this.questions = state.questions().stream()
                .map(q -> new Question(q.text(), q.correctAnswer(), q.alternatives())).toList();
        this.mode = Objects.requireNonNull(state.mode());
        this.clock = Objects.requireNonNull(clock);
        this.nextQuestions = Objects.requireNonNull(nextQuestions);
        this.expiresAt = state.expiresAt() == null ? null : Instant.ofEpochMilli(state.expiresAt());
        this.batchIndex = state.batchIndex();
        this.currentQuestionIndex = state.answered();
        this.finished = state.finished();
        this.score = state.score();
        this.timedOut = state.timedOut();
        this.mistakes.addAll(state.mistakes());
        if (questions.isEmpty() || batchIndex < 0 || batchIndex > questions.size()
                || currentQuestionIndex < 0 || score < 0 || score > currentQuestionIndex
                || (mode == GameMode.TIMED) != (expiresAt != null)) {
            throw new IllegalArgumentException("Invalid saved game session.");
        }
    }

    public record StoredQuestion(String text, String correctAnswer, List<String> alternatives) {
        public StoredQuestion { alternatives = List.copyOf(alternatives); }
    }

    public record Snapshot(List<StoredQuestion> questions, int batchIndex, int answered,
                           boolean finished, int score, GameMode mode, Long expiresAt,
                           boolean timedOut, List<Mistake> mistakes) {
        public Snapshot {
            questions = List.copyOf(questions);
            mistakes = List.copyOf(mistakes);
        }
    }

    private static Supplier<List<Question>> repeatQuestions(List<Question> questions) {
        List<Question> snapshot = questions == null ? List.of() : List.copyOf(questions);
        return () -> snapshot;
    }

    public record Mistake(int number, String text, String selectedAnswer, String correctAnswer) {}

    public record AnswerResult(boolean correct, String correctAnswer) {}
}
