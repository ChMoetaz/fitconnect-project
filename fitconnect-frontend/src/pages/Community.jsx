import { useEffect, useState } from 'react'
import '../styles/main.css'
import { useAuth } from '../context/AuthContext'
import { getGroups, joinGroup, leaveGroup } from '../api/community'
import {
  getEvents,
  createEvent,
  deleteEvent,
  registerEvent,
  unregisterEvent,
} from '../api/events'
import { getNearbyGroups } from '../api/maps'
import { getRecommendedGroups } from '../api/recommendations'
import { apiErrorMessage } from '../api/client'
import NearbyMap from '../components/NearbyMap'
import GroupChat from '../components/GroupChat'
import RecommendationCard from '../components/RecommendationCard'
import CommunityGroupForm from '../components/CommunityGroupForm'
import { useUserLocation } from '../hooks/useUserLocation'
import { toLatLng } from '../utils/geo'

const asArray = (x) => (Array.isArray(x) ? x : [])
const groupId = (g) => g?.communityId ?? g?.id ?? g?.groupId ?? g?.communityGroupId
const groupSport = (g) => g?.sportTypeName ?? g?.sportType?.name ?? g?.sportType ?? null
const memberCount = (g) =>
  g?.memberCount ?? (Array.isArray(g?.members) ? g.members.length : null)
// Nullable coords (backend geocoding may have failed) → null skips the marker.
const groupPosition = (g) => toLatLng(g?.latitude, g?.longitude)
// Backend flattens membership into isJoined (JSON key "isJoined"); fall back to the
// legacy members[] shape defensively in case an older payload is served.
const groupJoined = (g, userId) =>
  g?.isJoined ??
  (Array.isArray(g?.members)
    ? g.members.map((m) => m?.userId ?? m).includes(userId)
    : false)

// ── Events (nested under a group) ────────────────────────────────────────────
const eventId = (e) => e?.eventId ?? e?.id
const eventAttendeeCount = (e) =>
  e?.attendeeCount ??
  (Array.isArray(e?.attendeeIds)
    ? e.attendeeIds.length
    : Array.isArray(e?.attendees)
      ? e.attendees.length
      : null)
// EventResponse has no isRegistered flag — derive it from the attendeeIds list
// (the userIds registered for the event), with a defensive attendees[] fallback.
const eventAttendeeIds = (e) =>
  Array.isArray(e?.attendeeIds)
    ? e.attendeeIds
    : Array.isArray(e?.attendees)
      ? e.attendees.map((a) => a?.userId ?? a)
      : []
const eventRegistered = (e, userId) =>
  eventAttendeeIds(e).map(Number).includes(Number(userId))
const formatEventDate = (iso) => {
  if (!iso) return null
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? String(iso)
    : d.toLocaleString(undefined, {
        weekday: 'short', day: 'numeric', month: 'short',
        hour: '2-digit', minute: '2-digit',
      })
}

const emptyEventForm = { title: '', description: '', eventDate: '', location: '' }

export default function Community() {
  const { user } = useAuth()
  const [groups, setGroups] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)
  const [showCreate, setShowCreate] = useState(false)

  // Map view: List/Map toggle, user location (Berlin fallback), nearby-filtered ids.
  const [view, setView] = useState('list')
  const { location, resolved } = useUserLocation()
  const [nearbyIds, setNearbyIds] = useState(null) // null = show all (fallback / unfiltered)

  // AI recommendations (add-on). Loaded independently so the classic list never waits on it.
  const [recs, setRecs] = useState([])
  const [recsLoading, setRecsLoading] = useState(false)
  const [recsHint, setRecsHint] = useState('') // discreet note (e.g. onboarding needed)
  const [recBusyId, setRecBusyId] = useState(null)

  async function loadGroups() {
    const data = asArray(await getGroups())
    setGroups(data)
    return data
  }

  // Shallow-patch a single group in place (optimistic isJoined / memberCount updates).
  function patchGroup(id, patch) {
    setGroups((prev) => prev.map((g) => (groupId(g) === id ? { ...g, ...patch } : g)))
  }

  // Same, but for the recommended list (each rec wraps its group under `rec.group`).
  function patchRecGroup(id, patch) {
    setRecs((prev) =>
      prev.map((r) => (groupId(r?.group) === id ? { ...r, group: { ...r.group, ...patch } } : r)),
    )
  }

  // ── Events state (for the currently selected group) ──
  const [selectedGroupId, setSelectedGroupId] = useState(null)
  const [events, setEvents] = useState([])
  const [eventsLoading, setEventsLoading] = useState(false)
  const [eventsError, setEventsError] = useState('')
  const [eventBusyId, setEventBusyId] = useState(null)
  const [showEventForm, setShowEventForm] = useState(false)
  const [eventForm, setEventForm] = useState(emptyEventForm)
  const [creatingEvent, setCreatingEvent] = useState(false)

  const selectedGroup = groups.find((g) => groupId(g) === selectedGroupId) || null
  // Chat is members-only; membership is derived from the live `groups` state so a Join/Leave
  // click flips it and GroupChat opens/closes its socket accordingly.
  const selectedJoined = selectedGroup ? groupJoined(selectedGroup, user?.userId) : false
  // Markers come from the live `groups` state (so Join/Leave stays in sync with the list),
  // narrowed to the nearby id-set when available. Entries with null coords are dropped by the map.
  const mapGroups = nearbyIds ? groups.filter((g) => nearbyIds.has(groupId(g))) : groups

  function patchEvent(id, patch) {
    setEvents((prev) => prev.map((e) => (eventId(e) === id ? { ...e, ...patch } : e)))
  }

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      setLoading(true)
      setError('')
      try {
        const data = await loadGroups()
        if (cancelled) return
        // Auto-select the first group so the Events section has a context by default.
        setSelectedGroupId((cur) => cur ?? (data.length ? groupId(data[0]) : null))
      } catch (err) {
        if (!cancelled) setError(apiErrorMessage(err, 'Could not load community groups.'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [user])

  // Once a center is known, fetch the nearby subset (ids) for the map. On failure
  // (e.g. /nearby not available) fall back to null → all groups shown as markers.
  useEffect(() => {
    if (!resolved) return
    let cancelled = false
    ;(async () => {
      try {
        const data = asArray(await getNearbyGroups(location.lat, location.lng))
        if (!cancelled) setNearbyIds(new Set(data.map(groupId).filter((x) => x != null)))
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
      const data = asArray(await getRecommendedGroups(user.userId))
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

  // Join/Leave from a recommended card. Self-contained optimistic update on `recs`, and it also
  // syncs the main `groups` state (patchGroup is a no-op if the group isn't loaded there), so the
  // two surfaces never diverge. Does not touch the existing handleToggle used by the main list.
  async function handleRecGroupToggle(rec) {
    const g = rec?.group
    const id = groupId(g)
    if (!user?.userId || id == null || recBusyId === id) return

    const wasJoined = groupJoined(g, user.userId)
    const prevCount = memberCount(g)
    const nextCount =
      typeof prevCount === 'number' ? prevCount + (wasJoined ? -1 : 1) : prevCount

    setRecBusyId(id)
    patchRecGroup(id, { isJoined: !wasJoined, memberCount: nextCount })
    patchGroup(id, { isJoined: !wasJoined, memberCount: nextCount })
    try {
      if (wasJoined) await leaveGroup(id, user.userId)
      else await joinGroup(id, user.userId)
    } catch (err) {
      patchRecGroup(id, { isJoined: wasJoined, memberCount: prevCount })
      patchGroup(id, { isJoined: wasJoined, memberCount: prevCount })
      setError(apiErrorMessage(err, wasJoined ? 'Could not leave this group.' : 'Could not join this group.'))
    } finally {
      setRecBusyId(null)
    }
  }

  async function handleToggle(g) {
    const id = groupId(g)
    // Guard: never fire before the auth context is ready (userId) or without a
    // resolved group id, otherwise "undefined" would land in the URL path/query
    // and the backend fails converting it to Long. Also block double-clicks.
    if (!user?.userId || id == null || busyId === id) return

    const wasJoined = groupJoined(g, user.userId)
    const prevCount = memberCount(g)
    const nextCount =
      typeof prevCount === 'number' ? prevCount + (wasJoined ? -1 : 1) : prevCount

    setBusyId(id)
    setError('')
    // Optimistic: flip the flag and adjust the count right away.
    patchGroup(id, { isJoined: !wasJoined, memberCount: nextCount })
    try {
      if (wasJoined) await leaveGroup(id, user.userId)
      else await joinGroup(id, user.userId)
    } catch (err) {
      // Rollback the optimistic change on failure.
      patchGroup(id, { isJoined: wasJoined, memberCount: prevCount })
      setError(
        apiErrorMessage(err, wasJoined ? 'Could not leave this group.' : 'Could not join this group.'),
      )
    } finally {
      setBusyId(null)
    }
  }

  // Called by CommunityGroupForm after a successful POST. Refresh the list and keep the map's
  // nearby id-set in sync so the new group shows immediately (same behaviour as before).
  async function handleGroupCreated(created) {
    setShowCreate(false)
    await loadGroups()
    const newId = groupId(created)
    if (newId != null) {
      setNearbyIds((prev) => (prev == null ? prev : new Set(prev).add(newId)))
    }
  }

  // Reload the events of the selected group whenever the selection changes.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      if (selectedGroupId == null) {
        if (!cancelled) setEvents([])
        return
      }
      setEventsLoading(true)
      setEventsError('')
      setShowEventForm(false)
      try {
        const data = asArray(await getEvents(selectedGroupId))
        if (!cancelled) setEvents(data)
      } catch (err) {
        if (!cancelled) setEventsError(apiErrorMessage(err, 'Could not load events.'))
      } finally {
        if (!cancelled) setEventsLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [selectedGroupId])

  async function handleRegisterToggle(ev) {
    const eid = eventId(ev)
    if (!user?.userId || eid == null || selectedGroupId == null || eventBusyId === eid) return

    const wasRegistered = eventRegistered(ev, user.userId)
    const prevCount = eventAttendeeCount(ev)
    const prevIds = eventAttendeeIds(ev)
    const nextCount =
      typeof prevCount === 'number' ? prevCount + (wasRegistered ? -1 : 1) : prevCount
    const nextIds = wasRegistered
      ? prevIds.filter((x) => Number(x) !== Number(user.userId))
      : [...prevIds, Number(user.userId)]

    setEventBusyId(eid)
    setEventsError('')
    // Optimistic: flip registration + adjust attendee count/ids right away.
    patchEvent(eid, { attendeeCount: nextCount, attendeeIds: nextIds })
    try {
      const updated = wasRegistered
        ? await unregisterEvent(selectedGroupId, eid, user.userId)
        : await registerEvent(selectedGroupId, eid, user.userId)
      // Reconcile with the authoritative EventResponse the backend returns.
      if (updated && eventId(updated) != null) patchEvent(eid, updated)
    } catch (err) {
      patchEvent(eid, { attendeeCount: prevCount, attendeeIds: prevIds })
      setEventsError(
        apiErrorMessage(err, wasRegistered ? 'Could not unregister.' : 'Could not register.'),
      )
    } finally {
      setEventBusyId(null)
    }
  }

  async function handleCreateEvent(e) {
    e.preventDefault()
    if (selectedGroupId == null || creatingEvent) return
    if (!eventForm.title.trim() || !eventForm.eventDate) {
      setEventsError('Title and date are required.')
      return
    }
    setCreatingEvent(true)
    setEventsError('')
    try {
      const created = await createEvent(selectedGroupId, {
        title: eventForm.title.trim(),
        description: eventForm.description.trim(),
        eventDate: eventForm.eventDate, // "YYYY-MM-DDTHH:mm" — valid ISO LocalDateTime
        location: eventForm.location.trim(),
      })
      setEvents((prev) => [created, ...prev])
      setEventForm(emptyEventForm)
      setShowEventForm(false)
    } catch (err) {
      setEventsError(apiErrorMessage(err, 'Could not create the event.'))
    } finally {
      setCreatingEvent(false)
    }
  }

  async function handleDeleteEvent(ev) {
    const eid = eventId(ev)
    if (selectedGroupId == null || eid == null) return
    if (!window.confirm(`Delete event "${ev.title}"?`)) return
    setEventsError('')
    const snapshot = events
    setEvents((prev) => prev.filter((x) => eventId(x) !== eid)) // optimistic remove
    try {
      await deleteEvent(selectedGroupId, eid)
    } catch (err) {
      setEvents(snapshot) // rollback
      setEventsError(apiErrorMessage(err, 'Could not delete the event.'))
    }
  }

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div className="page-title">Community Groups</div>
          <div className="page-subtitle">Train together · Stay motivated</div>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => setShowCreate((s) => !s)}>
          {showCreate ? 'Cancel' : '+ New group'}
        </button>
      </div>

      {error && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, marginBottom: 16,
        }}>
          {error}
        </div>
      )}

      {showCreate && <CommunityGroupForm onCreated={handleGroupCreated} />}

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
            <div className="empty-state"><p>Finding groups for you…</p></div>
          ) : recs.length > 0 ? (
            recs.map((r) => {
              const g = r?.group || {}
              const id = groupId(g)
              const isJoined = groupJoined(g, user?.userId)
              const busy = recBusyId === id
              const count = memberCount(g)
              const meta = [
                count != null ? `${count} members` : null,
                g.location || groupSport(g) || null,
              ].filter(Boolean).join(' · ')
              return (
                <RecommendationCard key={id ?? g.name} reason={r?.reason}>
                  <div className="row-item">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <div style={{
                        width: 38, height: 38, borderRadius: 10,
                        background: 'var(--brand-light)', display: 'flex',
                        alignItems: 'center', justifyContent: 'center', fontSize: 18,
                      }}>🤝</div>
                      <div>
                        <div style={{ fontWeight: 600, fontSize: 13 }}>{g.name}</div>
                        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{meta || '—'}</div>
                      </div>
                    </div>
                    <button
                      className={`btn btn-sm ${isJoined ? 'btn-danger' : 'btn-primary'}`}
                      onClick={() => handleRecGroupToggle(r)}
                      disabled={busy || !user?.userId}
                    >
                      {busy
                        ? isJoined ? 'Leaving…' : 'Joining…'
                        : isJoined ? 'Leave' : 'Join'}
                    </button>
                  </div>
                </RecommendationCard>
              )
            })
          ) : recsHint ? (
            <div className="empty-state"><p style={{ color: 'var(--text-muted)' }}>{recsHint}</p></div>
          ) : null}
        </div>
      )}

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <div className="card-title" style={{ marginBottom: 0 }}>📍 Groups</div>
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
        {loading ? (
          <div className="empty-state"><p>Loading groups…</p></div>
        ) : view === 'map' ? (
          <NearbyMap
            center={location}
            items={mapGroups}
            getPosition={groupPosition}
            getKey={(g) => groupId(g)}
            renderPopup={(g) => {
              const isJoined = groupJoined(g, user?.userId)
              const busy = busyId === groupId(g)
              const count = memberCount(g)
              const meta = [g.location || groupSport(g), count != null ? `${count} members` : null]
                .filter(Boolean).join(' · ')
              return (
                <div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111', marginBottom: 2 }}>{g.name}</div>
                  <div style={{ fontSize: 12, color: '#555' }}>{meta || '—'}</div>
                  <button
                    className={`btn btn-sm ${isJoined ? 'btn-danger' : 'btn-primary'}`}
                    style={{ marginTop: 8 }}
                    onClick={() => handleToggle(g)}
                    disabled={busy || !user?.userId}
                  >
                    {busy
                      ? isJoined ? 'Leaving…' : 'Joining…'
                      : isJoined ? 'Leave' : 'Join'}
                  </button>
                </div>
              )
            }}
          />
        ) : groups.length ? (
          groups.map((g) => {
            const id = groupId(g)
            const isJoined = groupJoined(g, user?.userId)
            const busy = busyId === id
            const count = memberCount(g)
            const sport = groupSport(g)
            const sub = [
              count != null ? `${count} members` : null,
              g.location || sport || g.description || null,
            ].filter(Boolean).join(' · ')
            const isSelected = selectedGroupId === id
            return (
              <div
                className="row-item"
                key={id}
                onClick={() => setSelectedGroupId(id)}
                style={{
                  cursor: 'pointer',
                  borderRadius: 10,
                  background: isSelected ? 'var(--brand-light)' : undefined,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 38, height: 38, borderRadius: 10,
                    background: 'var(--brand-light)', display: 'flex',
                    alignItems: 'center', justifyContent: 'center', fontSize: 18,
                  }}>🤝</div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{g.name}</div>
                    <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{sub || '—'}</div>
                  </div>
                </div>
                <button
                  className={`btn btn-sm ${isJoined ? 'btn-danger' : 'btn-primary'}`}
                  onClick={(e) => { e.stopPropagation(); handleToggle(g) }}
                  disabled={busy || !user?.userId}
                >
                  {busy
                    ? isJoined ? 'Leaving…' : 'Joining…'
                    : isJoined ? 'Leave' : 'Join'}
                </button>
              </div>
            )
          })
        ) : (
          <div className="empty-state"><p>No groups yet. Create the first one!</p></div>
        )}
      </div>

      {/* Events for the currently selected group */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div className="card-title" style={{ marginBottom: 0 }}>
            📅 Events{selectedGroup ? ` · ${selectedGroup.name}` : ''}
          </div>
          <button
            className="btn btn-primary btn-sm"
            onClick={() => setShowEventForm((s) => !s)}
            disabled={selectedGroupId == null}
          >
            {showEventForm ? 'Cancel' : '+ Create event'}
          </button>
        </div>

        {eventsError && (
          <div style={{
            background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
            borderRadius: 12, padding: '10px 14px', fontSize: 13, margin: '12px 0',
          }}>
            {eventsError}
          </div>
        )}

        {showEventForm && (
          <form
            onSubmit={handleCreateEvent}
            style={{
              display: 'grid', gap: 8, padding: '14px 0',
              borderBottom: '1px solid #f3f4f6', marginBottom: 8,
            }}
          >
            <input
              className="input" placeholder="Title *" value={eventForm.title}
              onChange={(e) => setEventForm((f) => ({ ...f, title: e.target.value }))}
            />
            <input
              className="input" placeholder="Description" value={eventForm.description}
              onChange={(e) => setEventForm((f) => ({ ...f, description: e.target.value }))}
            />
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <input
                className="input" type="datetime-local" style={{ flex: 1, minWidth: 180 }}
                value={eventForm.eventDate}
                onChange={(e) => setEventForm((f) => ({ ...f, eventDate: e.target.value }))}
              />
              <input
                className="input" placeholder="Location" style={{ flex: 1, minWidth: 180 }}
                value={eventForm.location}
                onChange={(e) => setEventForm((f) => ({ ...f, location: e.target.value }))}
              />
            </div>
            <div>
              <button type="submit" className="btn btn-primary btn-sm" disabled={creatingEvent}>
                {creatingEvent ? 'Creating…' : 'Create event'}
              </button>
            </div>
          </form>
        )}

        {selectedGroupId == null ? (
          <div className="empty-state"><p>Select a group to see its events.</p></div>
        ) : eventsLoading ? (
          <div className="empty-state"><p>Loading events…</p></div>
        ) : events.length ? (
          events.map((ev) => {
            const eid = eventId(ev)
            const registered = eventRegistered(ev, user?.userId)
            const busy = eventBusyId === eid
            const attendees = eventAttendeeCount(ev)
            const sub = [
              formatEventDate(ev.eventDate),
              ev.location || null,
              attendees != null ? `${attendees} going` : null,
            ].filter(Boolean).join(' · ')
            return (
              <div className="row-item" key={eid}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div style={{
                    width: 38, height: 38, borderRadius: 10,
                    background: 'var(--brand-light)', display: 'flex',
                    alignItems: 'center', justifyContent: 'center', fontSize: 18,
                  }}>📅</div>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{ev.title}</div>
                    {ev.description && (
                      <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{ev.description}</div>
                    )}
                    <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>{sub || '—'}</div>
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <button
                    className={`btn btn-sm ${registered ? 'btn-danger' : 'btn-primary'}`}
                    onClick={() => handleRegisterToggle(ev)}
                    disabled={busy || !user?.userId}
                  >
                    {busy
                      ? registered ? 'Unregistering…' : 'Registering…'
                      : registered ? 'Unregister' : 'Register'}
                  </button>
                  <button
                    className="btn btn-sm"
                    title="Delete event"
                    onClick={() => handleDeleteEvent(ev)}
                    style={{ padding: '5px 9px' }}
                  >✕</button>
                </div>
              </div>
            )
          })
        ) : (
          <div className="empty-state"><p>No events yet for this group.</p></div>
        )}
      </div>

      {/* Real-time group chat — keyed by group id so switching groups fully remounts it
          (fresh state, one live socket at a time, no message carry-over). */}
      <GroupChat
        key={selectedGroupId ?? 'none'}
        groupId={selectedGroupId}
        groupName={selectedGroup?.name}
        joined={selectedJoined}
        userId={user?.userId}
        onJoin={() => selectedGroup && handleToggle(selectedGroup)}
        joining={busyId === selectedGroupId}
      />
    </div>
  )
}
