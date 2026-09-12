package com.photoshare.gallery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {
    List<Gallery> findAllByEventIdOrderByCreatedAtAsc(Long eventId);
    Optional<Gallery> findByIdAndEventId(Long id, Long eventId);
    Optional<Gallery> findByShareTokenAndStatus(String shareToken, GalleryStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gallery g where g.id = :id and g.eventId = :eventId")
    Optional<Gallery> findLocked(Long id, Long eventId);
}

