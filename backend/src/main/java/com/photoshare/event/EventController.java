package com.photoshare.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService events;
    private final MemberService members;

    public EventController(EventService events, MemberService members) {
        this.events = events;
        this.members = members;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventDtos.EventResponse create(@Valid @RequestBody EventDtos.CreateEventRequest request) {
        return events.create(request);
    }

    @GetMapping
    EventDtos.EventListResponse list() {
        return events.list();
    }

    @GetMapping("/{eventId}")
    EventDtos.EventResponse get(@PathVariable long eventId) {
        return events.get(eventId);
    }

    @PostMapping("/{eventId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    EventDtos.MemberResponse addMember(@PathVariable long eventId,
                                       @Valid @RequestBody EventDtos.AddMemberRequest request) {
        return members.add(eventId, request);
    }

    @GetMapping("/{eventId}/members")
    EventDtos.MemberListResponse listMembers(@PathVariable long eventId) {
        return members.list(eventId);
    }
}

