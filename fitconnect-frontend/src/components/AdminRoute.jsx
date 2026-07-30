import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * Guards the admin pages. Assumes it renders inside ProtectedRoute (so the user is already
 * authenticated); if they are not an ADMIN, bounce to the dashboard rather than showing the page.
 */
export default function AdminRoute({ children }) {
  const { isAdmin } = useAuth()
  if (!isAdmin) return <Navigate to="/" replace />
  return children
}
