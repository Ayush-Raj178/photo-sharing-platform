package com.photoshare.auth;

import com.photoshare.user.UserDtos;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password
    ) {}

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserDtos.UserResponse user) {}
}
