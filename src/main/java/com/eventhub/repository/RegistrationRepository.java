package com.eventhub.repository;

import com.eventhub.domain.Registration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    Page<Registration> findByEventId(Long eventId, Pageable pageable);
}
