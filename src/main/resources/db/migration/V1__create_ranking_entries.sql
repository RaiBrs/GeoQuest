CREATE TABLE ranking_entries (
    player_key TEXT COLLATE "C" PRIMARY KEY CHECK (player_key <> ''),
    player_name TEXT NOT NULL CHECK (btrim(player_name) <> ''),
    score INTEGER NOT NULL CHECK (score >= 0),
    total_questions INTEGER NOT NULL CHECK (total_questions >= 1),
    played_at_epoch_millis BIGINT NOT NULL CHECK (played_at_epoch_millis > 0),
    CONSTRAINT ranking_score_within_total CHECK (score <= total_questions)
);

CREATE INDEX ranking_entries_order_idx
    ON ranking_entries (score DESC, played_at_epoch_millis DESC, player_key ASC);
