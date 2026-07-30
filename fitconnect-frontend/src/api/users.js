import api from './client'

/** User + onboarding endpoints (JWT + self: userId must match the token). */

export async function getUser(userId) {
  const { data } = await api.get(`/api/users/${userId}`)
  return data
}

/**
 * Submit onboarding answers.
 * Backend expects: { fitnessGoal, fitnessLevel, trainingFrequency, sportTypeName }.
 * (location / equipment are frontend-only and are NOT sent.)
 */
export async function submitOnboarding(userId, payload) {
  const { data } = await api.post(`/api/users/${userId}/onboarding`, payload)
  return data
}
