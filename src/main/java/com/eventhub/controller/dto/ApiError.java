package com.eventhub.controller.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldViolation> violations
) {
}
