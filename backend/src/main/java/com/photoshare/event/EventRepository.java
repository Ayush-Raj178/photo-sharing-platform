package com.photoshare.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
    List<EventEntity> findAllByOwnerIdOrderByCreatedAtAscIdAsc(Long ownerId);
    Optional<EventEntity> findByIdAndOwnerId(Long id, Long ownerId);
}

