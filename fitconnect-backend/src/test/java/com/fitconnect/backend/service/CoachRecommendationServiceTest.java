package com.fitconnect.backend.service;

import com.fitconnect.backend.domain.CoachProfile;
import com.fitconnect.backend.domain.SportType;
import com.fitconnect.backend.dto.CoachProfileRequest;
import com.fitconnect.backend.dto.CoachProfileResponse;
import com.fitconnect.backend.exception.ResourceNotFoundException;
import com.fitconnect.backend.repository.CoachProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CoachRecommendationService}. Confirms the read methods use the
 * {@code sportTypes} fetch-join queries (the fix for the LazyInitializationException bug) and map to
 * {@link CoachProfileResponse}, the "not found" guard on {@code getById}, that {@code createCoach}
 * resolves each sport name through {@link SportTypeService} (tolerating a null set) and geocodes the
 * location into lat/lng, and that a failed geocoding leaves lat/lng null without failing creation.
 */
@ExtendWith(MockitoExtension.class)
class CoachRecommendationServiceTest {

    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private SportTypeService sportTypeService;
    @Mock
    private GeocodingService geocodingService;

    @InjectMocks
    private CoachRecommendationService coachRecommendationService;

    @Test
    void getAllCoaches_usesFetchJoinQueryAndMapsToDto() {
        SportType running = SportType.builder().sportTypeId(2L).name("Running").build();
        CoachProfile ada = CoachProfile.builder().coachId(1L).name("Ada")
                .location("Berlin").latitude(52.52).longitude(13.40)
                .sportTypes(java.util.Set.of(running)).build();
        when(coachProfileRepository.findAllWithSportTypes()).thenReturn(List.of(ada));

        List<CoachProfileResponse> result = coachRecommendationService.getAllCoaches();

        assertThat(result).hasSize(1);
        CoachProfileResponse dto = result.get(0);
        assertThat(dto.getName()).isEqualTo("Ada");
        assertThat(dto.getLatitude()).isEqualTo(52.52);
        assertThat(dto.getSportTypes()).extracting(CoachProfileResponse.SportTypeRef::name)
                .containsExactly("Running");
    }

    @Test
    void recommendBySportType_delegatesToDerivedQuery() {
        when(coachProfileRepository.findBySportTypes_SportTypeId(3L)).thenReturn(
                List.of(CoachProfile.builder().coachId(1L).name("Ada").build()));

        assertThat(coachRecommendationService.recommendBySportType(3L))
                .extracting(CoachProfileResponse::getName).containsExactly("Ada");
    }

    @Test
    void createCoach_resolvesSportNamesAndGeocodesLocation() {
        CoachProfileRequest request = new CoachProfileRequest();
        request.setName("Ada");
        request.setSpecialization("Strength");
        request.setExperienceYears(6);
        request.setLocation("Alexanderplatz, Berlin");
        request.setSportTypeNames(java.util.Set.of("Running", "Cycling"));
        when(sportTypeService.getOrCreate("Running")).thenReturn(SportType.builder().sportTypeId(1L).name("Running").build());
        when(sportTypeService.getOrCreate("Cycling")).thenReturn(SportType.builder().sportTypeId(2L).name("Cycling").build());
        when(geocodingService.geocode("Alexanderplatz, Berlin"))
                .thenReturn(Optional.of(new GeocodingService.GeoPoint(52.5219, 13.4132)));
        when(coachProfileRepository.save(any(CoachProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        CoachProfileResponse dto = coachRecommendationService.createCoach(request);

        ArgumentCaptor<CoachProfile> captor = ArgumentCaptor.forClass(CoachProfile.class);
        verify(coachProfileRepository).save(captor.capture());
        CoachProfile persisted = captor.getValue();
        assertThat(persisted.getName()).isEqualTo("Ada");
        assertThat(persisted.getLocation()).isEqualTo("Alexanderplatz, Berlin");
        assertThat(persisted.getLatitude()).isEqualTo(52.5219);
        assertThat(persisted.getLongitude()).isEqualTo(13.4132);
        assertThat(persisted.getSportTypes()).extracting(SportType::getName)
                .containsExactlyInAnyOrder("Running", "Cycling");
        // The returned DTO reflects the geocoded coordinates.
        assertThat(dto.getLatitude()).isEqualTo(52.5219);
    }

    @Test
    void createCoach_toleratesNullSportsAndFailedGeocoding() {
        CoachProfileRequest request = new CoachProfileRequest();
        request.setName("Bob");
        request.setLocation("Nowhere-ville");
        request.setSportTypeNames(null);
        // Geocoding fails / finds nothing → empty, and creation must still succeed with null lat/lng.
        when(geocodingService.geocode("Nowhere-ville")).thenReturn(Optional.empty());
        when(coachProfileRepository.save(any(CoachProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        CoachProfileResponse dto = coachRecommendationService.createCoach(request);

        assertThat(dto.getSportTypes()).isEmpty();
        assertThat(dto.getLatitude()).isNull();
        assertThat(dto.getLongitude()).isNull();
    }

    @Test
    void findNearby_keepsOnlyGeocodedCoachesWithinRadius() {
        // Berlin (~0 km away), Munich (~504 km away), and an ungeocoded coach (null lat/lng).
        CoachProfile berlin = CoachProfile.builder().coachId(1L).name("Berlin").latitude(52.52).longitude(13.40).build();
        CoachProfile munich = CoachProfile.builder().coachId(2L).name("Munich").latitude(48.14).longitude(11.58).build();
        CoachProfile unknown = CoachProfile.builder().coachId(3L).name("Unknown").build();
        when(coachProfileRepository.findAllWithSportTypes()).thenReturn(List.of(berlin, munich, unknown));

        List<CoachProfileResponse> near = coachRecommendationService.findNearby(52.52, 13.40, 50);

        assertThat(near).extracting(CoachProfileResponse::getName).containsExactly("Berlin");
    }

    @Test
    void getById_throwsWhenMissing() {
        when(coachProfileRepository.findByIdWithSportTypes(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coachRecommendationService.getById(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9");

        verify(geocodingService, never()).geocode(anyString());
    }
}
