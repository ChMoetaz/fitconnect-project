import { createContext, useContext, useState, useCallback } from 'react'
import * as authApi from '../api/auth'
import { getUser, setUser, setToken, clearSession } from '../api/storage'

/**
 * Auth state for the whole app. Backed by localStorage (see api/storage.js) so a
 * refresh keeps the user logged in until the 24h JWT expires (backend has no
 * refresh token — on expiry the 401 interceptor sends them back to /login).
 */
const AuthContext = createContext(null)

/**
 * The backend's AuthResponse may expose the user id as `userId` or `id`
 * depending on the DTO. Normalize to a guaranteed `userId` so every
 * `/api/users/{userId}/...` call reads the same field.
 */
function normalizeUser(userInfo) {
  if (!userInfo) return null
  return { ...userInfo, userId: userInfo.userId ?? userInfo.id }
}

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(() => normalizeUser(getUser())) // { userId, email, role }

  const persist = useCallback((authResponse) => {
    const { accessToken, ...rest } = authResponse
    const userInfo = normalizeUser(rest)
    setToken(accessToken)
    setUser(userInfo)
    setUserState(userInfo)
    return userInfo
  }, [])

  const login = useCallback(async (email, password) => {
    return persist(await authApi.login(email, password))
  }, [persist])

  const register = useCallback(async (email, password) => {
    return persist(await authApi.register(email, password))
  }, [persist])

  const logout = useCallback(() => {
    clearSession()
    setUserState(null)
  }, [])

  const value = {
    user,
    isAuthenticated: !!user,
    isAdmin: user?.role === 'ADMIN',
    login,
    register,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
