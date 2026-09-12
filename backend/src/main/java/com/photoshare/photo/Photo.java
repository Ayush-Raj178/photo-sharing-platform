package com.photoshare.photo;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "photos")
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "storage_key", nullable = false, length = 512, unique = true)
    private String storageKey;
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;
    @Column(name = "width_px", nullable = false)
    private int widthPx;
    @Column(name = "height_px", nullable = false)
    private int heightPx;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PhotoStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "failure_code", length = 50)
    private String failureCode;

    protected Photo() {}

    public Photo(Long eventId, Long uploadedBy, String originalFilename, String storageKey,
                 String contentType, long fileSizeBytes, int widthPx, int heightPx, Instant now) {
        this.eventId = eventId;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.status = PhotoStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markReady(Instant now) {
        this.status = PhotoStatus.READY;
        this.failureCode = null;
        this.updatedAt = now;
    }

    public void markFailed(String failureCode, Instant now) {
        this.status = PhotoStatus.FAILED;
        this.failureCode = failureCode;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public Long getUploadedBy() { return uploadedBy; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public int getWidthPx() { return widthPx; }
    public int getHeightPx() { return heightPx; }
    public PhotoStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

