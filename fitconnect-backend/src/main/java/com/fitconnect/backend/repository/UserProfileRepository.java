package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUser_UserId(Long userId);
}
