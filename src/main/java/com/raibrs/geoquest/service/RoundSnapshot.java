package com.raibrs.geoquest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import com.raibrs.geoquest.dto.GameDtos.AnswerView;
import com.raibrs.geoquest.model.GameLanguage;
import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.QuestionType;

import java.io.IOException;
import java.util.UUID;

/** Database-only state with answer keys and retry history. Never send it through the API. */
record RoundSnapshot(UUID id, GameSession.Snapshot session, UUID owner, QuestionType type,
                     GameLanguage language, AnswerRequest lastRequest, AnswerView lastAnswer,
                     long lastActivity, Long finishedAt) {
    private static final ObjectMapper JSON = new ObjectMapper();

    // Field names preserve compatibility with rounds already saved.
    byte[] toBytes() {
        try { return JSON.writeValueAsBytes(this); }
        catch (IOException error) { throw new RoundStorageUnavailable(); }
    }

    static RoundSnapshot fromBytes(byte[] data) {
        try { return JSON.readValue(data, RoundSnapshot.class); }
        catch (IOException | IllegalArgumentException error) { throw new RoundStorageUnavailable(); }
    }
}
