package com.raibrs.geoquest.model;

public class Question {

    private final String text;
    private final String correctAnswer;

    public Question(String text, String correctAnswer) {
        this.text = text;
        this.correctAnswer = correctAnswer;
    }

    public String getText() {
        return text;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public boolean isCorrectAnswer(String answer) {
        return answer != null && correctAnswer.equalsIgnoreCase(answer.trim());
    }
}
