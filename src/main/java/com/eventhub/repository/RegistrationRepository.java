package com.eventhub.repository;

import com.eventhub.domain.Registration;
import com.eventhub.domain.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    boolean existsByEventIdAndParticipantIdAndStatus(Long eventId, Long participantId, RegistrationStatus status);
}
