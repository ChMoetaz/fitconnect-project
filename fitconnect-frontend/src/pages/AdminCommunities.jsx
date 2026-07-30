import { useEffect, useState } from 'react'
import '../styles/main.css'
import { getGroups } from '../api/community'
import { updateGroup, deleteGroup } from '../api/admin'
import { apiErrorMessage } from '../api/client'
import CommunityGroupForm from '../components/CommunityGroupForm'

const asArray = (x) => (Array.isArray(x) ? x : [])
const groupId = (g) => g?.communityId ?? g?.id ?? g?.groupId
const memberCount = (g) => g?.memberCount ?? (Array.isArray(g?.members) ? g.members.length : null)
const groupSport = (g) => g?.sportTypeName ?? g?.sportType?.name ?? ''

export default function AdminCommunities() {
  const [groups, setGroups] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ name: '', description: '', location: '', sportTypeName: '' })
  const [saving, setSaving] = useState(false)
  const [showCreate, setShowCreate] = useState(false)

  async function load() {
    setLoading(true)
    setError('')
    try {
      setGroups(asArray(await getGroups()))
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load groups.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { ;(async () => { await load() })() }, [])

  function startEdit(g) {
    setEditingId(groupId(g))
    setForm({
      name: g.name || '',
      description: g.description || '',
      location: g.location || '',
      sportTypeName: groupSport(g),
    })
  }

  async function handleSave(g) {
    const id = groupId(g)
    if (id == null || !form.name.trim() || saving) return
    setSaving(true)
    setError('')
    try {
      await updateGroup(id, {
        name: form.name.trim(),
        description: form.description,
        location: form.location,
        sportTypeName: form.sportTypeName,
      })
      setEditingId(null)
      await load() // refetch: PUT returns the raw entity (no memberCount) — reload for the full shape
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not update the group.'))
    } finally {
      setSaving(false)
    }
  }

  function handleGroupCreated(created) {
    // POST returns the raw entity (no memberCount) — show it now with a sensible 0.
    setGroups((prev) => [{ ...created, memberCount: 0 }, ...prev])
    setShowCreate(false)
  }

  async function handleDelete(g) {
    const id = groupId(g)
    if (id == null) return
    if (!window.confirm(`Delete group "${g.name}"?`)) return
    setError('')
    const snapshot = groups
    setGroups((prev) => prev.filter((x) => groupId(x) !== id)) // optimistic
    try {
      await deleteGroup(id)
    } catch (err) {
      setGroups(snapshot) // rollback
      setError(apiErrorMessage(err, 'Could not delete the group.'))
    }
  }

  const inputStyle = { padding: '6px 8px', fontSize: 12 }

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">Manage Communities</div>
          <div className="page-subtitle">Admin · edit or remove groups</div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreate((s) => !s)}>
          {showCreate ? 'Cancel' : '+ Create community'}
        </button>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>{error}</div>
      )}

      {showCreate && <CommunityGroupForm onCreated={handleGroupCreated} />}

      <div className="card">
        {loading ? (
          <div className="empty-state"><p>Loading groups…</p></div>
        ) : groups.length ? (
          groups.map((g) => {
            const id = groupId(g)
            const editing = editingId === id
            const count = memberCount(g)
            const sub = [g.location || null, count != null ? `${count} members` : null].filter(Boolean).join(' · ')
            return (
              <div key={id ?? g.name} style={{ borderBottom: '1px solid #f3f4f6' }}>
                <div className="row-item" style={{ borderBottom: 'none' }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{g.name}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{sub || '—'}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="btn btn-sm" onClick={() => (editing ? setEditingId(null) : startEdit(g))}>
                      {editing ? 'Cancel' : 'Edit'}
                    </button>
                    <button className="btn btn-sm btn-danger" onClick={() => handleDelete(g)}>Delete</button>
                  </div>
                </div>
                {editing && (
                  <div style={{ display: 'grid', gap: 8, padding: '4px 0 14px' }}>
                    <input className="input" style={inputStyle} placeholder="Name *"
                      value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
                    <input className="input" style={inputStyle} placeholder="Description"
                      value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <input className="input" style={{ ...inputStyle, flex: 1, minWidth: 160 }} placeholder="Location"
                        value={form.location} onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))} />
                      <input className="input" style={{ ...inputStyle, flex: 1, minWidth: 160 }} placeholder="Sport type"
                        value={form.sportTypeName} onChange={(e) => setForm((f) => ({ ...f, sportTypeName: e.target.value }))} />
                    </div>
                    <div>
                      <button className="btn btn-primary btn-sm" onClick={() => handleSave(g)} disabled={saving || !form.name.trim()}>
                        {saving ? 'Saving…' : 'Save'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })
        ) : (
          <div className="empty-state"><p>No groups.</p></div>
        )}
      </div>
    </div>
  )
}
