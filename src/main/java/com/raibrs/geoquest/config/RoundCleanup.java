package com.raibrs.geoquest.config;
import com.raibrs.geoquest.service.GameService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class RoundCleanup {
    private final GameService games;
    public RoundCleanup(GameService games) { this.games = games; }
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void cleanup() { games.cleanupExpiredRounds(); }
}
