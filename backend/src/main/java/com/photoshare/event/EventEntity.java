package com.photoshare.event;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "events")
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventEntity() {}

    public EventEntity(Long ownerId, String name, String description, Instant createdAt) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}

