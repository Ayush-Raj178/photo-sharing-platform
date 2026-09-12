package com.photoshare.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findAllByEventIdAndStatusOrderByCreatedAtAscIdAsc(Long eventId, PhotoStatus status);
    List<Photo> findAllByEventIdAndUploadedByAndStatusOrderByCreatedAtAscIdAsc(
            Long eventId, Long uploadedBy, PhotoStatus status);
    Optional<Photo> findByIdAndEventIdAndStatus(Long id, Long eventId, PhotoStatus status);
    List<Photo> findAllByIdInAndEventIdAndStatus(Collection<Long> ids, Long eventId, PhotoStatus status);

    Page<Photo> findAllByEventIdAndUploadedByAndStatus(
            Long eventId, Long uploadedBy, PhotoStatus status, Pageable pageable);

    @Query("""
            select p from Photo p
            where p.eventId = :eventId and p.status = :status
              and (:search = '' or lower(p.originalFilename) like lower(concat('%', :search, '%')))
              and (:uploaderId is null or p.uploadedBy = :uploaderId)
              and (:selected is null
                   or (:selected = true and exists (select gp.id.photoId from GalleryPhoto gp
                       where gp.eventId = p.eventId and gp.id.photoId = p.id))
                   or (:selected = false and not exists (select gp.id.photoId from GalleryPhoto gp
                       where gp.eventId = p.eventId and gp.id.photoId = p.id)))
            """)
    Page<Photo> searchAdminPhotos(@Param("eventId") Long eventId,
                                  @Param("status") PhotoStatus status,
                                  @Param("search") String search,
                                  @Param("uploaderId") Long uploaderId,
                                  @Param("selected") Boolean selected,
                                  Pageable pageable);
}
