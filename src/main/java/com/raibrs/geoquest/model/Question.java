package com.raibrs.geoquest.model;

public class Question {

    private final String text;
    private final String correctAnswer;

    public Question(String text, String correctAnswer) {
        // A question must always be displayable and answerable by the game.
        this.text = requireNonBlank(text, "Question text");
        this.correctAnswer = requireNonBlank(correctAnswer, "Correct answer");
    }

    public String getText() {
        return text;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
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
}
