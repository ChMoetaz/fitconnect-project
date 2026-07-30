/**
 * Defensive lat/lng parse. Returns { lat, lng } or null when either coordinate is
 * missing/null/NaN — callers skip null results so ungeocoded entries never crash.
 */
export function toLatLng(rawLat, rawLng) {
  if (rawLat == null || rawLng == null) return null
  const lat = Number(rawLat)
  const lng = Number(rawLng)
  if (Number.isNaN(lat) || Number.isNaN(lng)) return null
  return { lat, lng }
}
