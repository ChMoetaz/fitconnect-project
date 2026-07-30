import api from './client'

/**
 * Fitness event endpoints, nested under a community group (JWT).
 * Mirrors the backend EventController: /api/community-groups/{groupId}/events...
 * register/unregister return the updated EventResponse (attendeeCount + attendeeIds).
 */

export async function getEvents(groupId) {
  const { data } = await api.get(`/api/community-groups/${groupId}/events`)
  return data
}

export async function createEvent(groupId, payload) {
  const { data } = await api.post(`/api/community-groups/${groupId}/events`, payload)
  return data
}

export async function getEvent(groupId, eventId) {
  const { data } = await api.get(`/api/community-groups/${groupId}/events/${eventId}`)
  return data
}

export async function deleteEvent(groupId, eventId) {
  const { data } = await api.delete(`/api/community-groups/${groupId}/events/${eventId}`)
  return data
}

export async function registerEvent(groupId, eventId, userId) {
  const { data } = await api.post(
    `/api/community-groups/${groupId}/events/${eventId}/register`,
    null,
    { params: { userId } },
  )
  return data
}

export async function unregisterEvent(groupId, eventId, userId) {
  const { data } = await api.post(
    `/api/community-groups/${groupId}/events/${eventId}/unregister`,
    null,
    { params: { userId } },
  )
  return data
}
