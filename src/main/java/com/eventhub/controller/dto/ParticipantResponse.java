package com.eventhub.controller.dto;

import java.time.Instant;

public record ParticipantResponse(
        Long id,
        String fullName,
        String email,
        Instant createdAt
) {
}
