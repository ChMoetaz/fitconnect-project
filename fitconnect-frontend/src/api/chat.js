import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import api from './client'
import { getToken } from './storage'

/**
 * Real-time group chat integration (STOMP over SockJS).
 *
 * - History is plain REST: GET /api/community-groups/{groupId}/messages (last 50, oldest-first).
 * - Live is STOMP: connect to /ws?token=<jwt> (token in the query — a browser can't set a custom
 *   header on a WebSocket handshake), subscribe to the group topic + the private error queue, and
 *   publish to /app/... with just { content }.
 *
 * MessageResponse shape (both REST history and live broadcast):
 *   { messageId, groupId, senderId, senderEmail, content, sentAt }
 */

const WS_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

/** Loads the chat history for a group (REST, JWT via the shared axios client). */
export async function getMessages(groupId) {
  const { data } = await api.get(`/api/community-groups/${groupId}/messages`)
  return data
}

/**
 * Wraps one STOMP session scoped to a single group. Lifecycle: `new ChatConnection(...)` →
 * `connect()` → `send(content)` → `disconnect()`. All callbacks are optional and defensive.
 */
export class ChatConnection {
  constructor({ groupId, token, onMessage, onError, onStatusChange } = {}) {
    this.groupId = groupId
    this.token = token ?? getToken()
    this.onMessage = onMessage
    this.onError = onError
    this.onStatusChange = onStatusChange
    this.client = null
  }

  connect() {
    if (this.client || this.groupId == null || !this.token) return
    const url = `${WS_BASE}/ws?token=${encodeURIComponent(this.token)}`
    this._setStatus('connecting')

    this.client = new Client({
      // SockJS handshake carries the JWT as a query param (see backend WebSocketConfig).
      webSocketFactory: () => new SockJS(url),
      reconnectDelay: 4000,
      onConnect: () => {
        this._setStatus('connected')
        this.client.subscribe(
          `/topic/community-groups/${this.groupId}/messages`,
          (frame) => {
            const msg = safeParse(frame.body)
            if (msg) this.onMessage?.(msg)
          },
        )
        // Per-session error channel (not a member, empty/too-long content, group missing…).
        this.client.subscribe('/user/queue/errors', (frame) => {
          const err = safeParse(frame.body)
          this.onError?.(err?.message || 'Chat error.')
        })
      },
      onStompError: (frame) => {
        this._setStatus('error')
        this.onError?.(frame?.headers?.message || 'Chat connection error.')
      },
      onWebSocketClose: () => this._setStatus('disconnected'),
    })

    this.client.activate()
  }

  /** Publishes a message. Returns false if the socket isn't connected yet. */
  send(content) {
    if (!this.client || !this.client.connected) return false
    this.client.publish({
      destination: `/app/community-groups/${this.groupId}/messages`,
      body: JSON.stringify({ content }),
    })
    return true
  }

  disconnect() {
    if (!this.client) return
    // deactivate() also stops auto-reconnect; ignore the returned promise on teardown.
    this.client.deactivate()
    this.client = null
    this._setStatus('disconnected')
  }

  _setStatus(status) {
    this.onStatusChange?.(status)
  }
}

function safeParse(body) {
  try {
    return JSON.parse(body)
  } catch {
    return null
  }
}
