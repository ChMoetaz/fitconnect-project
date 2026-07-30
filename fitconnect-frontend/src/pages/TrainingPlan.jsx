import { useEffect, useState } from 'react'
import '../styles/main.css'
import { useAuth } from '../context/AuthContext'
import { getPlans, generatePlan, adaptPlan, deletePlan } from '../api/trainingPlans'
import { getSport } from '../api/storage'
import { apiErrorMessage } from '../api/client'

const asArray = (x) => (Array.isArray(x) ? x : [])
// The backend uses a few possible id field names depending on the DTO — be tolerant.
const planId = (p) => p?.id ?? p?.planId ?? p?.trainingPlanId
const sportName = (p) => p?.sportType?.name ?? p?.sportType ?? p?.sportTypeName ?? 'General fitness'

export default function TrainingPlan() {
  const { user } = useAuth()
  const [plans, setPlans] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')     // 'generate' | 'adapt' | 'delete'
  const [error, setError] = useState('')

  async function loadPlans(selectFirst = false) {
    const data = asArray(await getPlans(user.userId))
    setPlans(data)
    if (selectFirst || selectedId == null) {
      setSelectedId(data.length ? planId(data[0]) : null)
    }
    return data
  }

  useEffect(() => {
    if (!user?.userId) return
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const data = asArray(await getPlans(user.userId))
        if (cancelled) return
        setPlans(data)
        setSelectedId(data.length ? planId(data[0]) : null)
      } catch (err) {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not load your training plans.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [user])

  const selected = plans.find((p) => planId(p) === selectedId) || plans[0] || null
  const exercises = asArray(selected?.exercises)

  async function handleGenerate() {
    setBusy('generate')
    setError('')
    try {
      await generatePlan(user.userId, { sportTypeName: getSport() || sportName(selected), durationWeeks: 8 })
      const data = await loadPlans()
      // Select the newest plan (last in the list) so the user sees what they just made.
      if (data.length) setSelectedId(planId(data[data.length - 1]))
    } catch (err) {
      setError(apiErrorMessage(err, 'Plan generation failed. Check that the backend AI key is configured.'))
    } finally {
      setBusy('')
    }
  }

  async function handleAdapt() {
    if (!selected) return
    setBusy('adapt')
    setError('')
    try {
      await adaptPlan(user.userId, planId(selected))
      await loadPlans()
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not adapt the plan. Log some progress first.'))
    } finally {
      setBusy('')
    }
  }

  async function handleDelete(id) {
    setBusy('delete')
    setError('')
    try {
      await deletePlan(user.userId, id)
      const data = await getPlans(user.userId)
      const arr = asArray(data)
      setPlans(arr)
      if (id === selectedId) setSelectedId(arr.length ? planId(arr[0]) : null)
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete the plan.'))
    } finally {
      setBusy('')
    }
  }

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <div className="page-title">Your Training Plan</div>
          <div className="page-subtitle">
            {selected
              ? `AI-generated · ${sportName(selected)}${selected.duration ? ` · ${selected.duration} weeks` : ''}`
              : 'AI-powered personalized plans'}
          </div>
        </div>
        <span className="ai-badge">✨ AI-powered</span>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>
          {error}
        </div>
      )}

      {loading ? (
        <div className="card"><div className="empty-state"><p>Loading your plans…</p></div></div>
      ) : !selected ? (
        <div className="card">
          <div className="empty-state">
            <p style={{ fontSize: 15, fontWeight: 600, color: 'var(--text-primary)' }}>No training plan yet</p>
            <p>Generate your first AI-powered plan based on your onboarding profile.</p>
            <button
              className="btn btn-primary"
              style={{ marginTop: 16 }}
              onClick={handleGenerate}
              disabled={busy === 'generate'}
            >
              {busy === 'generate' ? '✨ Generating…' : '✨ Generate my plan'}
            </button>
          </div>
        </div>
      ) : (
        <>
          {/* Selected plan detail */}
          <div className="card">
            <div className="card-title" style={{ justifyContent: 'space-between' }}>
              <span>💪 {selected.title || 'Training plan'}</span>
              <span className="badge badge-active">{sportName(selected)}</span>
            </div>

            {selected.description && (
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 14, lineHeight: 1.6 }}>
                {selected.description}
              </p>
            )}

            {exercises.length ? (
              exercises.map((ex, i) => (
                <div className="exercise-row" key={i}>
                  <div className="ex-num">{i + 1}</div>
                  <div style={{ flex: 1, fontWeight: 600, fontSize: 13 }}>{ex.name}</div>
                  <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    {ex.sets} × {ex.repetitions} reps
                  </div>
                </div>
              ))
            ) : (
              <div className="empty-state"><p>This plan has no exercises.</p></div>
            )}

            <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
              <button className="btn btn-primary btn-sm" onClick={handleAdapt} disabled={!!busy}>
                {busy === 'adapt' ? '✨ Adapting…' : '↻ Adapt to my progress'}
              </button>
              <button className="btn btn-sm" onClick={handleGenerate} disabled={!!busy}>
                {busy === 'generate' ? '✨ Generating…' : '＋ Generate new plan'}
              </button>
              <button
                className="btn btn-sm"
                onClick={() => handleDelete(planId(selected))}
                disabled={!!busy}
                style={{ color: '#dc2626' }}
              >
                🗑 Delete
              </button>
            </div>
          </div>

          {/* All plans */}
          <div className="card">
            <div className="card-title">📋 All your plans</div>
            {plans.map((p) => {
              const id = planId(p)
              const isActive = id === selectedId
              return (
                <div className="row-item" key={id}>
                  <div
                    onClick={() => setSelectedId(id)}
                    style={{ cursor: 'pointer', flex: 1 }}
                  >
                    <div style={{ fontWeight: 600, fontSize: 13, color: isActive ? 'var(--brand-dark)' : 'var(--text-primary)' }}>
                      {isActive ? '● ' : ''}{p.title || 'Untitled plan'}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                      {sportName(p)} · {asArray(p.exercises).length} exercises{p.duration ? ` · ${p.duration} weeks` : ''}
                    </div>
                  </div>
                  <button
                    className="btn btn-sm"
                    onClick={() => handleDelete(id)}
                    disabled={!!busy}
                    style={{ color: '#dc2626' }}
                  >
                    Delete
                  </button>
                </div>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
