package com.eventhub.controller;

import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.RegisterParticipantRequest;
import com.eventhub.controller.dto.RegistrationResponse;
import com.eventhub.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/events/{eventId}/registrations")
public class RegistrationController {

    private static final int MAX_PAGE_SIZE = 100;
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable Long eventId,
            @RequestBody @Valid RegisterParticipantRequest request) {
        RegistrationResponse response = registrationService.register(eventId, request);
        URI location = URI.create("/api/events/" + eventId + "/registrations/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<RegistrationResponse>> findByEventId(
            @PathVariable Long eventId,
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().and(Sort.by("id").ascending());
        Pageable cappedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);

        PageResponse<RegistrationResponse> response = registrationService.findByEventId(eventId, cappedPageable);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{registrationId}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long eventId,
            @PathVariable Long registrationId) {
        registrationService.cancel(eventId, registrationId);
        return ResponseEntity.noContent().build();
    }
}
