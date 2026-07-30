/**
 * Small localStorage wrapper for the auth session.
 * Kept in one place so the axios client, the AuthContext and the pages
 * all read/write the same keys.
 */
const TOKEN_KEY = 'fc_token'
const USER_KEY = 'fc_user'
const SPORT_KEY = 'fc_sport' // chosen sport at onboarding, reused for plan generation

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
}

/** Persisted user = { userId, email, role } (everything from AuthResponse except the token). */
export function getUser() {
  const raw = localStorage.getItem(USER_KEY)
  try {
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setUser(user) {
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getUserId() {
  return getUser()?.userId ?? null
}

/** Sport picked during onboarding — needed later by training-plan generation. */
export function getSport() {
  return localStorage.getItem(SPORT_KEY)
}

export function setSport(name) {
  if (name) localStorage.setItem(SPORT_KEY, name)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(SPORT_KEY)
}
