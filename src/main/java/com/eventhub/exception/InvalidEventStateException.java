package com.eventhub.exception;

public class InvalidEventStateException extends ConflictException {
    public InvalidEventStateException(String message) {
        super(message);
    }
}
