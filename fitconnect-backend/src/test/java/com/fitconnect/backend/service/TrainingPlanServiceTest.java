package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.TrainingPlan;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.TrainingPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrainingPlanService} — the persistence-only companion of
 * {@link AiTrainingPlanService}. Focuses on the fetch-join delegations and the
 * "not found" guards on get/delete.
 */
@ExtendWith(MockitoExtension.class)
class TrainingPlanServiceTest {

    @Mock
    private TrainingPlanRepository trainingPlanRepository;

    @InjectMocks
    private TrainingPlanService trainingPlanService;

    @Test
    void getPlansForUser_usesFetchJoinQuery() {
        List<TrainingPlan> expected = List.of(TrainingPlan.builder().planId(1L).title("Plan").build());
        when(trainingPlanRepository.findByUser_UserIdWithExercises(1L)).thenReturn(expected);

        assertThat(trainingPlanService.getPlansForUser(1L)).isEqualTo(expected);
    }

    @Test
    void getById_returnsPlanWhenPresent() {
        TrainingPlan plan = TrainingPlan.builder().planId(5L).title("Plan").build();
        when(trainingPlanRepository.findByIdWithExercises(5L)).thenReturn(Optional.of(plan));

        assertThat(trainingPlanService.getById(5L)).isSameAs(plan);
    }

    @Test
    void getById_throwsWhenMissing() {
        when(trainingPlanRepository.findByIdWithExercises(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trainingPlanService.getById(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("5");
    }

    @Test
    void save_delegatesToRepository() {
        TrainingPlan plan = TrainingPlan.builder().title("Plan").build();
        when(trainingPlanRepository.save(plan)).thenReturn(plan);

        assertThat(trainingPlanService.save(plan)).isSameAs(plan);
    }

    @Test
    void deletePlan_deletesWhenExists() {
        when(trainingPlanRepository.existsById(5L)).thenReturn(true);

        trainingPlanService.deletePlan(5L);

        verify(trainingPlanRepository).deleteById(5L);
    }

    @Test
    void deletePlan_throwsWhenMissing() {
        when(trainingPlanRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> trainingPlanService.deletePlan(5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("5");
        verify(trainingPlanRepository, never()).deleteById(5L);
    }
}