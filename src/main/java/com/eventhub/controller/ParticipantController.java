package com.eventhub.controller;

import com.eventhub.controller.dto.CreateParticipantRequest;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.service.ParticipantService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {

    private static final int MAX_PAGE_SIZE = 100;
    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @PostMapping
    public ResponseEntity<ParticipantResponse> create(@RequestBody @Valid CreateParticipantRequest request) {
        ParticipantResponse created = participantService.create(request);
        URI location = URI.create("/api/participants/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{participantId}")
    public ResponseEntity<ParticipantResponse> findById(@PathVariable Long participantId) {
        ParticipantResponse participant = participantService.findById(participantId);
        return ResponseEntity.ok(participant);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ParticipantResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        if (pageable.getPageNumber() < 0) {
            throw new IllegalArgumentException("Page number must be zero or positive");
        }
        if (pageable.getPageSize() < 1) {
            throw new IllegalArgumentException("Page size must be at least one");
        }

        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().and(Sort.by("id").ascending());
        Pageable cappedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);

        PageResponse<ParticipantResponse> response = participantService.findAll(cappedPageable);
        return ResponseEntity.ok(response);
    }
}
