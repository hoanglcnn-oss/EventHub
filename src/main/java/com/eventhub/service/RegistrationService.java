package com.eventhub.service;

import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.controller.mapper.RegistrationMapper;
import com.eventhub.domain.*;
import com.eventhub.exception.*;
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
    private final com.eventhub.repository.UserAccountRepository userAccountRepository;
    private final RegistrationMapper registrationMapper;
    private final Clock clock;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            ParticipantRepository participantRepository,
            com.eventhub.repository.UserAccountRepository userAccountRepository,
            RegistrationMapper registrationMapper,
            Clock clock) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.userAccountRepository = userAccountRepository;
        this.registrationMapper = registrationMapper;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse register(Long eventId, RegisterParticipantRequest request) {
        // Kiểm tra quyền sở hữu nếu là PARTICIPANT
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_EVENT_ADMIN"));
            
            if (!isAdmin) {
                String currentEmail = authentication.getName();
                com.eventhub.domain.UserAccount currentAccount = userAccountRepository.findByEmail(currentEmail)
                        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
                
                if (currentAccount.getParticipant() == null || !currentAccount.getParticipant().getId().equals(request.participantId())) {
                    throw new org.springframework.security.access.AccessDeniedException("A participant can only register for themselves");
                }
            }
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));

        Participant participant = participantRepository.findById(request.participantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found with id: " + request.participantId()));

        // Kiểm tra các quy tắc nghiệp vụ (Task 3 & 4 rules)
        if (event.getStatus() != EventStatus.OPEN) {
            throw new InvalidEventStateException("Event is not open for registration. Current status: " + event.getStatus());
        }

        if (event.getStartAt().isBefore(LocalDateTime.now(clock).plusHours(0))) {
            throw new InvalidEventStateException("Cannot register for an event that has already started or passed.");
        }

        if (event.getAvailableSeats() <= 0) {
            throw new EventFullCapacityException(eventId);
        }

        if (registrationRepository.existsByEventIdAndParticipantIdAndStatus(eventId, request.participantId(), RegistrationStatus.ACTIVE)) {
            throw new DuplicateRegistrationException(eventId, request.participantId());
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

        // Kiểm tra quyền sở hữu nếu là PARTICIPANT
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_EVENT_ADMIN"));
            
            if (!isAdmin) {
                String currentEmail = authentication.getName();
                com.eventhub.domain.UserAccount currentAccount = userAccountRepository.findByEmail(currentEmail)
                        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
                
                if (currentAccount.getParticipant() == null || 
                        !currentAccount.getParticipant().getId().equals(registration.getParticipant().getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("A participant can only cancel their own registration");
                }
            }
        }

        if (!registration.getEvent().getId().equals(eventId)) {
            throw new InvalidCancellationException("Registration does not belong to the specified event.");
        }

        if (registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new InvalidCancellationException("Only ACTIVE registrations can be cancelled.");
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
