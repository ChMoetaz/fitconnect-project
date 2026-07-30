import { useEffect, useState } from 'react'
import '../styles/main.css'
import { getCoaches, createCoach } from '../api/coaches'
import { updateCoach, deleteCoach } from '../api/admin'
import { apiErrorMessage } from '../api/client'

const asArray = (x) => (Array.isArray(x) ? x : [])
const coachId = (c) => c?.coachId ?? c?.id
const sportNames = (c) => asArray(c?.sportTypes).map((s) => s?.name ?? s).filter(Boolean)
const emptyCreate = { name: '', specialization: '', experienceYears: '', location: '', sportTypeNames: '' }

export default function AdminCoaches() {
  const [coaches, setCoaches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ name: '', specialization: '', experienceYears: '', location: '' })
  const [saving, setSaving] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [creating, setCreating] = useState(false)

  async function load() {
    setLoading(true)
    setError('')
    try {
      setCoaches(asArray(await getCoaches()))
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load coaches.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { ;(async () => { await load() })() }, [])

  function startEdit(c) {
    setEditingId(coachId(c))
    setForm({
      name: c.name || '',
      specialization: c.specialization || '',
      experienceYears: c.experienceYears != null ? String(c.experienceYears) : '',
      location: c.location || '',
    })
  }

  async function handleSave(c) {
    const id = coachId(c)
    if (id == null || !form.name.trim() || saving) return
    setSaving(true)
    setError('')
    try {
      await updateCoach(id, {
        name: form.name.trim(),
        specialization: form.specialization,
        experienceYears: form.experienceYears === '' ? null : Number(form.experienceYears),
        location: form.location,
        // Preserve existing sports (the minimal form doesn't edit them).
        sportTypeNames: sportNames(c),
      })
      setEditingId(null)
      await load() // refetch for the canonical shape
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not update the coach.'))
    } finally {
      setSaving(false)
    }
  }

  async function handleCreate() {
    if (!createForm.name.trim() || creating) return
    setCreating(true)
    setError('')
    try {
      const created = await createCoach({
        name: createForm.name.trim(),
        specialization: createForm.specialization,
        experienceYears: createForm.experienceYears === '' ? null : Number(createForm.experienceYears),
        location: createForm.location,
        sportTypeNames: createForm.sportTypeNames.split(',').map((s) => s.trim()).filter(Boolean),
      })
      setCoaches((prev) => [created, ...prev]) // show immediately, no reload
      setCreateForm(emptyCreate)
      setShowCreate(false)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not create the coach.'))
    } finally {
      setCreating(false)
    }
  }

  async function handleDelete(c) {
    const id = coachId(c)
    if (id == null) return
    if (!window.confirm(`Delete coach "${c.name}"?`)) return
    setError('')
    const snapshot = coaches
    setCoaches((prev) => prev.filter((x) => coachId(x) !== id)) // optimistic
    try {
      await deleteCoach(id)
    } catch (err) {
      setCoaches(snapshot) // rollback
      setError(apiErrorMessage(err, 'Could not delete the coach.'))
    }
  }

  const inputStyle = { padding: '6px 8px', fontSize: 12 }

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">Manage Coaches</div>
          <div className="page-subtitle">Admin · edit or remove coaches</div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreate((s) => !s)}>
          {showCreate ? 'Cancel' : '+ Create coach'}
        </button>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>{error}</div>
      )}

      {showCreate && (
        <div className="card" style={{ marginBottom: 16 }}>
          <div className="card-title">New coach</div>
          <div style={{ display: 'grid', gap: 8 }}>
            <input className="input" style={inputStyle} placeholder="Name *"
              value={createForm.name} onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))} />
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <input className="input" style={{ ...inputStyle, flex: 1, minWidth: 160 }} placeholder="Specialization"
                value={createForm.specialization} onChange={(e) => setCreateForm((f) => ({ ...f, specialization: e.target.value }))} />
              <input className="input" style={{ ...inputStyle, width: 120 }} type="number" placeholder="Years"
                value={createForm.experienceYears} onChange={(e) => setCreateForm((f) => ({ ...f, experienceYears: e.target.value }))} />
            </div>
            <input className="input" style={inputStyle} placeholder="Location"
              value={createForm.location} onChange={(e) => setCreateForm((f) => ({ ...f, location: e.target.value }))} />
            <input className="input" style={inputStyle} placeholder="Sport types (comma-separated, e.g. Running, HIIT)"
              value={createForm.sportTypeNames} onChange={(e) => setCreateForm((f) => ({ ...f, sportTypeNames: e.target.value }))} />
            <div>
              <button className="btn btn-primary btn-sm" onClick={handleCreate} disabled={creating || !createForm.name.trim()}>
                {creating ? 'Creating…' : 'Create coach'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty-state"><p>Loading coaches…</p></div>
        ) : coaches.length ? (
          coaches.map((c) => {
            const id = coachId(c)
            const editing = editingId === id
            const sub = [c.specialization || null, c.location || null].filter(Boolean).join(' · ')
            return (
              <div key={id ?? c.name} style={{ borderBottom: '1px solid #f3f4f6' }}>
                <div className="row-item" style={{ borderBottom: 'none' }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{c.name}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{sub || '—'}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="btn btn-sm" onClick={() => (editing ? setEditingId(null) : startEdit(c))}>
                      {editing ? 'Cancel' : 'Edit'}
                    </button>
                    <button className="btn btn-sm btn-danger" onClick={() => handleDelete(c)}>Delete</button>
                  </div>
                </div>
                {editing && (
                  <div style={{ display: 'grid', gap: 8, padding: '4px 0 14px' }}>
                    <input className="input" style={inputStyle} placeholder="Name *"
                      value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <input className="input" style={{ ...inputStyle, flex: 1, minWidth: 160 }} placeholder="Specialization"
                        value={form.specialization} onChange={(e) => setForm((f) => ({ ...f, specialization: e.target.value }))} />
                      <input className="input" style={{ ...inputStyle, width: 120 }} type="number" placeholder="Years"
                        value={form.experienceYears} onChange={(e) => setForm((f) => ({ ...f, experienceYears: e.target.value }))} />
                    </div>
                    <input className="input" style={inputStyle} placeholder="Location"
                      value={form.location} onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))} />
                    <div>
                      <button className="btn btn-primary btn-sm" onClick={() => handleSave(c)} disabled={saving || !form.name.trim()}>
                        {saving ? 'Saving…' : 'Save'}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })
        ) : (
          <div className="empty-state"><p>No coaches.</p></div>
        )}
      </div>
    </div>
  )
}
