package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.SportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SportTypeRepository extends JpaRepository<SportType, Long> {
    Optional<SportType> findByNameIgnoreCase(String name);
}
