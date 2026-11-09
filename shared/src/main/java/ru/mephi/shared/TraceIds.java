package ru.mephi.shared;

import java.util.UUID;

public final class TraceIds {
    public static final String HEADER = "X-Request-Id";
    private TraceIds() {
    }
    public static String normalize(String candidate) {
        try {
            return UUID.fromString(candidate).toString();
        }
        catch (IllegalArgumentException | NullPointerException ignored) {
            return UUID.randomUUID().toString();
        }
    }
}
