package com.eventhub.service;

import com.eventhub.controller.dto.CreateParticipantRequest;
import com.eventhub.controller.dto.PageResponse;
import com.eventhub.controller.dto.ParticipantResponse;
import com.eventhub.controller.mapper.ParticipantMapper;
import com.eventhub.domain.Participant;
import com.eventhub.exception.DuplicateEmailException;
import com.eventhub.exception.ResourceNotFoundException;
import com.eventhub.repository.ParticipantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class ParticipantService {

    private final ParticipantRepository participantRepository;
    private final ParticipantMapper participantMapper;
    private final com.eventhub.repository.UserAccountRepository userAccountRepository;
    private final Clock clock;

    public ParticipantService(
            ParticipantRepository participantRepository,
            ParticipantMapper participantMapper,
            com.eventhub.repository.UserAccountRepository userAccountRepository,
            Clock clock) {
        this.participantRepository = participantRepository;
        this.participantMapper = participantMapper;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public ParticipantResponse create(CreateParticipantRequest request) {
        if (participantRepository.findByEmail(request.email()).isPresent()) {
            throw new DuplicateEmailException(request.email());
        }

        Participant participant = participantMapper.toEntity(request);
        participant.setCreatedAt(Instant.now(clock));
        
        Participant saved = participantRepository.save(participant);
        return participantMapper.toResponse(saved);
    }

    public ParticipantResponse findById(Long id) {
        org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_EVENT_ADMIN"));
            
            if (!isAdmin) {
                String currentEmail = authentication.getName();
                com.eventhub.domain.UserAccount currentAccount = userAccountRepository.findByEmail(currentEmail)
                        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
                
                if (currentAccount.getParticipant() == null || !currentAccount.getParticipant().getId().equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("A participant can only view their own profile");
                }
            }
        }

        Participant participant = participantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found with id: " + id));
        return participantMapper.toResponse(participant);
    }

    public PageResponse<ParticipantResponse> findAll(Pageable pageable) {
        Page<Participant> page = participantRepository.findAll(pageable);
        return new PageResponse<>(
                page.getContent().stream().map(participantMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
