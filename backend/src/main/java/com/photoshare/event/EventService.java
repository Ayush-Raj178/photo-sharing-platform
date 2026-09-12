package com.photoshare.event;

import com.photoshare.security.CurrentUser;
import com.photoshare.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EventService {
    private final EventRepository events;
    private final EventMemberRepository members;
    private final EventAccessService access;
    private final CurrentUser currentUser;

    public EventService(EventRepository events, EventMemberRepository members,
                        EventAccessService access, CurrentUser currentUser) {
        this.events = events;
        this.members = members;
        this.access = access;
        this.currentUser = currentUser;
    }

    @Transactional
    public EventDtos.EventResponse create(EventDtos.CreateEventRequest request) {
        CurrentUser.Principal principal = currentUser.require();
        if (principal.role() != UserRole.ADMIN) {
            throw com.photoshare.common.ApiException.forbidden("FORBIDDEN", "Operation is not permitted");
        }
        EventEntity event = events.save(new EventEntity(principal.id(), request.name().trim(),
                trimToNull(request.description()), Instant.now()));
        return EventDtos.EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventDtos.EventListResponse list() {
        CurrentUser.Principal principal = currentUser.require();
        List<EventEntity> result = principal.role() == UserRole.ADMIN
                ? events.findAllByOwnerIdOrderByCreatedAtAscIdAsc(principal.id())
                : members.findAssignedEvents(principal.id());
        return new EventDtos.EventListResponse(result.stream().map(EventDtos.EventResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public EventDtos.EventResponse get(long eventId) {
        return EventDtos.EventResponse.from(access.requireReadable(eventId, currentUser.require()));
    }

    static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}

