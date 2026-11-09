package ru.mephi.booking.integration;

public class RemoteFailure extends RuntimeException {
    public RemoteFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
