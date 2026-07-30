import { useEffect, useState } from 'react'
import '../styles/main.css'
import { getUsers, updateUserRole, deleteUser } from '../api/admin'
import { apiErrorMessage } from '../api/client'

const asArray = (x) => (Array.isArray(x) ? x : [])
const userId = (u) => u?.userId ?? u?.id
const ROLES = ['USER', 'ADMIN', 'COACH']

export default function AdminUsers() {
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      setUsers(asArray(await getUsers()))
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load users.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { ;(async () => { await load() })() }, [])

  function patchUser(id, patch) {
    setUsers((prev) => prev.map((u) => (userId(u) === id ? { ...u, ...patch } : u)))
  }

  async function handleRoleChange(u, role) {
    const id = userId(u)
    const prev = u.role
    if (id == null || role === prev) return
    setError('')
    patchUser(id, { role }) // optimistic
    try {
      const updated = await updateUserRole(id, role)
      if (updated?.role) patchUser(id, { role: updated.role })
    } catch (err) {
      patchUser(id, { role: prev }) // rollback
      setError(apiErrorMessage(err, 'Could not update role.'))
    }
  }

  async function handleDelete(u) {
    const id = userId(u)
    if (id == null) return
    if (!window.confirm(`Delete user ${u.email}?`)) return
    setError('')
    const snapshot = users
    setUsers((prev) => prev.filter((x) => userId(x) !== id)) // optimistic
    try {
      await deleteUser(id)
    } catch (err) {
      setUsers(snapshot) // rollback
      setError(apiErrorMessage(err, 'Could not delete user.'))
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="page-title">Manage Users</div>
        <div className="page-subtitle">Admin · change roles and remove accounts</div>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>{error}</div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty-state"><p>Loading users…</p></div>
        ) : users.length ? (
          users.map((u) => (
            <div className="row-item" key={userId(u) ?? u.email}>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis' }}>{u.email}</div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>ID {userId(u)}</div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <select
                  className="input"
                  value={ROLES.includes(u.role) ? u.role : 'USER'}
                  onChange={(e) => handleRoleChange(u, e.target.value)}
                  style={{ padding: '5px 8px', fontSize: 12 }}
                >
                  {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
                <button className="btn btn-sm btn-danger" onClick={() => handleDelete(u)}>Delete</button>
              </div>
            </div>
          ))
        ) : (
          <div className="empty-state"><p>No users.</p></div>
        )}
      </div>
    </div>
  )
}
