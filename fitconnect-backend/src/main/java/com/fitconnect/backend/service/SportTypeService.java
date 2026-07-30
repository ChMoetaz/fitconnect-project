package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.repository.SportTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal helper shared by several services (Onboarding, TrainingPlan, Community, Coach). */
@Service
@RequiredArgsConstructor
public class SportTypeService {

    private final SportTypeRepository sportTypeRepository;

    @Transactional
    public SportType getOrCreate(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return sportTypeRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> sportTypeRepository.save(SportType.builder().name(name).build()));
    }
}
