package com.fitconnect.backend.repository;

import com.fitconnect.backend.domain.ProgressRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgressRecordRepository extends JpaRepository<ProgressRecord, Long> {
    List<ProgressRecord> findByUser_UserIdOrderByDateDesc(Long userId);

    /** The N most recent records, used by AiTrainingPlanService.adaptPlan to feed real progress to Gemini. */
    List<ProgressRecord> findTop10ByUser_UserIdOrderByDateDesc(Long userId);
}
