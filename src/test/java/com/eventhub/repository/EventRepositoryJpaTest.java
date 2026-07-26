package com.eventhub.repository;

import com.eventhub.domain.Event;
import com.eventhub.domain.EventStatus;
import com.eventhub.domain.Participant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class EventRepositoryJpaTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Test
    void search_ShouldReturnFilteredAndPaginatedEvents() {
        // Given
        Event event1 = Event.builder()
                .title("Spring Boot Workshop")
                .description("Learn spring boot")
                .location("HCM")
                .status(EventStatus.OPEN)
                .startAt(LocalDateTime.now().plusDays(2))
                .capacity(100)
                .availableSeats(100)
                .createdAt(Instant.now())
                .build();

        Event event2 = Event.builder()
                .title("Advanced Hibernate")
                .description("Learn hibernate")
                .location("HN")
                .status(EventStatus.DRAFT)
                .startAt(LocalDateTime.now().plusDays(5))
                .capacity(50)
                .availableSeats(50)
                .createdAt(Instant.now())
                .build();

        eventRepository.save(event1);
        eventRepository.save(event2);
        eventRepository.flush();

        // When: Search with title "Spring"
        Page<Event> page = eventRepository.search("spring", null, null, PageRequest.of(0, 10, Sort.by("id").ascending()));

        // Then
        assertEquals(1, page.getTotalElements());
        assertEquals("Spring Boot Workshop", page.getContent().get(0).getTitle());

        // When: Search with status DRAFT
        Page<Event> draftPage = eventRepository.search(null, EventStatus.DRAFT, null, PageRequest.of(0, 10, Sort.by("id").ascending()));

        // Then
        assertEquals(1, draftPage.getTotalElements());
        assertEquals("Advanced Hibernate", draftPage.getContent().get(0).getTitle());
    }

    @Test
    void save_ThrowsDataIntegrityViolationException_WhenEmailDuplicated() {
        // Given
        Participant participant1 = Participant.builder()
                .fullName("User One")
                .email("duplicate@email.com")
                .createdAt(Instant.now())
                .build();

        Participant participant2 = Participant.builder()
                .fullName("User Two")
                .email("duplicate@email.com") // Duplicated email
                .createdAt(Instant.now())
                .build();

        participantRepository.save(participant1);

        // When & Then: Flush to DB to trigger unique constraint validation
        assertThrows(DataIntegrityViolationException.class, () -> {
            participantRepository.save(participant2);
            participantRepository.flush();
        });
    }
}
