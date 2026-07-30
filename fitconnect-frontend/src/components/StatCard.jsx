import '../styles/main.css'

/**
 * StatCard — reusable metric display
 * Props:
 *   label    {string}  — e.g. "Workouts"
 *   value    {string}  — e.g. "12"
 *   change   {string}  — e.g. "↑ 3 this week"
 *   positive {boolean} — green if true, red if false, gray if undefined
 */
export default function StatCard({ label, value, change, positive }) {
  const changeClass = positive === true ? 'pos' : positive === false ? 'neg' : ''

  return (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {change && (
        <div className={`stat-change ${changeClass}`}>{change}</div>
      )}
    </div>
  )
}