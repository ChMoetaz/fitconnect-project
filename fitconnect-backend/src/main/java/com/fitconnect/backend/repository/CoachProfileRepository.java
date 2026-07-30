package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.CoachProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {

    @Query("SELECT DISTINCT c FROM CoachProfile c LEFT JOIN FETCH c.sportTypes")
    List<CoachProfile> findAllWithSportTypes();

    @Query("SELECT c FROM CoachProfile c LEFT JOIN FETCH c.sportTypes WHERE c.coachId = :coachId")
    Optional<CoachProfile> findByIdWithSportTypes(@Param("coachId") Long coachId);

    @Query("SELECT DISTINCT c FROM CoachProfile c LEFT JOIN FETCH c.sportTypes st WHERE st.sportTypeId = :sportTypeId")
    List<CoachProfile> findBySportTypes_SportTypeId(@Param("sportTypeId") Long sportTypeId);
}
