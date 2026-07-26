package com.eventhub.controller.dto;

import com.eventhub.domain.RegistrationStatus;
import java.time.Instant;

public record RegistrationResponse(
        Long id,
        Long eventId,
        String eventTitle,
        Long participantId,
        String participantName,
        Instant registeredAt,
        Instant cancelledAt,
        RegistrationStatus status
) {
}
