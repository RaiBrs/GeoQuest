package com.raibrs.geoquest.account;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.QuestionType;

public interface AccountsBackend {
    record User(UUID id, String displayName, Instant authenticatedAt) {}
    record Leader(String displayName, int score, int answered) {}
    User exchange(String provider, String code, String verifier) throws IOException, InterruptedException;
    void createSession(User user, String tokenHash) throws IOException, InterruptedException;
    User findSession(String tokenHash) throws IOException, InterruptedException;
    void logout(String tokenHash) throws IOException, InterruptedException;
    void delete(User user) throws IOException, InterruptedException;
    void saveResult(User user, GameView state, QuestionType type) throws IOException, InterruptedException;
    List<Leader> leaderboard(GameMode mode, QuestionType type) throws IOException, InterruptedException;
}
