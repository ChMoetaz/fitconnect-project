import { useState } from 'react'
import { GoogleMap, Marker, InfoWindow, useJsApiLoader } from '@react-google-maps/api'

const MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY

const containerStyle = { width: '100%', height: 440, borderRadius: 12 }

/**
 * Presentational Google Map: renders one marker per item that has a valid position,
 * with a click-to-open InfoWindow whose content is provided by `renderPopup(item)`.
 *
 * Props:
 *  - center:      { lat, lng } (keep a stable reference to avoid re-centering)
 *  - items:       array of raw backend objects
 *  - getPosition: (item) => { lat, lng } | null
 *  - getKey:      (item) => stable unique key
 *  - renderPopup: (item) => ReactNode
 */
export default function NearbyMap({ center, items, getPosition, getKey, renderPopup }) {
  const { isLoaded, loadError } = useJsApiLoader({
    id: 'google-map-script',
    googleMapsApiKey: MAPS_API_KEY || '',
  })
  const [activeKey, setActiveKey] = useState(null)

  const notice = (text) => <div className="empty-state"><p>{text}</p></div>

  if (!MAPS_API_KEY) return notice('Google Maps key missing — set VITE_GOOGLE_MAPS_API_KEY in .env.')
  if (loadError) return notice('Could not load Google Maps.')
  if (!isLoaded) return notice('Loading map…')

  const markers = (Array.isArray(items) ? items : [])
    .map((item) => ({ item, key: getKey(item), pos: getPosition(item) }))
    .filter((m) => m.pos) // skip null lat/lng (ungeocoded entries)

  const active = markers.find((m) => m.key === activeKey)

  return (
    <GoogleMap
      mapContainerStyle={containerStyle}
      center={center}
      zoom={11}
      onClick={() => setActiveKey(null)}
      options={{ streetViewControl: false, mapTypeControl: false }}
    >
      {markers.map((m) => (
        <Marker key={m.key} position={m.pos} onClick={() => setActiveKey(m.key)} />
      ))}
      {active && (
        <InfoWindow position={active.pos} onCloseClick={() => setActiveKey(null)}>
          <div style={{ minWidth: 170, maxWidth: 240 }}>{renderPopup(active.item)}</div>
        </InfoWindow>
      )}
    </GoogleMap>
  )
}
