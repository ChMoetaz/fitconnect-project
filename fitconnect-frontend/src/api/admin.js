import api from './client'

/**
 * Admin-only endpoints (JWT + ADMIN role — backend enforces via CurrentUser.requireAdmin()).
 * Purely additive: none of the classic endpoints change.
 *
 * DTO shapes:
 *   users     → [{ userId, email, role }]  (UserResponse)
 *   role body → { role: "USER" | "ADMIN" | "COACH" }
 *   coach PUT → CoachProfileRequest { name*, specialization, experienceYears, location, sportTypeNames[] }
 *   group PUT → CommunityGroupRequest { name*, description, location, sportTypeName }
 */

// ── Users ──
export async function getUsers() {
  const { data } = await api.get('/api/admin/users')
  return data
}

export async function updateUserRole(userId, role) {
  const { data } = await api.patch(`/api/admin/users/${userId}/role`, { role })
  return data
}

export async function deleteUser(userId) {
  await api.delete(`/api/admin/users/${userId}`)
}

// ── Coaches (admin) ──
export async function updateCoach(coachId, payload) {
  const { data } = await api.put(`/api/coaches/${coachId}`, payload)
  return data
}

export async function deleteCoach(coachId) {
  await api.delete(`/api/coaches/${coachId}`)
}

// ── Community groups (admin) ──
export async function updateGroup(communityId, payload) {
  const { data } = await api.put(`/api/community-groups/${communityId}`, payload)
  return data
}

export async function deleteGroup(communityId) {
  await api.delete(`/api/community-groups/${communityId}`)
}
