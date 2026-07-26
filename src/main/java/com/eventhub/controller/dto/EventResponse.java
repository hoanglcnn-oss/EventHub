package com.eventhub.controller.dto;

import com.eventhub.domain.EventStatus;
import java.time.Instant;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startAt,
        int capacity,
        int availableSeats,
        EventStatus status,
        Instant createdAt
) {
}
