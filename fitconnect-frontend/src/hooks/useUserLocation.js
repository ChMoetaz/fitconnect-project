import { useEffect, useState } from 'react'

/** Default map center when geolocation is denied/unavailable — Berlin (project context). */
export const BERLIN = { lat: 52.52, lng: 13.405 }

/**
 * Resolves the browser geolocation once on mount, falling back to Berlin on
 * denial / failure / unsupported browser. `resolved` flips to true either way,
 * so callers can trigger a /nearby fetch as soon as a center is known.
 */
const GEO_SUPPORTED = typeof navigator !== 'undefined' && 'geolocation' in navigator

export function useUserLocation() {
  const [location, setLocation] = useState(BERLIN)
  // If geolocation is unsupported, we are already "resolved" on the Berlin fallback.
  const [resolved, setResolved] = useState(!GEO_SUPPORTED)

  useEffect(() => {
    if (!GEO_SUPPORTED) return
    let cancelled = false
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        if (cancelled) return
        setLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude })
        setResolved(true)
      },
      () => { if (!cancelled) setResolved(true) }, // keep the Berlin fallback
      { timeout: 8000, maximumAge: 300000 },
    )
    return () => { cancelled = true }
  }, [])

  return { location, resolved }
}
