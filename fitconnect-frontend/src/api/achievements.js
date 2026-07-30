import api from './client'

/** Achievement endpoints. */

/** Full catalogue of badge definitions (JWT). */
export async function getCatalogue() {
  const { data } = await api.get('/api/achievements')
  return data
}

/** Badges earned by the user, with earnedAt (JWT + self). */
export async function getUserAchievements(userId) {
  const { data } = await api.get(`/api/users/${userId}/achievements`)
  return data
}
