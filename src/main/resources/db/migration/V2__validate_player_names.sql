ALTER TABLE ranking_entries
    ADD CONSTRAINT ranking_player_name_length
        CHECK (char_length(player_name) <= 80),
    ADD CONSTRAINT ranking_player_key_length
        CHECK (char_length(player_key) <= 80),
    ADD CONSTRAINT ranking_player_name_no_controls
        CHECK (player_name !~ U&'[\0001-\001F\007F-\009F]'),
    ADD CONSTRAINT ranking_player_key_no_controls
        CHECK (player_key !~ U&'[\0001-\001F\007F-\009F]');
