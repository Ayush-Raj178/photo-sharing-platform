package com.photoshare.gallery;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "galleries")
public class Gallery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;
    @Column(nullable = false, length = 150)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GalleryStatus status;
    @Column(name = "pin_hash", length = 255)
    private String pinHash;
    @Column(name = "pin_version", nullable = false)
    private int pinVersion;
    @Column(name = "share_token", nullable = false, length = 32, unique = true)
    private String shareToken;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Gallery() {}

    public Gallery(Long eventId, String title, String shareToken, Instant now) {
        this.eventId = eventId;
        this.title = title;
        this.shareToken = shareToken;
        this.status = GalleryStatus.DRAFT;
        this.pinVersion = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setPin(String pinHash, Instant now) {
        this.pinHash = pinHash;
        this.pinVersion += 1;
        this.updatedAt = now;
    }

    public void publish(Instant now) {
        this.status = GalleryStatus.PUBLISHED;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void setExpiresAt(Instant expiresAt, Instant now) {
        this.expiresAt = expiresAt;
        this.updatedAt = now;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getTitle() { return title; }
    public GalleryStatus getStatus() { return status; }
    public String getPinHash() { return pinHash; }
    public int getPinVersion() { return pinVersion; }
    public String getShareToken() { return shareToken; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
