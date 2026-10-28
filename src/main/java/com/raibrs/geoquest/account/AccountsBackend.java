package com.raibrs.geoquest.account;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.raibrs.geoquest.dto.GameDtos.GameView;
import com.raibrs.geoquest.model.GameMode;
import com.raibrs.geoquest.model.QuestionType;

public interface AccountsBackend {
    record User(UUID id, String displayName, Instant authenticatedAt, String email,
                String provider, String providerId, String avatarUrl) implements java.security.Principal {
        public User(UUID id, String displayName, Instant authenticatedAt) { this(id, displayName, authenticatedAt, null, null, null, null); }
        @Override public String getName() { return id.toString(); }
        @Override public String toString() { return "User[id=" + id + "]"; }
    }
    record Leader(String displayName, int score, int answered) {}
    User login(SocialIdentity identity) throws IOException;
    void createSession(User user, String tokenHash) throws IOException;
    User findSession(String tokenHash) throws IOException;
    void logout(String tokenHash) throws IOException;
    void delete(User user) throws IOException;
    void saveResult(User user, GameView state, QuestionType type) throws IOException;
    List<Leader> leaderboard(GameMode mode, QuestionType type) throws IOException;
}
