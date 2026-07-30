package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.CommunityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityGroupRepository extends JpaRepository<CommunityGroup, Long> {

    // LEFT JOIN FETCH members so memberCount / isJoined can be computed after the Hibernate
    // session closes (open-in-view=false) — same convention as the coach/plan collections.
    // DISTINCT drops the duplicate group rows the collection join produces.
    @Query("SELECT DISTINCT g FROM CommunityGroup g LEFT JOIN FETCH g.members")
    List<CommunityGroup> findAllWithMembers();

    @Query("SELECT DISTINCT g FROM CommunityGroup g LEFT JOIN FETCH g.members "
            + "WHERE g.sportType.sportTypeId = :sportTypeId")
    List<CommunityGroup> findBySportType_SportTypeIdWithMembers(@Param("sportTypeId") Long sportTypeId);
}
