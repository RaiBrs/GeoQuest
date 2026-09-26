package com.raibrs.geoquest.model;

import java.util.List;

public class GameSession {

    private final List<Question> questions;
    private int currentQuestionIndex;
    private int score;

    public GameSession(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("A game session needs at least one question.");
        }

        // Keep the session stable even if the caller later changes its original list.
        this.questions = List.copyOf(questions);
    }

    public boolean hasNextQuestion() {
        return currentQuestionIndex < questions.size();
    }

    public Question getCurrentQuestion() {
        if (!hasNextQuestion()) {
            throw new IllegalStateException("There are no questions left in this session.");
        }

        return questions.get(currentQuestionIndex);
    }

    public void registerCorrectAnswer() {
        if (!hasNextQuestion()) {
            throw new IllegalStateException("There are no questions left in this session.");
        }

        score++;
        currentQuestionIndex++;
    }

    public int getScore() {
        return score;
    }

    public int getTotalQuestions() {
        return questions.size();
    }
}
