package com.eventhub.controller.dto;

import jakarta.validation.constraints.NotNull;

public record RegisterParticipantRequest(
        @NotNull(message = "Participant ID is required")
        Long participantId
) {
}
