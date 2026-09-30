package com.raibrs.geoquest.service;

import com.raibrs.geoquest.model.RankingEntry;
import com.raibrs.geoquest.repository.RankingRepository;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RankingService {

    private static final int MAX_DISPLAYED_ENTRIES = 10;
    // The comparator defines the complete business order: higher score first, then newer result.
    private static final Comparator<RankingEntry> RANKING_ORDER = Comparator
            .comparingInt(RankingEntry::getScore)
            .reversed()
            .thenComparing(Comparator.comparingLong(RankingEntry::getPlayedAtEpochMillis).reversed());

    private final RankingRepository repository;
    private final Clock clock;

    public RankingService(RankingRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public RankingService(RankingRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    public List<RankingEntry> registerResult(String playerName, int score, int totalQuestions)
            throws IOException {
        RankingEntry newEntry = new RankingEntry(
                playerName,
                score,
                totalQuestions,
                clock.millis());
        List<RankingEntry> entries = new ArrayList<>(repository.load());
        RankingEntry bestEntry = newEntry;

        // Iterate backwards so removing old entries never shifts an unvisited index.
        for (int index = entries.size() - 1; index >= 0; index--) {
            RankingEntry existingEntry = entries.get(index);

            if (existingEntry.getPlayerName().equalsIgnoreCase(newEntry.getPlayerName())) {
                // Keep the highest score; for a tie, RANKING_ORDER keeps the most recent round.
                if (RANKING_ORDER.compare(existingEntry, bestEntry) < 0) {
                    bestEntry = existingEntry;
                }
                entries.remove(index);
            }
        }

        entries.add(bestEntry);
        entries.sort(RANKING_ORDER);

        // Store one best result per player and return only the entries the terminal should display.
        repository.save(entries);
        return limitForDisplay(entries);
    }

    public List<RankingEntry> getTopRanking() throws IOException {
        List<RankingEntry> entries = new ArrayList<>(repository.load());
        entries.sort(RANKING_ORDER);

        // Viewing the ranking must not rewrite the file; only a completed round changes it.
        return limitForDisplay(entries);
    }

    private List<RankingEntry> limitForDisplay(List<RankingEntry> entries) {
        return entries.stream().limit(MAX_DISPLAYED_ENTRIES).toList();
    }
}
