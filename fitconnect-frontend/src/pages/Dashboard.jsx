import { useCallback, useEffect, useState } from 'react'
import '../styles/main.css'
import StatCard from '../components/StatCard'
import WorkoutCard from '../components/WorkoutCard'
import { useAuth } from '../context/AuthContext'
import { getProgress, addProgress } from '../api/progress'
import { getPlans } from '../api/trainingPlans'
import { getCatalogue, getUserAchievements } from '../api/achievements'
import { apiErrorMessage } from '../api/client'

const asArray = (x) => (Array.isArray(x) ? x : [])

/** Formats an ISO date (e.g. "2026-07-25") into a weekday label, with a safe fallback. */
function formatDay(dateStr) {
  const d = new Date(dateStr)
  return isNaN(d.getTime())
    ? (dateStr || '—')
    : d.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' })
}

/** Today as YYYY-MM-DD. Only call from event handlers (Date is impure — keep it out of render). */
function todayISO() {
  return new Date().toISOString().slice(0, 10)
}

export default function Dashboard() {
  const { user } = useAuth()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [progress, setProgress] = useState([])
  const [plans, setPlans] = useState([])
  const [catalogue, setCatalogue] = useState([])
  const [earned, setEarned] = useState([])
  const [weekWorkouts, setWeekWorkouts] = useState(0)

  // Log-progress form state
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ date: '', completedWorkouts: 1, notes: '' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState('')
  const [flash, setFlash] = useState('')

  const loadData = useCallback(async () => {
    if (!user?.userId) return
    setLoading(true)
    setError('')
    // Resilient: one failing widget shouldn't blank the whole dashboard.
    const [p, pl, cat, ea] = await Promise.allSettled([
      getProgress(user.userId),
      getPlans(user.userId),
      getCatalogue(),
      getUserAchievements(user.userId),
    ])
    if (p.status === 'fulfilled') {
      const records = asArray(p.value)
      setProgress(records)
      // Compute "this week" here (at load time) so render stays pure — no Date.now() in JSX.
      const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
      setWeekWorkouts(
        records
          .filter((r) => {
            const t = new Date(r.date).getTime()
            return !isNaN(t) && t >= weekAgo
          })
          .reduce((sum, r) => sum + (r.completedWorkouts || 0), 0),
      )
    }
    if (pl.status === 'fulfilled') setPlans(asArray(pl.value))
    if (cat.status === 'fulfilled') setCatalogue(asArray(cat.value))
    if (ea.status === 'fulfilled') setEarned(asArray(ea.value))
    if ([p, pl, cat, ea].every((r) => r.status === 'rejected')) {
      setError('Could not load your dashboard. Is the backend running?')
    }
    setLoading(false)
  }, [user])

  useEffect(() => {
    const run = async () => { await loadData() }
    run()
  }, [loadData])

  function openForm() {
    setForm({ date: todayISO(), completedWorkouts: 1, notes: '' })
    setFormError('')
    setShowForm(true)
  }

  async function handleLogProgress(e) {
    e.preventDefault()
    const count = Number(form.completedWorkouts)
    if (!form.date || !count || count < 1) {
      setFormError('Pick a date and at least 1 completed workout.')
      return
    }
    setSubmitting(true)
    setFormError('')
    try {
      await addProgress(user.userId, {
        date: form.date,
        completedWorkouts: count,
        notes: form.notes.trim(),
      })
      setShowForm(false)
      setFlash('Session logged! 🎉 Badges are re-checked automatically.')
      await loadData() // refresh stats + achievements (POST auto-awards badges)
    } catch (err) {
      setFormError(apiErrorMessage(err, 'Could not log your session.'))
    } finally {
      setSubmitting(false)
    }
  }

  // ─── Derived stats (all from real backend data) ───────────────────────────
  const totalWorkouts = progress.reduce((sum, r) => sum + (r.completedWorkouts || 0), 0)
  const streakDays = new Set(progress.map((r) => r.date)).size

  const badgesTotal = catalogue.length
  const badgesEarned = earned.length

  // Weekly target from the user's chosen training frequency, if we can infer it.
  const weeklyTarget = 3 // sensible default; onboarding frequency isn't exposed on the dashboard payload
  const goals = [
    {
      label: 'Workouts this week',
      val: `${weekWorkouts} / ${weeklyTarget}`,
      pct: Math.min(100, Math.round((weekWorkouts / weeklyTarget) * 100)),
    },
    {
      label: 'Badges earned',
      val: badgesTotal ? `${badgesEarned} / ${badgesTotal}` : `${badgesEarned}`,
      pct: badgesTotal ? Math.round((badgesEarned / badgesTotal) * 100) : 0,
    },
  ]

  // Most recent sessions to show under "This week".
  const recent = [...progress]
    .sort((a, b) => new Date(b.date) - new Date(a.date))
    .slice(0, 3)

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">Good morning 👋</div>
          <div className="page-subtitle">
            {plans.length
              ? `${plans.length} active plan${plans.length > 1 ? 's' : ''} · ${weekWorkouts} workouts logged this week`
              : 'No training plan yet — head to Training Plan to generate one'}
          </div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={openForm}>+ Log a session</button>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>
          {error}
        </div>
      )}

      {flash && (
        <div style={{
          background: 'var(--brand-light)', border: '1px solid var(--brand-mid)', color: 'var(--brand-dark)',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>
          {flash}
        </div>
      )}

      {/* Log-progress form */}
      {showForm && (
        <div className="card">
          <div className="card-title" style={{ justifyContent: 'space-between' }}>
            <span>📝 Log a workout session</span>
            <button className="btn btn-sm" onClick={() => setShowForm(false)}>✕ Cancel</button>
          </div>
          <form onSubmit={handleLogProgress}>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <div style={{ flex: '1 1 160px' }}>
                <label className="input-label">Date</label>
                <input
                  className="input"
                  type="date"
                  value={form.date}
                  onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                />
              </div>
              <div style={{ flex: '1 1 160px' }}>
                <label className="input-label">Completed workouts</label>
                <input
                  className="input"
                  type="number"
                  min="1"
                  value={form.completedWorkouts}
                  onChange={(e) => setForm((f) => ({ ...f, completedWorkouts: e.target.value }))}
                />
              </div>
            </div>
            <div style={{ marginTop: 12 }}>
              <label className="input-label">Notes (optional)</label>
              <input
                className="input"
                type="text"
                placeholder="e.g. Felt strong, increased weight on squats"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              />
            </div>

            {formError && (
              <div style={{ color: '#dc2626', fontSize: 12, marginTop: 12 }}>{formError}</div>
            )}

            <div style={{ marginTop: 16 }}>
              <button className="btn btn-primary btn-sm" type="submit" disabled={submitting}>
                {submitting ? 'Saving…' : 'Save session'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Stats row */}
      <div className="stats-grid">
        <StatCard label="Workouts"     value={loading ? '—' : String(totalWorkouts)} change={loading ? '' : `${weekWorkouts} this week`} positive={weekWorkouts > 0} />
        <StatCard label="Streak"       value={loading ? '—' : `${streakDays} days`}  change={loading ? '' : 'Active days logged'} positive={streakDays > 0} />
        <StatCard label="Achievements" value={loading ? '—' : (badgesTotal ? `${badgesEarned} / ${badgesTotal}` : String(badgesEarned))} change={loading ? '' : 'Badges earned'} />
      </div>

      <div className="two-col">
        {/* This week — recent logged sessions */}
        <div className="card">
          <div className="card-title">📅 Recent activity</div>
          {loading ? (
            <div className="empty-state"><p>Loading…</p></div>
          ) : recent.length ? (
            recent.map((r, i) => (
              <WorkoutCard
                key={i}
                day={formatDay(r.date)}
                focus={r.notes || `${r.completedWorkouts || 0} workout${(r.completedWorkouts || 0) > 1 ? 's' : ''} completed`}
                status="done"
              />
            ))
          ) : (
            <div className="empty-state">
              <p>No sessions logged yet. Complete a workout to see it here.</p>
            </div>
          )}
        </div>

        <div>
          {/* Weekly goal */}
          <div className="card">
            <div className="card-title">📊 Weekly goal</div>
            {goals.map((g) => (
              <div key={g.label} style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                  <span style={{ color: 'var(--text-secondary)' }}>{g.label}</span>
                  <span style={{ fontWeight: 600 }}>{g.val}</span>
                </div>
                <div className="progress-wrap">
                  <div className="progress-fill" style={{ width: `${g.pct}%` }} />
                </div>
              </div>
            ))}
          </div>

          {/* AI tip — static placeholder for now (In-App Chat, out of scope today) */}
          <div className="card">
            <div className="card-title" style={{ justifyContent: 'space-between' }}>
              <span>🤖 AI tip of the day</span>
              <span className="ai-badge">✨ AI</span>
            </div>
            <p style={{ fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              Rest 60–90 sec between sets for muscle endurance. Your Friday session
              focuses on compound movements — prioritise form over weight.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
