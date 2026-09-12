package com.photoshare.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UserAccount> findAllByProvisionedByAndRoleOrderByCreatedAtAscIdAsc(Long provisionedBy, UserRole role);
}

