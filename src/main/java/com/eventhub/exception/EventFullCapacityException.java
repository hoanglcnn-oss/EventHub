package com.eventhub.exception;

public class EventFullCapacityException extends ConflictException {
    public EventFullCapacityException(Long eventId) {
        super("Event with id %d is full capacity. No seats available.".formatted(eventId));
    }
}
