import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { apiErrorMessage } from '../api/client'
import '../styles/main.css'

/**
 * Combined Login / Register screen. Same visual language as the Onboarding page
 * (centered brand card on a soft green gradient). Toggles between the two modes.
 */
export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, register } = useAuth()

  const [mode, setMode] = useState('login') // 'login' | 'register'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const isRegister = mode === 'register'
  const from = location.state?.from?.pathname || '/'

  async function handleSubmit(e) {
    e.preventDefault()
    if (!email || !password) return
    setLoading(true)
    setError('')
    try {
      if (isRegister) {
        await register(email, password)
        // Fresh account → straight to onboarding to build the profile.
        navigate('/onboarding', { replace: true })
      } else {
        await login(email, password)
        navigate(from, { replace: true })
      }
    } catch (err) {
      setError(apiErrorMessage(err, isRegister ? 'Registration failed.' : 'Invalid email or password.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      minHeight: '100vh', display: 'flex', alignItems: 'center',
      justifyContent: 'center', padding: '24px',
      background: 'linear-gradient(135deg, #f0fdf8 0%, #ffffff 60%)',
    }}>
      <div style={{ width: '100%', maxWidth: '400px' }}>
        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div style={{ fontSize: '26px', fontWeight: '700', color: '#0F6E56', marginBottom: '6px' }}>
            Fit<span style={{ color: '#1D9E75' }}>Connect</span>
          </div>
          <div style={{ fontSize: '13px', color: '#6b7280' }}>
            {isRegister ? 'Create your account to get started' : 'Welcome back — sign in to continue'}
          </div>
        </div>

        {/* Card */}
        <div style={{
          background: '#fff', borderRadius: '20px',
          boxShadow: '0 4px 24px rgba(0,0,0,0.07)', padding: '32px',
        }}>
          <form onSubmit={handleSubmit}>
            <div style={{ marginBottom: '16px' }}>
              <label className="input-label">Email</label>
              <input
                className="input"
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>

            <div style={{ marginBottom: '20px' }}>
              <label className="input-label">Password</label>
              <input
                className="input"
                type="password"
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            {error && (
              <div style={{
                background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
                borderRadius: '10px', padding: '10px 12px', fontSize: '12px', marginBottom: '16px',
              }}>
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading || !email || !password}
              style={{
                width: '100%', padding: '13px 24px', borderRadius: '10px',
                cursor: loading || !email || !password ? 'not-allowed' : 'pointer',
                border: 'none',
                background: loading || !email || !password ? '#d1d5db' : '#1D9E75',
                color: '#fff', fontSize: '14px', fontWeight: '600',
                transition: 'all 0.2s',
              }}
            >
              {loading
                ? (isRegister ? 'Creating account…' : 'Signing in…')
                : (isRegister ? 'Create account' : 'Sign in')}
            </button>
          </form>

          {/* Mode toggle */}
          <div style={{ textAlign: 'center', marginTop: '18px', fontSize: '13px', color: '#6b7280' }}>
            {isRegister ? 'Already have an account?' : "Don't have an account?"}{' '}
            <button
              type="button"
              onClick={() => { setMode(isRegister ? 'login' : 'register'); setError('') }}
              style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: '#1D9E75', fontWeight: '600', fontSize: '13px', padding: 0,
              }}
            >
              {isRegister ? 'Sign in' : 'Create one'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
