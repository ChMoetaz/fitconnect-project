import api from './client'

/**
 * Auth endpoints (public — no token required).
 * Both return an AuthResponse: { userId, email, role, accessToken }.
 */

export async function login(email, password) {
  const { data } = await api.post('/api/users/login', { email, password })
  return data
}

export async function register(email, password) {
  const { data } = await api.post('/api/users/register', { email, password })
  return data
}
