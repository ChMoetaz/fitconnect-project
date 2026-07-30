import api from './client'

/**
 * AI-personalized recommendation endpoints (JWT + self). These are ADD-ONS to the classic
 * catalogue endpoints (getCoaches / getGroups / /nearby), which stay untouched.
 *
 * Both require the user to have completed onboarding; otherwise the backend returns 400 with a
 * clear message — callers should treat that as "no recommendations yet", not a blocking error.
 *
 * Shapes:
 *   coaches → [{ coach: CoachProfileResponse, reason: string }]
 *   groups  → [{ group: CommunityGroupResponse, reason: string }]
 */

export async function getRecommendedCoaches(userId) {
  const { data } = await api.get(`/api/users/${userId}/coaches/recommended`)
  return data
}

export async function getRecommendedGroups(userId) {
  const { data } = await api.get(`/api/users/${userId}/community-groups/recommended`)
  return data
}
