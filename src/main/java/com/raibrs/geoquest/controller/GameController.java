package com.raibrs.geoquest.controller;

import com.raibrs.geoquest.account.AccountSessions;
import com.raibrs.geoquest.dto.GameDtos.*;
import com.raibrs.geoquest.model.*;
import com.raibrs.geoquest.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.security.Principal;
import java.util.UUID;

@RestController
public final class GameController {
    private final GameService games;
    private final AccountSessions accounts;
    public GameController(GameService games, AccountSessions accounts) { this.games = games; this.accounts = accounts; }
    @PostMapping(value = "/api/games", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public GameView start(@RequestBody StartRequest request, Principal principal) throws IOException, InterruptedException {
        return games.start(request.mode() == null ? GameMode.SURVIVAL : request.mode(),
                request.questionType() == null ? QuestionType.CAPITAL : request.questionType(),
                request.language() == null ? GameLanguage.EN : request.language(), owner(principal));
    }
    @PostMapping(value = "/api/answers", consumes = "application/json")
    public AnswerView answer(@RequestBody AnswerRequest request, Principal principal) throws IOException, InterruptedException {
        var user = accounts.user(principal);
        var answer = games.answer(request, user == null ? null : user.id());
        accounts.save(user, answer.state(), games.questionType(request.gameId()));
        return answer;
    }
    @PostMapping(value = "/api/games/status", consumes = "application/json")
    public GameView status(@RequestBody StatusRequest request, Principal principal) throws IOException, InterruptedException {
        var user = accounts.user(principal);
        var state = games.status(request.gameId(), user == null ? null : user.id());
        accounts.save(user, state, games.questionType(state.gameId()));
        return state;
    }
    private UUID owner(Principal principal) { var user = accounts.user(principal); return user == null ? null : user.id(); }
}
