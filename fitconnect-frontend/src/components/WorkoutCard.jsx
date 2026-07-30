import '../styles/main.css'

/**
 * WorkoutCard — single workout day row
 * Props:
 *   day     {string}  — e.g. "Monday"
 *   focus   {string}  — e.g. "Upper body · Pull"
 *   status  {string}  — "done" | "today" | "upcoming"
 */
const STATUS_MAP = {
  done:     { label: 'Done',     cls: 'badge-green'  },
  today:    { label: 'Today',    cls: 'badge-active'  },
  upcoming: { label: 'Upcoming', cls: 'badge-gray'    },
}

export default function WorkoutCard({ day, focus, status = 'upcoming' }) {
  const { label, cls } = STATUS_MAP[status] || STATUS_MAP.upcoming

  return (
    <div className="row-item">
      <div>
        <div style={{ fontWeight: 600, fontSize: 13 }}>{day}</div>
        <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{focus}</div>
      </div>
      <span className={`badge ${cls}`}>{label}</span>
    </div>
  )
}