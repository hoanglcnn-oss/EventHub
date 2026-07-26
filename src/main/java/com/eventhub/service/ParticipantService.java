package com.eventhub.service;

import com.eventhub.controller.dto.CreateParticipantRequest;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.controller.mapper.ParticipantMapper;
import com.eventhub.domain.Participant;
import com.eventhub.exception.ConflictException;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.ParticipantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final ParticipantMapper participantMapper;
    private final Clock clock;

    public ParticipantService(
            ParticipantRepository participantRepository,
            ParticipantMapper participantMapper,
            Clock clock) {
        this.participantRepository = participantRepository;
        this.participantMapper = participantMapper;
        this.clock = clock;
    }

    @Transactional
    public ParticipantResponse create(CreateParticipantRequest request) {
        if (participantRepository.findByEmail(request.email()).isPresent()) {
            throw new ConflictException("Email is already in use: " + request.email());
        }

        Participant participant = participantMapper.toEntity(request);
        participant.setCreatedAt(Instant.now(clock));
        
        Participant saved = participantRepository.save(participant);
        return participantMapper.toResponse(saved);
    }

    public ParticipantResponse findById(Long id) {
        Participant participant = participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found with id: " + id));
        return participantMapper.toResponse(participant);
    }

    public PageResponse<ParticipantResponse> findAll(Pageable pageable) {
        Page<Participant> page = participantRepository.findAll(pageable);
        return new PageResponse<>(
                page.getContent().stream().map(participantMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
