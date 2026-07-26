package com.eventhub.service;

import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.controller.mapper.RegistrationMapper;
import com.eventhub.domain.Event;
import com.eventhub.domain.Participant;
import com.eventhub.domain.Registration;
import com.eventhub.domain.RegistrationStatus;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.ParticipantRepository;
import com.eventhub.repository.RegistrationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final RegistrationMapper registrationMapper;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            ParticipantRepository participantRepository,
            RegistrationMapper registrationMapper,
            Clock clock) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.registrationMapper = registrationMapper;
        this.clock = clock;
    }

    public RegistrationResponse register(Long eventId, RegisterParticipantRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        Participant participant = participantRepository.findById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found with id: " + request.participantId()));

        Registration registration = Registration.builder()
                .event(event)
                .participant(participant)
                .registeredAt(Instant.now(clock))
                .status(RegistrationStatus.ACTIVE)
                .build();

        Registration saved = registrationRepository.save(registration);
        return registrationMapper.toResponse(saved);
    }

    public PageResponse<RegistrationResponse> findByEventId(Long eventId, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        Page<Registration> page = registrationRepository.findByEventId(eventId, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(registrationMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public void cancel(Long eventId, Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + registrationId));

        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Instant.now(clock));
        registrationRepository.save(registration);
    }
}
