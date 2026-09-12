package com.photoshare.event;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "event_members")
public class EventMember {
    @EmbeddedId
    private EventMemberId id;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected EventMember() {}

    public EventMember(Long eventId, Long userId, Instant addedAt) {
        this.id = new EventMemberId(eventId, userId);
        this.addedAt = addedAt;
    }

    public EventMemberId getId() { return id; }
    public Instant getAddedAt() { return addedAt; }
}

