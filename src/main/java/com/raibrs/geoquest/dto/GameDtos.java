package com.raibrs.geoquest.dto;

import java.util.List;
import java.util.UUID;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.QuestionType;
import com.raibrs.geoquest.model.GameLanguage;

/** Plain API data, kept separate from HTTP handling and domain state. */
public final class GameDtos {
    private GameDtos() {}

    // Display names belong to browser greetings and are never part of API requests.
    public record StartRequest(GameMode mode, QuestionType questionType, GameLanguage language) {}
    public record StatusRequest(UUID gameId) {}
    public record AnswerRequest(UUID gameId, int number, String choice) {}
    public record QuestionView(String text, List<String> alternatives) {}
    public record MistakeView(int number, String text, String selectedAnswer, String correctAnswer) {}
    public record GameView(UUID gameId, int number, Integer total, int score,
                           boolean finished, QuestionView question,
                           GameMode mode, Long expiresAt, boolean timedOut, List<MistakeView> mistakes, int answered) {}
    public record AnswerView(boolean correct, String correctAnswer, GameView state) {}
}
