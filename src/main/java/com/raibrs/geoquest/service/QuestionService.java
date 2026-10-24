package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class QuestionService {

    private static final int ALTERNATIVES_PER_QUESTION = 4;

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
        if (distinctCapitals(countries).size() < questionCount * ALTERNATIVES_PER_QUESTION) {
            throw new IllegalArgumentException(
                    "At least four distinct capitals per question are required.");
        }

        // Shuffle a copy so creating a round never changes the caller's country list.
        List<Country> shuffledCountries = new ArrayList<>(countries);
        Collections.shuffle(shuffledCountries, random);
        List<Country> questionCountries = selectQuestionCountries(shuffledCountries, questionCount);
        List<String> unusedDistractors = createUnusedDistractors(countries, questionCountries);
        Collections.shuffle(unusedDistractors, random);
        List<Question> questions = new ArrayList<>();

        for (Country country : questionCountries) {
            questions.add(createCapitalQuestion(country, unusedDistractors));
        }

        return questions;
    }

    private List<Country> selectQuestionCountries(List<Country> countries, int questionCount) {
        List<Country> questionCountries = new ArrayList<>();
        Set<String> selectedCapitals = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Country country : countries) {
            if (selectedCapitals.add(country.getCapital())) {
                questionCountries.add(country);
            }
            if (questionCountries.size() == questionCount) {
                return questionCountries;
            }
        }

        throw new IllegalArgumentException("Question countries must have distinct capitals.");
    }

    private List<String> createUnusedDistractors(
            List<Country> countries,
            List<Country> questionCountries
    ) {
        List<String> unusedDistractors = new ArrayList<>();
        Set<String> usedCapitals = distinctCapitals(questionCountries);

        for (Country candidate : countries) {
            // Correct answers are excluded so each capital is displayed at most once per round.
            if (usedCapitals.add(candidate.getCapital())) {
                unusedDistractors.add(candidate.getCapital());
            }
        }

        return unusedDistractors;
    }

    private Question createCapitalQuestion(Country country, List<String> unusedDistractors) {
        String text = "What is the capital of " + country.getName() + "?";
        List<String> alternatives = new ArrayList<>();
        alternatives.add(country.getCapital());

        for (int index = 1; index < ALTERNATIVES_PER_QUESTION; index++) {
            alternatives.add(unusedDistractors.removeFirst());
        }

        // Each distractor is consumed once per round, preventing repeated incorrect alternatives.
        Collections.shuffle(alternatives, random);

        return new Question(text, country.getCapital(), alternatives);
    }

    private Set<String> distinctCapitals(List<Country> countries) {
        Set<String> capitals = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Country country : countries) {
            capitals.add(country.getCapital());
        }

        return capitals;
    }
}
