package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.repository.SportTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link SportTypeService#getOrCreate} "find-or-create" helper: the blank
 * short-circuit, reuse of an existing row, and creation of a new one.
 */
@ExtendWith(MockitoExtension.class)
class SportTypeServiceTest {

    @Mock
    private SportTypeRepository sportTypeRepository;

    @InjectMocks
    private SportTypeService sportTypeService;

    @Test
    void getOrCreate_returnsNullForNullOrBlankName() {
        assertThat(sportTypeService.getOrCreate(null)).isNull();
        assertThat(sportTypeService.getOrCreate("   ")).isNull();

        // A blank name must never hit the database.
        verify(sportTypeRepository, never()).findByNameIgnoreCase(any());
        verify(sportTypeRepository, never()).save(any());
    }

    @Test
    void getOrCreate_reusesExistingSportType() {
        SportType existing = SportType.builder().sportTypeId(3L).name("Running").build();
        when(sportTypeRepository.findByNameIgnoreCase("Running")).thenReturn(Optional.of(existing));

        assertThat(sportTypeService.getOrCreate("Running")).isSameAs(existing);
        verify(sportTypeRepository, never()).save(any());
    }

    @Test
    void getOrCreate_createsWhenMissing() {
        when(sportTypeRepository.findByNameIgnoreCase("Cycling")).thenReturn(Optional.empty());
        when(sportTypeRepository.save(any(SportType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SportType created = sportTypeService.getOrCreate("Cycling");

        assertThat(created.getName()).isEqualTo("Cycling");
        verify(sportTypeRepository).save(any(SportType.class));
    }
}