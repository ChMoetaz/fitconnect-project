import api from './client'

/** Community group endpoints (JWT). Event endpoints live in api/events.js. */

export async function getGroups() {
  const { data } = await api.get('/api/community-groups')
  return data
}

export async function createGroup(payload) {
  const { data } = await api.post('/api/community-groups', payload)
  return data
}

export async function joinGroup(groupId, userId) {
  const { data } = await api.post(`/api/community-groups/${groupId}/join`, null, {
    params: { userId },
  })
  return data
}

export async function leaveGroup(groupId, userId) {
  const { data } = await api.post(`/api/community-groups/${groupId}/leave`, null, {
    params: { userId },
  })
  return data
}
