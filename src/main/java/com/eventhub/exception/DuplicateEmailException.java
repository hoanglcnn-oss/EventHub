package com.eventhub.exception;

public class DuplicateEmailException extends ConflictException {
    public DuplicateEmailException(String email) {
        super("Email is already in use: " + email);
    }
}
