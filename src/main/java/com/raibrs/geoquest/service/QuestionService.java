package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class QuestionService {

    private final Random random;

    public QuestionService() {
        this(new Random());
    }

    public QuestionService(Random random) {
        this.random = random;
    }

    public List<Question> createCapitalQuestions(List<Country> countries, int questionCount) {
        if (countries == null || countries.isEmpty()) {
            throw new IllegalArgumentException("At least one country is required to create questions.");
        }
        if (questionCount < 1 || questionCount > countries.size()) {
            throw new IllegalArgumentException("Question count must fit the available countries.");
        }

        // Shuffle a copy so creating a round never changes the caller's country list.
        List<Country> shuffledCountries = new ArrayList<>(countries);
        Collections.shuffle(shuffledCountries, random);
        List<Question> questions = new ArrayList<>();

        for (Country country : shuffledCountries.subList(0, questionCount)) {
            questions.add(createCapitalQuestion(country));
        }

        return questions;
    }

    private Question createCapitalQuestion(Country country) {
        String text = "What is the capital of " + country.getName() + "?";

        return new Question(text, country.getCapital());
    }
}
