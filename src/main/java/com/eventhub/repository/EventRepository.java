package com.eventhub.repository;

import com.eventhub.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eventhub.domain.EventStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("SELECT e FROM Event e WHERE " +
           "(:title IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :title, '%'))) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:startAt IS NULL OR e.startAt >= :startAt)")
    Page<Event> search(
            @Param("title") String title,
            @Param("status") EventStatus status,
            @Param("startAt") LocalDateTime startAt,
            Pageable pageable);
}
