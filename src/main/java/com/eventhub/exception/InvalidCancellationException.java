package com.eventhub.exception;

public class InvalidCancellationException extends ConflictException {
    public InvalidCancellationException(String message) {
        super(message);
    }
}
