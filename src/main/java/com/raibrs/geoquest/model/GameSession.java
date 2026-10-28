package com.raibrs.geoquest.model;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class GameSession {

    private final List<Question> questions;
    private int currentQuestionIndex;
    private boolean finished;
    private int score;
    private final GameMode mode;
    private final Clock clock;
    private final Instant expiresAt;
    private boolean timedOut;

    public GameSession(List<Question> questions) {
        this(questions, GameMode.SURVIVAL, Clock.systemUTC());
    }

    public GameSession(List<Question> questions, GameMode mode, Clock clock) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("A game session needs at least one question.");
        }

        // Keep the session stable even if the caller later changes its original list.
        this.questions = List.copyOf(questions);
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
        return isFinished() ? null : questions.get(currentQuestionIndex);
    }

    public AnswerResult answer(String choice) {
        Question question = getCurrentQuestion();
        if (choice == null || !question.getAlternatives().contains(choice)) {
            throw new IllegalArgumentException("Invalid answer choice.");
        }
        boolean correct = question.isCorrectAnswer(choice);
        // One state transition owns both scoring and termination, independent of any UI.
        if (correct) score++;
        currentQuestionIndex++;
        finished = currentQuestionIndex == questions.size()
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
        return Math.min(currentQuestionIndex + 1, questions.size());
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public record AnswerResult(boolean correct, String correctAnswer) {}
}
