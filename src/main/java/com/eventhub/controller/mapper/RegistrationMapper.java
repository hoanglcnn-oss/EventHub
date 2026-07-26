package com.eventhub.controller.mapper;

import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.domain.Registration;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMapper {

    public RegistrationResponse toResponse(Registration registration) {
        if (registration == null) {
            return null;
        }
        return new RegistrationResponse(
                registration.getId(),
                registration.getEvent().getId(),
                registration.getEvent().getTitle(),
                registration.getParticipant().getId(),
                registration.getParticipant().getFullName(),
                registration.getRegisteredAt(),
                registration.getCancelledAt(),
                registration.getStatus()
        );
    }
}
