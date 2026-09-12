package com.photoshare.event;

import com.photoshare.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class EventDtos {
    private EventDtos() {}

    public record CreateEventRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description
    ) {}

    public record EventResponse(String id, String name, String description, Instant createdAt) {
        public static EventResponse from(EventEntity event) {
            return new EventResponse(event.getId().toString(), event.getName(), event.getDescription(),
                    event.getCreatedAt());
        }
    }

    public record EventListResponse(List<EventResponse> items) {}

    public record AddMemberRequest(
            String userId,
            @Email @Size(max = 254) String email,
            @Size(max = 100) String displayName,
            @Size(min = 8, max = 128) String password
    ) {}

    public record MemberResponse(UserDtos.UserResponse user, Instant addedAt) {}
    public record MemberListResponse(List<MemberResponse> items) {}
    public record UserListResponse(List<UserDtos.UserResponse> items) {}
}
