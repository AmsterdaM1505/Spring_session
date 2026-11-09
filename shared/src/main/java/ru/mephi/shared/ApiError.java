package ru.mephi.shared;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String message, String traceId) {
    public static ApiError of(int status, String code, String message, String traceId) {
        return new ApiError(Instant.now(), status, code, message, traceId);
    }
}
