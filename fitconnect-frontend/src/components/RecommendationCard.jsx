import '../styles/main.css'

/**
 * Visual wrapper marking an item as an AI recommendation: a brand-coloured border, an
 * "✨ AI recommended" badge, and Gemini's `reason` below the wrapped content. Purely additive —
 * it decorates whatever card/row is passed as children (a CoachCard, a group row, …) without
 * altering it, so the classic lists keep rendering exactly as before.
 */
export default function RecommendationCard({ reason, children }) {
  return (
    <div style={{
      border: '1.5px solid var(--brand)',
      borderRadius: 12,
      padding: '0 12px 10px',
      marginBottom: 10,
    }}>
      <div style={{
        display: 'inline-block',
        fontSize: 10, fontWeight: 700, letterSpacing: 0.4,
        color: 'var(--brand-dark)', background: 'var(--brand-light)',
        borderRadius: 20, padding: '3px 10px', margin: '10px 0 2px',
      }}>
        ✨ AI RECOMMENDED
      </div>
      {children}
      {reason && (
        <div style={{
          fontSize: 12, color: 'var(--text-secondary)', fontStyle: 'italic',
          marginTop: 2, paddingTop: 6, borderTop: '1px solid var(--border)',
        }}>
          💡 {reason}
        </div>
      )}
    </div>
  )
}
