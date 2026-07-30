import api from './client'

/** Training plan endpoints (JWT + self). */

export async function getPlans(userId) {
  const { data } = await api.get(`/api/users/${userId}/training-plans`)
  return data
}

/**
 * Generate a plan via Gemini.
 * Body is optional: { sportTypeName (default "General fitness"), durationWeeks (default 8) }.
 */
export async function generatePlan(userId, { sportTypeName, durationWeeks } = {}) {
  const body = {}
  if (sportTypeName) body.sportTypeName = sportTypeName
  if (durationWeeks) body.durationWeeks = durationWeeks
  const { data } = await api.post(`/api/users/${userId}/training-plans`, body)
  return data
}

/** Re-tune an existing plan from real progress (needs at least one progress record). */
export async function adaptPlan(userId, planId) {
  const { data } = await api.post(`/api/users/${userId}/training-plans/${planId}/adapt`)
  return data
}

export async function deletePlan(userId, planId) {
  await api.delete(`/api/users/${userId}/training-plans/${planId}`)
}
