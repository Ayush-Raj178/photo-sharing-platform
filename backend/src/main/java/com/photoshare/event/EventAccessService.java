package com.photoshare.event;

import com.photoshare.common.ApiException;
import com.photoshare.security.CurrentUser;
import com.photoshare.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventAccessService {
    private final EventRepository events;
    private final EventMemberRepository members;

    public EventAccessService(EventRepository events, EventMemberRepository members) {
        this.events = events;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public EventEntity requireOwned(long eventId, CurrentUser.Principal principal) {
        if (principal.role() != UserRole.ADMIN) {
            throw ApiException.forbidden("FORBIDDEN", "Operation is not permitted");
        }
        return events.findByIdAndOwnerId(eventId, principal.id())
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event is unavailable"));
    }

    @Transactional(readOnly = true)
    public EventEntity requireAssigned(long eventId, CurrentUser.Principal principal) {
        if (principal.role() != UserRole.TEAM_MEMBER) {
            throw ApiException.forbidden("FORBIDDEN", "Operation is not permitted");
        }
        EventEntity event = events.findById(eventId)
                .filter(ignored -> members.existsByIdEventIdAndIdUserId(eventId, principal.id()))
                .orElseThrow(() -> ApiException.notFound("EVENT_NOT_FOUND", "Event is unavailable"));
        return event;
    }

    @Transactional(readOnly = true)
    public EventEntity requireReadable(long eventId, CurrentUser.Principal principal) {
        if (principal.role() == UserRole.ADMIN) {
            return requireOwned(eventId, principal);
        }
        return requireAssigned(eventId, principal);
    }
}

