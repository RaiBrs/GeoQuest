package com.raibrs.geoquest.model;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Question {

    private static final int ALTERNATIVE_COUNT = 4;

    private final String text;
    private final String correctAnswer;
    private final List<String> alternatives;

    public Question(String text, String correctAnswer, List<String> alternatives) {
        // A question must always be displayable and answerable by the game.
        this.text = requireNonBlank(text, "Question text");
        this.correctAnswer = requireNonBlank(correctAnswer, "Correct answer");
        this.alternatives = normalizeAlternatives(alternatives);

        // Multiple-choice input is always mapped from A-D, so the model requires four choices.
        if (this.alternatives.size() != ALTERNATIVE_COUNT) {
            throw new IllegalArgumentException("A question must have exactly four alternatives.");
        }
        if (this.alternatives.stream().noneMatch(this.correctAnswer::equalsIgnoreCase)) {
            throw new IllegalArgumentException("Alternatives must contain the correct answer.");
        }
    }

    public String getText() {
        return text;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public List<String> getAlternatives() {
        return alternatives;
    }

    public boolean isCorrectAnswer(String answer) {
        return answer != null && correctAnswer.equalsIgnoreCase(answer.strip());
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }

        return value.strip();
    }

    private static List<String> normalizeAlternatives(List<String> alternatives) {
        if (alternatives == null) {
            throw new IllegalArgumentException("Alternatives must not be null.");
        }

        List<String> normalizedAlternatives = alternatives.stream()
                .map(alternative -> requireNonBlank(alternative, "Alternative"))
                .toList();
        Set<String> distinctAlternatives = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (String alternative : normalizedAlternatives) {
            if (!distinctAlternatives.add(alternative)) {
                throw new IllegalArgumentException("Alternatives must not repeat.");
            }
        }

        return normalizedAlternatives;
    }
}
