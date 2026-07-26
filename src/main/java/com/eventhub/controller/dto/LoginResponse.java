package com.eventhub.controller.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn
) {
}
