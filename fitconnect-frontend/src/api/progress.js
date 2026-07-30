import api from './client'

/** Progress endpoints (JWT + self). POST also auto-awards achievements. */

export async function getProgress(userId) {
  const { data } = await api.get(`/api/users/${userId}/progress`)
  return data
}

/** payload: { date, completedWorkouts, notes } */
export async function addProgress(userId, payload) {
  const { data } = await api.post(`/api/users/${userId}/progress`, payload)
  return data
}
