package com.eventhub.controller.dto;

import com.eventhub.domain.EventStatus;
import java.time.LocalDateTime;

public record EventSummaryResponse(
        Long id,
        String title,
        String location,
        LocalDateTime startAt,
        int availableSeats,
        EventStatus status
) {
}
