import axios from 'axios'
import { getToken, clearSession } from './storage'

/**
 * Centralized HTTP client for the FitConnect backend.
 *
 * - baseURL comes from VITE_API_BASE_URL (see .env), falls back to localhost:8080.
 * - Request interceptor injects `Authorization: Bearer <token>` when we have one.
 * - Response interceptor catches 401 (missing/expired token) → clears the session
 *   and redirects to /login.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    // 401 = no/expired token. Don't loop if we're already on the login page,
    // and don't hijack the login/register calls themselves (bad credentials).
    const url = error.config?.url || ''
    const isAuthCall = url.includes('/users/login') || url.includes('/users/register')
    if (status === 401 && !isAuthCall && window.location.pathname !== '/login') {
      clearSession()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

/**
 * Normalizes an axios error into a readable message.
 * The backend's GlobalExceptionHandler returns an ApiError JSON; we surface its
 * `message` when present, otherwise fall back to something sensible.
 */
export function apiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  return (
    error?.response?.data?.message ||
    error?.response?.data?.error ||
    error?.message ||
    fallback
  )
}

export default api
