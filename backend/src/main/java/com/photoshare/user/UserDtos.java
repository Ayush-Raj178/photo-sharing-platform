package com.photoshare.user;

public final class UserDtos {
    private UserDtos() {}

    public record UserResponse(String id, String email, String displayName, UserRole role) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getRole());
        }
    }
}

