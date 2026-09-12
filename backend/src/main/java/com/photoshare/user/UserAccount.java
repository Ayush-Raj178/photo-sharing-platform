package com.photoshare.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "provisioned_by")
    private Long provisionedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String email, String displayName, String passwordHash, UserRole role,
                       Long provisionedBy, Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.provisionedBy = provisionedBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public Long getProvisionedBy() { return provisionedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

