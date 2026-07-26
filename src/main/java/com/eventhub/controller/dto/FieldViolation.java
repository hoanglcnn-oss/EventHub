package com.eventhub.controller.dto;

public record FieldViolation(
        String field,
        String code,
        String message
) {
}
