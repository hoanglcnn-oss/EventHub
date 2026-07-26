package com.eventhub.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RegisterParticipantRequest(
        @NotNull(message = "Participant ID is required")
        @Positive(message = "Participant ID must be positive")
        Long participantId
) {
}
