import api from './client'

/** Coach endpoints (JWT — any authenticated user). */

export async function getCoaches() {
  const { data } = await api.get('/api/coaches')
  return data
}

export async function recommendCoaches(sportTypeId) {
  const { data } = await api.get('/api/coaches/recommend', { params: { sportTypeId } })
  return data
}

/** Create a coach (POST /api/coaches). Payload = CoachProfileRequest. Admin-facing use. */
export async function createCoach(payload) {
  const { data } = await api.post('/api/coaches', payload)
  return data
}
