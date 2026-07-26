package com.eventhub.service;

import com.eventhub.controller.dto.CreateEventRequest;
import com.eventhub.controller.dto.EventResponse;
import com.eventhub.controller.dto.EventSummaryResponse;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.UpdateEventRequest;
import com.eventhub.domain.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class EventService {

    public EventResponse create(CreateEventRequest request) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public EventResponse findById(Long id) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public PageResponse<EventSummaryResponse> search(
            String title,
            EventStatus status,
            LocalDateTime startAt,
            Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public EventResponse update(Long id, UpdateEventRequest request) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }

    public void cancel(Long id) {
        throw new UnsupportedOperationException("Not implemented yet for Task 2 skeleton");
    }
}
