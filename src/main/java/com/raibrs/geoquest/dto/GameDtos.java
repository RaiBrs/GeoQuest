package com.raibrs.geoquest.dto;

import java.util.List;
import java.util.UUID;

/** Plain API data, kept separate from HTTP handling and domain state. */
public final class GameDtos {
    private GameDtos() {}

    // Display names belong to browser greetings and are never part of API requests.
    public record StartRequest() {}
    public record AnswerRequest(UUID gameId, int number, String choice) {}
    public record QuestionView(String text, List<String> alternatives) {}
    public record GameView(UUID gameId, int number, int total, int score,
                           boolean finished, QuestionView question) {}
    public record AnswerView(boolean correct, String correctAnswer, GameView state) {}
}
