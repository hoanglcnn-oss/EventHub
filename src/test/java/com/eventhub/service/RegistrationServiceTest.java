package com.eventhub.service;

import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.controller.mapper.RegistrationMapper;
import com.eventhub.domain.*;
import com.eventhub.exception.DuplicateRegistrationException;
import com.eventhub.exception.EventFullCapacityException;
import com.eventhub.exception.InvalidCancellationException;
import com.eventhub.exception.InvalidEventStateException;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.ParticipantRepository;
import com.eventhub.repository.RegistrationRepository;
import com.eventhub.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    private RegistrationService registrationService;

    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private RegistrationMapper registrationMapper;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registrationService = new RegistrationService(
                registrationRepository,
                eventRepository,
                participantRepository,
                userAccountRepository,
                registrationMapper,
                clock
        );
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void register_Success() {
        // Given
        Long eventId = 1L;
        Long participantId = 2L;
        RegisterParticipantRequest request = new RegisterParticipantRequest(participantId);

        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now(clock).plusDays(1))
                .capacity(10)
                .availableSeats(10)
                .build();

        Participant participant = Participant.builder()
                .id(participantId)
                .email("test@email.com")
                .fullName("John Doe")
                .build();

        Registration registration = Registration.builder()
                .event(event)
                .participant(participant)
                .status(RegistrationStatus.ACTIVE)
                .registeredAt(Instant.now(clock))
                .build();

        Registration savedRegistration = Registration.builder()
                .id(100L)
                .event(event)
                .participant(participant)
                .status(RegistrationStatus.ACTIVE)
                .registeredAt(Instant.now(clock))
                .build();

        RegistrationResponse response = new RegistrationResponse(
                100L,
                eventId,
                "Java Concurrency",
                participantId,
                "John Doe",
                Instant.now(clock),
                null,
                RegistrationStatus.ACTIVE
        );

        // Security Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("test@email.com");
        UserAccount currentAccount = UserAccount.builder()
                .email("test@email.com")
                .role(UserRole.PARTICIPANT)
                .participant(participant)
                .build();
        when(userAccountRepository.findByEmail("test@email.com")).thenReturn(Optional.of(currentAccount));

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(registrationRepository.existsByEventIdAndParticipantIdAndStatus(eventId, participantId, RegistrationStatus.ACTIVE))
                .thenReturn(false);
        when(registrationRepository.save(any(Registration.class))).thenReturn(savedRegistration);
        when(registrationMapper.toResponse(savedRegistration)).thenReturn(response);

        // When
        RegistrationResponse result = registrationService.register(eventId, request);

        // Then
        assertNotNull(result);
        assertEquals(9, event.getAvailableSeats()); // Giảm 1 ghế
        verify(eventRepository).save(event);
        verify(registrationRepository).save(any(Registration.class));
    }

    @Test
    void register_ThrowsResourceNotFoundException_WhenEventDoesNotExist() {
        // Given
        Long eventId = 1L;
        RegisterParticipantRequest request = new RegisterParticipantRequest(2L);

        when(securityContext.getAuthentication()).thenReturn(null); // Anonymous bypass ownership checks
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> registrationService.register(eventId, request));
        verify(registrationRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void register_ThrowsInvalidEventStateException_WhenEventIsNotOpen() {
        // Given
        Long eventId = 1L;
        Long participantId = 2L;
        RegisterParticipantRequest request = new RegisterParticipantRequest(participantId);

        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.DRAFT) // DRAFT instead of OPEN
                .startAt(LocalDateTime.now(clock).plusDays(1))
                .availableSeats(10)
                .build();

        Participant participant = Participant.builder()
                .id(participantId)
                .build();

        when(securityContext.getAuthentication()).thenReturn(null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));

        // When & Then
        assertThrows(InvalidEventStateException.class, () -> registrationService.register(eventId, request));
        verify(registrationRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void register_ThrowsEventFullCapacityException_WhenNoSeatsAvailable() {
        // Given
        Long eventId = 1L;
        Long participantId = 2L;
        RegisterParticipantRequest request = new RegisterParticipantRequest(participantId);

        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now(clock).plusDays(1))
                .availableSeats(0) // Full capacity
                .build();

        Participant participant = Participant.builder()
                .id(participantId)
                .build();

        when(securityContext.getAuthentication()).thenReturn(null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));

        // When & Then
        assertThrows(EventFullCapacityException.class, () -> registrationService.register(eventId, request));
        verify(registrationRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void register_ThrowsDuplicateRegistrationException_WhenAlreadyRegistered() {
        // Given
        Long eventId = 1L;
        Long participantId = 2L;
        RegisterParticipantRequest request = new RegisterParticipantRequest(participantId);

        Event event = Event.builder()
                .id(eventId)
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now(clock).plusDays(1))
                .availableSeats(5)
                .build();

        Participant participant = Participant.builder()
                .id(participantId)
                .build();

        when(securityContext.getAuthentication()).thenReturn(null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(participantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(registrationRepository.existsByEventIdAndParticipantIdAndStatus(eventId, participantId, RegistrationStatus.ACTIVE))
                .thenReturn(true); // Already active

        // When & Then
        assertThrows(DuplicateRegistrationException.class, () -> registrationService.register(eventId, request));
        verify(registrationRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancel_Success() {
        // Given
        Long eventId = 1L;
        Long registrationId = 100L;

        Event event = Event.builder()
                .id(eventId)
                .capacity(10)
                .availableSeats(5)
                .build();

        Participant participant = Participant.builder()
                .id(2L)
                .build();

        Registration registration = Registration.builder()
                .id(registrationId)
                .event(event)
                .participant(participant)
                .status(RegistrationStatus.ACTIVE)
                .build();

        when(securityContext.getAuthentication()).thenReturn(null); // Bypass ownership
        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));

        // When
        registrationService.cancel(eventId, registrationId);

        // Then
        assertEquals(RegistrationStatus.CANCELLED, registration.getStatus());
        assertNotNull(registration.getCancelledAt());
        assertEquals(6, event.getAvailableSeats()); // Khôi phục 1 ghế
        verify(eventRepository).save(event);
        verify(registrationRepository).save(registration);
    }

    @Test
    void cancel_ThrowsInvalidCancellationException_WhenRegistrationNotActive() {
        // Given
        Long eventId = 1L;
        Long registrationId = 100L;

        Event event = Event.builder()
                .id(eventId)
                .build();

        Registration registration = Registration.builder()
                .id(registrationId)
                .event(event)
                .status(RegistrationStatus.CANCELLED) // Already cancelled
                .build();

        when(securityContext.getAuthentication()).thenReturn(null);
        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));

        // When & Then
        assertThrows(InvalidCancellationException.class, () -> registrationService.cancel(eventId, registrationId));
        verify(eventRepository, never()).save(any());
    }
}
