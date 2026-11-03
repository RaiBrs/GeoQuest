package com.raibrs.geoquest.model;

public enum GameMode {
    CAPITALS, SURVIVAL, TIMED;

    public boolean isRanked() { return this == TIMED; }
}
