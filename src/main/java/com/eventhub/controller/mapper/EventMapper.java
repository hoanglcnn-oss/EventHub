package com.eventhub.controller.mapper;

import com.eventhub.controller.dto.CreateEventRequest;
import com.eventhub.controller.dto.EventResponse;
import com.eventhub.controller.dto.EventSummaryResponse;
import com.eventhub.domain.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public Event toEntity(CreateEventRequest request) {
        if (request == null) {
            return null;
        }
        return Event.builder()
                .title(request.title())
                .description(request.description())
                .location(request.location())
                .startAt(request.startAt())
                .capacity(request.capacity())
                .availableSeats(request.capacity())
                .build();
    }

    public EventResponse toResponse(Event event) {
        if (event == null) {
            return null;
        }
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getLocation(),
                event.getStartAt(),
                event.getCapacity(),
                event.getAvailableSeats(),
                event.getStatus(),
                event.getCreatedAt()
        );
    }

    public EventSummaryResponse toSummaryResponse(Event event) {
        if (event == null) {
            return null;
        }
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getLocation(),
                event.getStartAt(),
                event.getAvailableSeats(),
                event.getStatus()
        );
    }
}
