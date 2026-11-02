package com.raibrs.geoquest.service;

public final class RoundStorageUnavailable extends RuntimeException {
    public RoundStorageUnavailable() { super("Round storage is unavailable."); }
}
