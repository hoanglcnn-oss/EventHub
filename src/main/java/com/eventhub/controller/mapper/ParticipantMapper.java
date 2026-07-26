package com.eventhub.controller.mapper;

import com.eventhub.controller.dto.CreateParticipantRequest;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.domain.Participant;
import org.springframework.stereotype.Component;

@Component
public class ParticipantMapper {

    public Participant toEntity(CreateParticipantRequest request) {
        if (request == null) {
            return null;
        }
        return Participant.builder()
                .fullName(request.fullName())
                .email(request.email())
                .build();
    }

    public ParticipantResponse toResponse(Participant participant) {
        if (participant == null) {
            return null;
        }
        return new ParticipantResponse(
                participant.getId(),
                participant.getFullName(),
                participant.getEmail(),
                participant.getCreatedAt()
        );
    }
}
