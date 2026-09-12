package com.photoshare.event;

import com.photoshare.auth.AuthService;
import com.photoshare.common.ApiException;
import com.photoshare.common.AttemptLimiter;
import com.photoshare.config.PhotoShareProperties;
import com.photoshare.security.CurrentUser;
import com.photoshare.user.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class MemberService {
    private final UserRepository users;
    private final EventMemberRepository members;
    private final EventAccessService access;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter limiter;
    private final PhotoShareProperties properties;

    public MemberService(UserRepository users, EventMemberRepository members, EventAccessService access,
                         CurrentUser currentUser, PasswordEncoder passwordEncoder, AttemptLimiter limiter,
                         PhotoShareProperties properties) {
        this.users = users;
        this.members = members;
        this.access = access;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public EventDtos.UserListResponse roster() {
        CurrentUser.Principal principal = requireAdmin();
        return new EventDtos.UserListResponse(users
                .findAllByProvisionedByAndRoleOrderByCreatedAtAscIdAsc(principal.id(), UserRole.TEAM_MEMBER)
                .stream().map(UserDtos.UserResponse::from).toList());
    }

    @Transactional
    public EventDtos.MemberResponse add(long eventId, EventDtos.AddMemberRequest request) {
        CurrentUser.Principal principal = requireAdmin();
        access.requireOwned(eventId, principal);
        limiter.check("member-provision:" + principal.id(), properties.rateLimit().memberProvisionAttempts());

        boolean existingForm = request.userId() != null && !request.userId().isBlank();
        boolean createFieldsPresent = request.email() != null || request.displayName() != null || request.password() != null;
        if (existingForm == createFieldsPresent) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "Provide either userId or email, displayName, and password");
        }

        UserAccount member;
        if (existingForm) {
            long userId;
            try {
                userId = Long.parseLong(request.userId());
            } catch (NumberFormatException ex) {
                throw ApiException.badRequest("VALIDATION_ERROR", "userId must be a positive decimal value");
            }
            member = users.findById(userId)
                    .filter(user -> user.getRole() == UserRole.TEAM_MEMBER)
                    .filter(user -> Long.valueOf(principal.id()).equals(user.getProvisionedBy()))
                    .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Team Member is unavailable"));
        } else {
            if (request.email() == null || request.email().isBlank()
                    || request.displayName() == null || request.displayName().isBlank()
                    || request.password() == null || request.password().length() < 8) {
                throw ApiException.badRequest("VALIDATION_ERROR", "Email, displayName, and password are required");
            }
            String email = AuthService.normalizeEmail(request.email());
            if (users.existsByEmail(email)) {
                throw ApiException.conflict("ACCOUNT_UNAVAILABLE", "Account could not be created");
            }
            member = users.save(new UserAccount(email, request.displayName().trim(),
                    passwordEncoder.encode(request.password()), UserRole.TEAM_MEMBER, principal.id(), Instant.now()));
        }

        EventMemberId id = new EventMemberId(eventId, member.getId());
        if (members.existsById(id)) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "Team Member is already assigned");
        }
        EventMember membership = members.save(new EventMember(eventId, member.getId(), Instant.now()));
        return new EventDtos.MemberResponse(UserDtos.UserResponse.from(member), membership.getAddedAt());
    }

    @Transactional(readOnly = true)
    public EventDtos.MemberListResponse list(long eventId) {
        CurrentUser.Principal principal = requireAdmin();
        access.requireOwned(eventId, principal);
        List<EventDtos.MemberResponse> result = members.findAllByIdEventIdOrderByAddedAtAsc(eventId).stream()
                .map(member -> new EventDtos.MemberResponse(
                        UserDtos.UserResponse.from(users.findById(member.getId().getUserId()).orElseThrow()),
                        member.getAddedAt()))
                .toList();
        return new EventDtos.MemberListResponse(result);
    }

    private CurrentUser.Principal requireAdmin() {
        CurrentUser.Principal principal = currentUser.require();
        if (principal.role() != UserRole.ADMIN) {
            throw ApiException.forbidden("FORBIDDEN", "Operation is not permitted");
        }
        return principal;
    }
}
