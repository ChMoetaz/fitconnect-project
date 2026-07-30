import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import AdminRoute from './components/AdminRoute'
import Sidebar from './components/Sidebar'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import TrainingPlan from './pages/TrainingPlan'
import Coaches from './pages/Coaches'
import Community from './pages/Community'
import Onboarding from './pages/Onboarding'
import AdminUsers from './pages/AdminUsers'
import AdminCommunities from './pages/AdminCommunities'
import AdminCoaches from './pages/AdminCoaches'

/** The authenticated app shell: sidebar + the private pages. */
function AppLayout() {
  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar />
      <main style={{ flex: 1, overflowY: 'auto', padding: '32px', background: '#f9fafb' }}>
        <Routes>
          <Route path="/"           element={<Dashboard />}    />
          <Route path="/plan"       element={<TrainingPlan />} />
          <Route path="/coaches"    element={<Coaches />}      />
          <Route path="/community"  element={<Community />}    />
          <Route path="/onboarding" element={<Onboarding />}   />
          <Route path="/admin/users"       element={<AdminRoute><AdminUsers /></AdminRoute>} />
          <Route path="/admin/communities" element={<AdminRoute><AdminCommunities /></AdminRoute>} />
          <Route path="/admin/coaches"      element={<AdminRoute><AdminCoaches /></AdminRoute>} />
        </Routes>
      </main>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/*"
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
