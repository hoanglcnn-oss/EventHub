package com.eventhub.controller;

import com.eventhub.controller.dto.CreateEventRequest;
import com.eventhub.controller.dto.EventResponse;
import com.eventhub.controller.dto.EventSummaryResponse;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.UpdateEventRequest;
import com.eventhub.domain.EventStatus;
import com.eventhub.service.EventService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final int MAX_PAGE_SIZE = 100;
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@RequestBody @Valid CreateEventRequest request) {
        EventResponse created = eventService.create(request);
        URI location = URI.create("/api/events/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> findById(@PathVariable Long eventId) {
        EventResponse event = eventService.findById(eventId);
        return ResponseEntity.ok(event);
    }

    @GetMapping
    public ResponseEntity<PageResponse<EventSummaryResponse>> search(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {

        int pageNumber = page != null ? page : pageable.getPageNumber();
        int pageSize = size != null ? size : pageable.getPageSize();

        if (pageNumber < 0) {
            throw new IllegalArgumentException("Page number must be zero or positive");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be at least one");
        }

        // Giới hạn page size tối đa để bảo vệ hệ thống
        int cappedSize = Math.min(pageSize, MAX_PAGE_SIZE);

        // Luôn đính kèm tie-breaker 'id' để đảm bảo thứ tự sắp xếp nhất quán
        Sort sort = pageable.getSort().and(Sort.by("id").ascending());

        Pageable cappedPageable = PageRequest.of(pageNumber, cappedSize, sort);

        PageResponse<EventSummaryResponse> response = eventService.search(title, status, startAt, cappedPageable);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> update(
            @PathVariable Long eventId,
            @RequestBody @Valid UpdateEventRequest request) {
        EventResponse updated = eventService.update(eventId, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{eventId}/cancellations")
    public ResponseEntity<Void> cancel(@PathVariable Long eventId) {
        eventService.cancel(eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/publish")
    public ResponseEntity<EventResponse> publish(@PathVariable Long eventId) {
        EventResponse response = eventService.publish(eventId);
        return ResponseEntity.ok(response);
    }
}