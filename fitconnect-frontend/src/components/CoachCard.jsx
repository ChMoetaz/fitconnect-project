import '../styles/main.css'

/**
 * CoachCard — coach profile row with a book button.
 * Props map to the backend CoachProfile:
 *   initials         {string}  — derived from the name, e.g. "SR"
 *   name             {string}  — e.g. "Sarah R."
 *   specialization   {string}  — e.g. "Strength"
 *   sports           {string}  — comma-joined sport type names, e.g. "Strength Training, HIIT"
 *   experienceYears  {number}  — e.g. 8
 *   onBook           {function} — callback when Book is clicked
 */
export default function CoachCard({ initials, name, specialization, sports, experienceYears, onBook }) {
  return (
    <div className="row-item">
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <div className="avatar avatar-lg">{initials}</div>
        <div>
          <div style={{ fontWeight: 600, fontSize: 14 }}>{name}</div>
          <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
            {[specialization, sports].filter(Boolean).join(' · ')}
          </div>
          {experienceYears != null && (
            <div style={{ fontSize: 12, color: '#d97706', marginTop: 2 }}>
              🎓 {experienceYears} {experienceYears === 1 ? 'year' : 'years'} of experience
            </div>
          )}
        </div>
      </div>
      <button className="btn btn-primary btn-sm" onClick={onBook}>
        Book
      </button>
    </div>
  )
}
