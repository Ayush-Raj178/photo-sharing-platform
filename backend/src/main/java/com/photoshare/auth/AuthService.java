package com.photoshare.auth;

import com.photoshare.common.ApiException;
import com.photoshare.common.AttemptLimiter;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.security.CurrentUser;
import com.photoshare.security.JwtService;
import com.photoshare.user.UserAccount;
import com.photoshare.user.UserDtos;
import com.photoshare.user.UserRepository;
import com.photoshare.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AttemptLimiter limiter;
    private final PhotoShareProperties properties;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       CurrentUser currentUser, AttemptLimiter limiter, PhotoShareProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.limiter = limiter;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("PhotoShare dummy password value");
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, String clientIp) {
        limiter.check("register:" + clientIp, properties.rateLimit().registrationAttempts());
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("ACCOUNT_UNAVAILABLE", "Account could not be created");
        }
        UserAccount user = users.save(new UserAccount(email, request.displayName().trim(),
                passwordEncoder.encode(request.password()), UserRole.ADMIN, null, Instant.now()));
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        limiter.check("login:account:" + clientIp + ":" + email, properties.rateLimit().loginAttempts());
        limiter.check("login:ip:" + clientIp, properties.rateLimit().loginIpAttempts());
        UserAccount user = users.findByEmail(email).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse me() {
        UserAccount user = users.findById(currentUser.require().id())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_TOKEN", "Authentication token is invalid"));
        return UserDtos.UserResponse.from(user);
    }

    private AuthDtos.AuthResponse response(UserAccount user) {
        JwtService.Token token = jwtService.issueStaff(user);
        return new AuthDtos.AuthResponse(token.value(), "Bearer", token.expiresIn(), UserDtos.UserResponse.from(user));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

