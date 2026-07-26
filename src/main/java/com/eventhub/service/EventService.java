package com.eventhub.service;

import com.eventhub.controller.dto.CreateEventRequest;
import com.eventhub.controller.dto.EventResponse;
import com.eventhub.controller.dto.EventSummaryResponse;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.UpdateEventRequest;
import com.eventhub.controller.mapper.EventMapper;
import com.eventhub.domain.Event;
import com.eventhub.domain.EventStatus;
import com.eventhub.exception.InvalidCancellationException;
import com.eventhub.exception.InvalidEventStateException;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final Clock clock;

    public EventService(EventRepository eventRepository, EventMapper eventMapper, Clock clock) {
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.clock = clock;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = eventMapper.toEntity(request);
        event.setStatus(EventStatus.DRAFT); // Mặc định là DRAFT khi tạo mới
        event.setCreatedAt(Instant.now(clock));
        Event saved = eventRepository.save(event);
        return eventMapper.toResponse(saved);
    }

    public EventResponse findById(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        return eventMapper.toResponse(event);
    }

    public PageResponse<EventSummaryResponse> search(
            String title,
            EventStatus status,
            LocalDateTime startAt,
            Pageable pageable) {
        Page<Event> page = eventRepository.search(title, status, startAt, pageable);
        return new PageResponse<>(
                page.getContent().stream().map(eventMapper::toSummaryResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setLocation(request.location());
        event.setStartAt(request.startAt());
        
        // Điều chỉnh available seats nếu capacity thay đổi
        int diff = request.capacity() - event.getCapacity();
        event.setCapacity(request.capacity());
        event.setAvailableSeats(event.getAvailableSeats() + diff);

        Event updated = eventRepository.save(event);
        return eventMapper.toResponse(updated);
    }

    @Transactional
    public EventResponse cancel(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidCancellationException("Event is already cancelled.");
        }
        
        event.setStatus(EventStatus.CANCELLED);
        Event updated = eventRepository.save(event);
        return eventMapper.toResponse(updated);
    }

    @Transactional
    public EventResponse publish(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + id));
        
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException("Only DRAFT events can be published.");
        }
        
        event.setStatus(EventStatus.OPEN);
        Event updated = eventRepository.save(event);
        return eventMapper.toResponse(updated);
    }
}
