package com.eventhub.service;

import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.controller.mapper.RegistrationMapper;
import com.eventhub.domain.*;
import com.eventhub.exception.ConflictException;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.ParticipantRepository;
import com.eventhub.repository.RegistrationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
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

    @Transactional
    public RegistrationResponse register(Long eventId, RegisterParticipantRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        Participant participant = participantRepository.findById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found with id: " + request.participantId()));

        // Kiểm tra các quy tắc nghiệp vụ (Task 3 rules)
        if (event.getStatus() != EventStatus.OPEN) {
            throw new ConflictException("Event is not open for registration. Current status: " + event.getStatus());
        }

        if (event.getStartAt().isBefore(LocalDateTime.now(clock).plusHours(0))) { // start time must be in future
            throw new ConflictException("Cannot register for an event that has already started or passed.");
        }

        if (event.getAvailableSeats() <= 0) {
            throw new ConflictException("Event is full capacity. No seats available.");
        }

        if (registrationRepository.existsByEventIdAndParticipantIdAndStatus(eventId, request.participantId(), RegistrationStatus.ACTIVE)) {
            throw new ConflictException("Participant is already registered for this event.");
        }

        // Tạo bản ghi đăng ký và giảm ghế trống
        Registration registration = Registration.builder()
                .event(event)
                .participant(participant)
                .registeredAt(Instant.now(clock))
                .status(RegistrationStatus.ACTIVE)
                .build();

        event.setAvailableSeats(event.getAvailableSeats() - 1);
        eventRepository.save(event);
        
        Registration saved = registrationRepository.save(registration);
        return registrationMapper.toResponse(saved);
    }

    public PageResponse<RegistrationResponse> findByEventId(Long eventId, Pageable pageable) {
        // Kiểm tra event tồn tại trước
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

    @Transactional
    public void cancel(Long eventId, Long registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id: " + registrationId));

        if (!registration.getEvent().getId().equals(eventId)) {
            throw new ConflictException("Registration does not belong to the specified event.");
        }

        if (registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new ConflictException("Only ACTIVE registrations can be cancelled.");
        }

        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Instant.now(clock));
        
        // Hoàn lại ghế trống cho sự kiện
        Event event = registration.getEvent();
        event.setAvailableSeats(event.getAvailableSeats() + 1);
        
        eventRepository.save(event);
        registrationRepository.save(registration);
    }
}
