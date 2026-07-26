package com.eventhub.service;

import com.eventhub.controller.dto.CreateParticipantRequest;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ParticipantService {

    public ParticipantResponse create(CreateParticipantRequest request) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public ParticipantResponse findById(Long id) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public PageResponse<ParticipantResponse> findAll(Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }
}
