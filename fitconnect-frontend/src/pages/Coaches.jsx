import { useEffect, useMemo, useState } from 'react'
import '../styles/main.css'
import CoachCard from '../components/CoachCard'
import RecommendationCard from '../components/RecommendationCard'
import { getCoaches } from '../api/coaches'
import { getNearbyCoaches } from '../api/maps'
import { getRecommendedCoaches } from '../api/recommendations'
import { apiErrorMessage } from '../api/client'
import NearbyMap from '../components/NearbyMap'
import { useUserLocation } from '../hooks/useUserLocation'
import { useAuth } from '../context/AuthContext'
import { toLatLng } from '../utils/geo'

const asArray = (x) => (Array.isArray(x) ? x : [])
const coachId = (c) => c?.coachId ?? c?.id
// Nullable coords (geocoding may have failed) → null skips the marker.
const coachPosition = (c) => toLatLng(c?.latitude, c?.longitude)

function initialsOf(name = '') {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join('')
    .toUpperCase() || 'C'
}

const sportNames = (coach) => asArray(coach.sportTypes).map((s) => s?.name ?? s).filter(Boolean)

export default function Coaches() {
  const { user } = useAuth()
  const [coaches, setCoaches] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [active, setActive] = useState('All')

  // Map view: List/Map toggle, user location (Berlin fallback), nearby-filtered ids.
  const [view, setView] = useState('list')
  const { location, resolved } = useUserLocation()
  const [nearbyIds, setNearbyIds] = useState(null) // null = show all (fallback / unfiltered)

  // AI recommendations (add-on). Loaded independently so the classic list never waits on it.
  const [recs, setRecs] = useState([])
  const [recsLoading, setRecsLoading] = useState(false)
  const [recsHint, setRecsHint] = useState('') // discreet note (e.g. onboarding needed)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const data = asArray(await getCoaches())
        if (!cancelled) setCoaches(data)
      } catch (err) {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not load coaches.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [])

  // Once a center is known, fetch the nearby subset (ids) for the map. On failure
  // (e.g. /nearby not available) fall back to null → all coaches shown as markers.
  useEffect(() => {
    if (!resolved) return
    let cancelled = false
    ;(async () => {
      try {
        const data = asArray(await getNearbyCoaches(location.lat, location.lng))
        if (!cancelled) setNearbyIds(new Set(data.map(coachId).filter((x) => x != null)))
      } catch {
        if (!cancelled) setNearbyIds(null)
      }
    })()
    return () => { cancelled = true }
  }, [resolved, location.lat, location.lng])

  // AI recommendations — triggered explicitly by a button (no auto-load, to avoid spending
  // Gemini tokens on every page visit). A 400 means the user hasn't completed onboarding:
  // show a discreet hint, never a blocking error. Other errors just clear the section.
  async function handleGenerateRecs() {
    if (!user?.userId || recsLoading) return
    setRecsLoading(true)
    setRecsHint('')
    try {
      const data = asArray(await getRecommendedCoaches(user.userId))
      setRecs(data)
    } catch (err) {
      setRecs([])
      if (err?.response?.status === 400) {
        setRecsHint('Complete your onboarding to get personalized recommendations.')
      }
    } finally {
      setRecsLoading(false)
    }
  }

  // Build filter chips from the sports the coaches actually cover.
  const filters = useMemo(() => {
    const set = new Set()
    coaches.forEach((c) => sportNames(c).forEach((n) => set.add(n)))
    return ['All', ...Array.from(set).sort()]
  }, [coaches])

  const filtered = active === 'All'
    ? coaches
    : coaches.filter((c) => sportNames(c).includes(active))

  // Markers respect the active sport chip, narrowed to the nearby id-set when available.
  const mapCoaches = nearbyIds ? filtered.filter((c) => nearbyIds.has(coachId(c))) : filtered

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">Find a Coach</div>
          <div className="page-subtitle">Book a session with a certified trainer</div>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            className={`btn btn-sm ${view === 'list' ? 'btn-primary' : ''}`}
            onClick={() => setView('list')}
          >List view</button>
          <button
            className={`btn btn-sm ${view === 'map' ? 'btn-primary' : ''}`}
            onClick={() => setView('map')}
          >Map view</button>
        </div>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>
          {error}
        </div>
      )}

      {/* AI recommendations — List view only, above the full list (which is unchanged below).
          Loaded on demand via the button, never automatically. */}
      {view === 'list' && (
        <div className="card" style={{ marginBottom: 16 }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            gap: 8, flexWrap: 'wrap',
            marginBottom: (recsLoading || recs.length > 0 || recsHint) ? 12 : 0,
          }}>
            <div className="card-title" style={{ marginBottom: 0 }}>✨ Recommended for you</div>
            <button
              className="btn btn-primary btn-sm"
              onClick={handleGenerateRecs}
              disabled={recsLoading || !user?.userId}
            >
              {recsLoading
                ? 'Generating…'
                : recs.length > 0 ? 'Regenerate recommendations' : '✨ Generate recommendations'}
            </button>
          </div>
          {recsLoading ? (
            <div className="empty-state"><p>Finding coaches for you…</p></div>
          ) : recs.length > 0 ? (
            recs.map((r) => {
              const c = r?.coach || {}
              return (
                <RecommendationCard key={coachId(c) ?? c.name} reason={r?.reason}>
                  <CoachCard
                    initials={initialsOf(c.name)}
                    name={c.name}
                    specialization={c.specialization}
                    sports={sportNames(c).join(', ')}
                    experienceYears={c.experienceYears}
                    onBook={() => alert(`Booking request sent to ${c.name}!`)}
                  />
                </RecommendationCard>
              )
            })
          ) : recsHint ? (
            <div className="empty-state"><p style={{ color: 'var(--text-muted)' }}>{recsHint}</p></div>
          ) : null}
        </div>
      )}

      {filters.length > 1 && (
        <div style={{ display: 'flex', gap: 8, marginBottom: 20, flexWrap: 'wrap' }}>
          {filters.map((f) => (
            <span key={f} className={`tag ${active === f ? 'active' : ''}`} onClick={() => setActive(f)}>
              {f}
            </span>
          ))}
        </div>
      )}

      <div className="card">
        {loading ? (
          <div className="empty-state"><p>Loading coaches…</p></div>
        ) : view === 'map' ? (
          <NearbyMap
            center={location}
            items={mapCoaches}
            getPosition={coachPosition}
            getKey={(c) => coachId(c) ?? c.name}
            renderPopup={(c) => {
              const meta = [
                c.specialization,
                c.experienceYears != null ? `${c.experienceYears} yrs exp` : null,
                c.location || null,
              ].filter(Boolean).join(' · ')
              return (
                <div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111', marginBottom: 2 }}>{c.name}</div>
                  <div style={{ fontSize: 12, color: '#555' }}>{meta || '—'}</div>
                  {sportNames(c).length > 0 && (
                    <div style={{ fontSize: 12, color: '#777', marginTop: 2 }}>{sportNames(c).join(', ')}</div>
                  )}
                </div>
              )
            }}
          />
        ) : filtered.length ? (
          filtered.map((c) => (
            <CoachCard
              key={coachId(c) ?? c.name}
              initials={initialsOf(c.name)}
              name={c.name}
              specialization={c.specialization}
              sports={sportNames(c).join(', ')}
              experienceYears={c.experienceYears}
              onBook={() => alert(`Booking request sent to ${c.name}!`)}
            />
          ))
        ) : (
          <div className="empty-state">
            <p>{coaches.length ? 'No coaches match this filter.' : 'No coaches available yet.'}</p>
          </div>
        )}
      </div>
    </div>
  )
}
