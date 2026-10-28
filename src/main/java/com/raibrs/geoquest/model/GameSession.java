package com.raibrs.geoquest.model;

import java.util.List;

public final class GameSession {

    private final List<Question> questions;
    private int currentQuestionIndex;
    private boolean finished;

    public GameSession(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("A game session needs at least one question.");
        }

        // Keep the session stable even if the caller later changes its original list.
        this.questions = List.copyOf(questions);
    }

    public boolean hasNextQuestion() {
        return !finished;
    }

    public Question getCurrentQuestion() {
        if (!hasNextQuestion()) {
            throw new IllegalStateException("There are no questions left in this session.");
        }

        return questions.get(currentQuestionIndex);
    }

    public AnswerResult answer(String choice) {
        Question question = getCurrentQuestion();
        if (choice == null || !question.getAlternatives().contains(choice)) {
            throw new IllegalArgumentException("Invalid answer choice.");
        }
        boolean correct = question.isCorrectAnswer(choice);
        // One state transition owns both scoring and termination, independent of any UI.
        if (correct) {
            currentQuestionIndex++;
            finished = currentQuestionIndex == questions.size();
        } else {
            finished = true;
        }
        return new AnswerResult(correct, question.getCorrectAnswer());
    }

    public boolean isFinished() {
        return finished;
    }

    public int getCurrentQuestionNumber() {
        return Math.min(currentQuestionIndex + 1, questions.size());
    }

    public int getScore() {
        return currentQuestionIndex;
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public record AnswerResult(boolean correct, String correctAnswer) {}
}
