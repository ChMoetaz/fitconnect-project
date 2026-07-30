import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import '../styles/main.css'

const navItems = [
  { to: '/',           icon: '🏠', label: 'Dashboard'     },
  { to: '/plan',       icon: '🏋️', label: 'Training Plan' },
  { to: '/coaches',    icon: '👥', label: 'Coaches'       },
  { to: '/community',  icon: '🤝', label: 'Community'     },
  { to: '/onboarding', icon: '⚙️', label: 'Onboarding'   },
]

const adminItems = [
  { to: '/admin/users',       icon: '👤', label: 'Manage Users'       },
  { to: '/admin/communities', icon: '🤝', label: 'Manage Communities' },
  { to: '/admin/coaches',     icon: '👥', label: 'Manage Coaches'      },
]

// Shared NavLink style (mirrors the existing MAIN/ACCOUNT links).
const navLinkStyle = ({ isActive }) => ({
  display: 'flex', alignItems: 'center', gap: 10,
  padding: '9px 12px', borderRadius: 10, marginBottom: 2,
  fontSize: 13, fontWeight: isActive ? 600 : 400,
  color: isActive ? 'var(--brand-dark)' : 'var(--text-secondary)',
  background: isActive ? 'var(--brand-light)' : 'transparent',
  textDecoration: 'none', transition: 'all 0.15s',
})

const sectionLabelStyle = {
  fontSize: 10, fontWeight: 700, color: 'var(--text-muted)',
  padding: '10px 10px 6px', letterSpacing: '0.08em', textTransform: 'uppercase',
}

export default function Sidebar() {
  const { user, isAdmin, logout } = useAuth()
  const navigate = useNavigate()

  const email = user?.email || ''
  const initials = email ? email.slice(0, 2).toUpperCase() : 'FC'
  const roleLabel = user?.role ? String(user.role).toLowerCase() : 'member'

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <aside style={{
      width: 210, flexShrink: 0,
      background: '#fff',
      borderRight: '1px solid var(--border)',
      display: 'flex', flexDirection: 'column',
      height: '100vh',
    }}>
      {/* Logo */}
      <div style={{ padding: '22px 20px 18px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--brand-dark)' }}>
          Fit<span style={{ color: 'var(--brand)' }}>Connect</span>
        </div>
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, padding: '12px 10px' }}>
        <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', padding: '0 10px', marginBottom: 6, letterSpacing: '0.08em', textTransform: 'uppercase' }}>
          Main
        </div>
        {navItems.slice(0, 4).map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '9px 12px', borderRadius: 10, marginBottom: 2,
              fontSize: 13, fontWeight: isActive ? 600 : 400,
              color: isActive ? 'var(--brand-dark)' : 'var(--text-secondary)',
              background: isActive ? 'var(--brand-light)' : 'transparent',
              textDecoration: 'none', transition: 'all 0.15s',
            })}
          >
            <span>{item.icon}</span>
            {item.label}
          </NavLink>
        ))}

        <div style={{ fontSize: 10, fontWeight: 700, color: 'var(--text-muted)', padding: '10px 10px 6px', letterSpacing: '0.08em', textTransform: 'uppercase' }}>
          Account
        </div>
        <NavLink
          to="/onboarding"
          style={({ isActive }) => ({
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '9px 12px', borderRadius: 10,
            fontSize: 13, fontWeight: isActive ? 600 : 400,
            color: isActive ? 'var(--brand-dark)' : 'var(--text-secondary)',
            background: isActive ? 'var(--brand-light)' : 'transparent',
            textDecoration: 'none', transition: 'all 0.15s',
          })}
        >
          <span>⚙️</span> Onboarding
        </NavLink>

        {isAdmin && (
          <>
            <div style={sectionLabelStyle}>Admin</div>
            {adminItems.map((item) => (
              <NavLink key={item.to} to={item.to} style={navLinkStyle}>
                <span>{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </>
        )}
      </nav>

      {/* User chip */}
      <div style={{ padding: '12px 10px', borderTop: '1px solid var(--border)' }}>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '8px 12px', borderRadius: 10,
        }}>
          <div className="avatar">{initials}</div>
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{
              fontSize: 13, fontWeight: 600,
              whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
            }}>
              {email || 'Not signed in'}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'capitalize' }}>
              {roleLabel}
            </div>
          </div>
        </div>
        <button
          onClick={handleLogout}
          className="btn btn-sm"
          style={{ width: '100%', justifyContent: 'center', marginTop: 6 }}
        >
          ⎋ Log out
        </button>
      </div>
    </aside>
  )
}