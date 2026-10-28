package com.raibrs.geoquest.service;
import com.raibrs.geoquest.support.TestCountriesClient;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.dto.GameDtos.AnswerRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RoundOwnershipTest {
    @Test void anotherAccountOrGuestCannotAccessAnAuthenticatedRound() throws Exception {
        var games = new GameService(new TestCountriesClient(), new QuestionService());
        var owner = UUID.randomUUID();
        var state = games.start(GameMode.CAPITALS, QuestionType.CAPITAL, GameLanguage.EN, owner);
        assertThrows(NoSuchElementException.class, () -> games.status(state.gameId()));
        assertThrows(NoSuchElementException.class, () -> games.status(state.gameId(), UUID.randomUUID()));
        var request = new AnswerRequest(state.gameId(), 1, TestCountriesClient.answerFor(state.question().text()));
        assertThrows(NoSuchElementException.class, () -> games.answer(request));
        assertEquals(1, games.answer(request, owner).state().score());
        games.removePlayerRounds(owner);
        assertThrows(NoSuchElementException.class, () -> games.status(state.gameId(), owner));
    }
}
