package com.eventhub.service;

import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    public RegistrationResponse register(Long eventId, RegisterParticipantRequest request) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public PageResponse<RegistrationResponse> findByEventId(Long eventId, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public void cancel(Long eventId, Long registrationId) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }
}
