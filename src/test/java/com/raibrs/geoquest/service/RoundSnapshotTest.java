package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.GameSession;
import com.raibrs.geoquest.model.QuestionType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoundSnapshotTest {
    @Test void readsTheStoredFormatFromBeforeTheRefactoring() {
        String legacy = """
                {"id":"00000000-0000-0000-0000-000000000001",
                 "session":{"questions":[{"text":"Capital?","correctAnswer":"B",
                   "alternatives":["A","B","C","D"]}],"batchIndex":0,"answered":0,
                   "finished":false,"score":0,"mode":"CAPITALS","expiresAt":null,
                   "timedOut":false,"mistakes":[]},
                 "owner":null,"type":"CAPITAL","language":"EN",
                 "lastRequest":null,"lastAnswer":null,"lastActivity":1767225600000,"finishedAt":null}
                """;
        var stored = RoundSnapshot.fromBytes(legacy.getBytes(StandardCharsets.UTF_8));
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), stored.id());
        assertEquals(QuestionType.CAPITAL, stored.type());
        var session = GameSession.restore(stored.session(), Clock.systemUTC(), List::of);
        assertTrue(session.answer("B").correct());
        assertEquals(1, session.getScore());
        assertEquals(stored, RoundSnapshot.fromBytes(stored.toBytes()));
    }

    @Test void invalidStoredJsonUsesTheStorageErrorContract() {
        assertThrows(RoundStorageUnavailable.class,
                () -> RoundSnapshot.fromBytes("invalid JSON".getBytes(StandardCharsets.UTF_8)));
    }
}
