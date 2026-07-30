import { useState } from 'react'
import '../styles/main.css'
import { createGroup } from '../api/community'
import { apiErrorMessage } from '../api/client'

const emptyForm = { name: '', description: '', location: '', sportTypeName: '' }
const inputStyle = { padding: '6px 8px', fontSize: 12 }

/**
 * Reusable "create a community group" form (POST /api/community-groups). Extracted verbatim from the
 * admin page (AdminCommunities) so the admin panel and the normal Community page share the exact same
 * fields, style and submit logic. Calls onCreated(created) with the backend response so each page can
 * update its own local list however it wants.
 */
export default function CommunityGroupForm({ onCreated }) {
  const [form, setForm] = useState(emptyForm)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')

  async function handleCreate() {
    if (!form.name.trim() || creating) return
    setCreating(true)
    setError('')
    try {
      const created = await createGroup({
        name: form.name.trim(),
        description: form.description,
        location: form.location,
        sportTypeName: form.sportTypeName,
      })
      setForm(emptyForm)
      onCreated?.(created)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not create the group.'))
    } finally {
      setCreating(false)
    }
  }

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title">New community</div>
      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 12,
        }}>{error}</div>
      )}
      <div style={{ display: 'grid', gap: 8 }}>
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
          <button className="btn btn-primary btn-sm" onClick={handleCreate} disabled={creating || !form.name.trim()}>
            {creating ? 'Creating…' : 'Create community'}
          </button>
        </div>
      </div>
    </div>
  )
}
