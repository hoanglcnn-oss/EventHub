package com.eventhub.exception;

public class DuplicateRegistrationException extends ConflictException {
    public DuplicateRegistrationException(Long eventId, Long participantId) {
        super("Participant with id %d is already registered for event with id %d"
                .formatted(participantId, eventId));
    }
}
