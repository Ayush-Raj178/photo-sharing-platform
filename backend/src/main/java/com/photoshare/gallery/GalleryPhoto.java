package com.photoshare.gallery;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "gallery_photos")
public class GalleryPhoto {
    @EmbeddedId
    private GalleryPhotoId id;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(nullable = false)
    private int position;
    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    protected GalleryPhoto() {}

    public GalleryPhoto(Long galleryId, Long photoId, Long eventId, int position, Instant selectedAt) {
        this.id = new GalleryPhotoId(galleryId, photoId);
        this.eventId = eventId;
        this.position = position;
        this.selectedAt = selectedAt;
    }

    public GalleryPhotoId getId() { return id; }
    public Long getEventId() { return eventId; }
    public int getPosition() { return position; }
    public Instant getSelectedAt() { return selectedAt; }
}

