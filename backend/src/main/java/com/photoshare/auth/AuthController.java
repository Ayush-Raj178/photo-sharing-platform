package com.photoshare.auth;

import com.photoshare.user.UserDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                   HttpServletRequest servletRequest) {
        return service.register(request, servletRequest.getRemoteAddr());
    }

    @PostMapping("/login")
    AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                HttpServletRequest servletRequest) {
        return service.login(request, servletRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    UserDtos.UserResponse me() {
        return service.me();
    }
}

