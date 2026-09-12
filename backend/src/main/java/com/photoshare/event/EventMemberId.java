package com.photoshare.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EventMemberId implements Serializable {
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "user_id")
    private Long userId;

    protected EventMemberId() {}

    public EventMemberId(Long eventId, Long userId) {
        this.eventId = eventId;
        this.userId = userId;
    }

    public Long getEventId() { return eventId; }
    public Long getUserId() { return userId; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EventMemberId that
                && Objects.equals(eventId, that.eventId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId);
    }
}

