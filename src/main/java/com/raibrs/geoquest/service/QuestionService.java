package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.Country;
import com.raibrs.geoquest.model.Question;
import com.raibrs.geoquest.model.QuestionType;
import com.raibrs.geoquest.model.GameLanguage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class QuestionService {
    private static final int ALTERNATIVES_PER_QUESTION = 4;
    private final Random random;

    public QuestionService() { this(new Random()); }
    public QuestionService(Random random) { this.random = Objects.requireNonNull(random); }

    public List<Question> createCapitalQuestions(List<Country> countries, int questionCount) {
        return createQuestions(countries, questionCount, QuestionType.CAPITAL, GameLanguage.EN);
    }

    public synchronized List<Question> createQuestions(List<Country> countries, int questionCount,
                                                       QuestionType type, GameLanguage language) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(language);
        if (countries == null || countries.isEmpty()) {
            throw new IllegalArgumentException("At least one country is required to create questions.");
        }
        if (questionCount < 1 || questionCount > countries.size()) {
            throw new IllegalArgumentException("Question count must fit the available countries.");
        }
        List<Candidate> candidates = candidates(countries, type, language);
        Set<String> allAnswers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        candidates.forEach(candidate -> allAnswers.add(candidate.answer()));
        if (allAnswers.size() < questionCount * ALTERNATIVES_PER_QUESTION) {
            throw new IllegalArgumentException("At least four distinct answers per question are required.");
        }

        // Work with copies; one displayed answer is used only once within the round.
        Collections.shuffle(candidates, random);
        List<Candidate> selected = new ArrayList<>();
        Set<String> used = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Candidate candidate : candidates) {
            if (used.add(candidate.answer())) selected.add(candidate);
            if (selected.size() == questionCount) break;
        }
        List<String> distractors = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (used.add(candidate.answer())) distractors.add(candidate.answer());
        }
        Collections.shuffle(distractors, random);
        List<Question> questions = new ArrayList<>();
        for (Candidate candidate : selected) {
            List<String> alternatives = new ArrayList<>();
            alternatives.add(candidate.answer());
            for (int i = 1; i < ALTERNATIVES_PER_QUESTION; i++) alternatives.add(distractors.removeLast());
            Collections.shuffle(alternatives, random);
            questions.add(new Question(language.prompt(type, candidate.subject()), candidate.answer(), alternatives));
        }
        return questions;
    }

    private List<Candidate> candidates(List<Country> countries, QuestionType type, GameLanguage language) {
        countries = countries.stream().filter(country -> country.supports(language)).toList();
        Map<String, Integer> capitalCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (type == QuestionType.COUNTRY) {
            countries.forEach(country -> capitalCounts.merge(country.getCapital(language), 1, Integer::sum));
        }
        List<Candidate> result = new ArrayList<>();
        for (Country country : countries) {
            if (type == QuestionType.CAPITAL) {
                result.add(new Candidate(country.getName(language), country.getCapital(language)));
            } else if (capitalCounts.get(country.getCapital(language)) == 1) {
                // Shared capitals would make a country question ambiguous.
                result.add(new Candidate(country.getCapital(language), country.getName(language)));
            }
        }
        return result;
    }

    private record Candidate(String subject, String answer) {}
}
