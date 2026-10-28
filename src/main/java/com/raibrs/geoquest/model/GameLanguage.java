package com.raibrs.geoquest.model;

public enum GameLanguage {
    EN, PT_BR;

    public String prompt(QuestionType type, String subject) {
        if (type == QuestionType.COUNTRY) {
            return this == PT_BR ? "De qual país " + subject + " é a capital?"
                    : "Which country has " + subject + " as its capital?";
        }
        return this == PT_BR ? "Qual é a capital de " + subject + "?"
                : "What is the capital of " + subject + "?";
    }
}
