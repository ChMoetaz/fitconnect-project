import api from './client'

/**
 * Geo /nearby endpoints (JWT). Both return the same DTO shape as their list
 * counterparts (CommunityGroupResponse / CoachProfileResponse), already carrying
 * latitude/longitude, filtered to a radius around (lat, lng).
 */

// Default search radius (km) — generous so a city-scale demo still shows markers.
export const DEFAULT_RADIUS_KM = 50

export async function getNearbyGroups(lat, lng, radiusKm = DEFAULT_RADIUS_KM) {
  const { data } = await api.get('/api/community-groups/nearby', {
    params: { lat, lng, radiusKm },
  })
  return data
}

export async function getNearbyCoaches(lat, lng, radiusKm = DEFAULT_RADIUS_KM) {
  const { data } = await api.get('/api/coaches/nearby', {
    params: { lat, lng, radiusKm },
  })
  return data
}
