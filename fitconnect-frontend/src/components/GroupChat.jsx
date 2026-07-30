import { useEffect, useRef, useState } from 'react'
import { getMessages, ChatConnection } from '../api/chat'
import { getToken } from '../api/storage'
import { apiErrorMessage } from '../api/client'

/**
 * Real-time chat for ONE community group. Mounted with `key={groupId}` by the parent, so
 * switching groups fully remounts this component: the previous instance unmounts (its effect
 * cleanup disconnects the STOMP session) before the new one mounts with a clean, empty state.
 * That keying is what guarantees the right history + the right live socket always match the
 * selected group — no message carry-over, never two sockets alive at once.
 *
 * Members-only: no history is fetched and no socket is opened until `joined` is true.
 */

const asArray = (x) => (Array.isArray(x) ? x : [])
const messageId = (m) => m?.messageId ?? m?.id
const messageMine = (m, userId) => Number(m?.senderId) === Number(userId)
const senderLabel = (m, userId) =>
  messageMine(m, userId) ? 'You' : (m?.senderEmail?.split('@')[0] || 'User')
const formatMessageTime = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? ''
    : d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

export default function GroupChat({ groupId, groupName, joined, userId, onJoin, joining }) {
  const [messages, setMessages] = useState([])
  const [chatError, setChatError] = useState('')
  const [chatStatus, setChatStatus] = useState('idle') // idle | connecting | connected | disconnected | error
  const [chatInput, setChatInput] = useState('')
  const chatRef = useRef(null)
  const messagesEndRef = useRef(null)

  // Load history + open the STOMP session once the user has joined this group.
  // Depends on [joined]: a Join click opens the chat, a Leave click tears it down —
  // without any remount, since the group (and thus this component's key) is unchanged.
  useEffect(() => {
    if (groupId == null || !userId || !joined) return
    let cancelled = false
    console.debug('[chat] open group', groupId)

    const conn = new ChatConnection({
      groupId,
      token: getToken(),
      // Guard every callback against a stale connection: if chatRef has moved on
      // (unmount/leave), a late frame or status from the old socket must not touch state.
      onMessage: (msg) => {
        if (chatRef.current !== conn) return
        if (msg?.groupId != null && Number(msg.groupId) !== Number(groupId)) return
        setMessages((prev) =>
          prev.some((m) => messageId(m) === messageId(msg)) ? prev : [...prev, msg],
        )
      },
      onError: (message) => { if (chatRef.current === conn) setChatError(message) },
      onStatusChange: (status) => { if (chatRef.current === conn) setChatStatus(status) },
    })
    chatRef.current = conn

    ;(async () => {
      setChatError('')
      setChatInput('')
      setMessages([])
      try {
        const history = asArray(await getMessages(groupId))
        if (!cancelled) {
          console.debug('[chat] history loaded', groupId, history.length)
          setMessages(history)
        }
      } catch (err) {
        if (!cancelled) setChatError(apiErrorMessage(err, 'Could not load chat history.'))
      }
      if (!cancelled) conn.connect() // subscribe for live messages after history is in
    })()

    return () => {
      console.debug('[chat] close group', groupId)
      cancelled = true
      conn.disconnect()
      chatRef.current = null
    }
  }, [groupId, userId, joined])

  // Keep the latest message in view.
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ block: 'nearest' })
  }, [messages])

  function handleSendMessage(e) {
    e.preventDefault()
    const content = chatInput.trim()
    if (!content || groupId == null) return
    setChatError('')
    const ok = chatRef.current?.send(content)
    if (ok) {
      setChatInput('') // the sent message echoes back via the topic → no optimistic append
    } else {
      setChatError('Not connected yet — please retry in a moment.')
    }
  }

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div className="card-title" style={{ marginBottom: 0 }}>
          💬 Chat{groupName ? ` · ${groupName}` : ''}
        </div>
        {groupId != null && joined && (
          <span style={{
            fontSize: 11, fontWeight: 600,
            color: chatStatus === 'connected' ? '#16a34a' : 'var(--text-muted)',
          }}>
            {chatStatus === 'connected' ? '● live'
              : chatStatus === 'connecting' ? '○ connecting…'
              : chatStatus === 'error' ? '○ offline' : ''}
          </span>
        )}
      </div>

      {chatError && joined && (
        <div style={{
          background: '#fef2f2', border: '1px solid #fecaca', color: '#dc2626',
          borderRadius: 12, padding: '10px 14px', fontSize: 13, margin: '12px 0',
        }}>
          {chatError}
        </div>
      )}

      {groupId == null ? (
        <div className="empty-state"><p>Select a group to open its chat.</p></div>
      ) : !joined ? (
        // Members-only: no history is fetched and no socket is opened until the user joins.
        <div className="empty-state" style={{ display: 'grid', gap: 12, justifyItems: 'center' }}>
          <p style={{ margin: 0 }}>Join this group to see and send messages.</p>
          <button
            className="btn btn-primary btn-sm"
            onClick={onJoin}
            disabled={joining || !userId}
          >
            {joining ? 'Joining…' : 'Join group'}
          </button>
        </div>
      ) : (
        <>
          <div style={{ maxHeight: 320, overflowY: 'auto', marginTop: 8 }}>
            {messages.length ? (
              messages.map((m) => {
                const mine = messageMine(m, userId)
                return (
                  <div key={messageId(m)} style={{ padding: '8px 0', textAlign: mine ? 'right' : 'left' }}>
                    <div style={{
                      display: 'flex', alignItems: 'center', gap: 8,
                      justifyContent: mine ? 'flex-end' : 'flex-start',
                    }}>
                      <span style={{ fontWeight: 600, fontSize: 13 }}>{senderLabel(m, userId)}</span>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{formatMessageTime(m.sentAt)}</span>
                    </div>
                    <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 3 }}>{m.content}</div>
                  </div>
                )
              })
            ) : (
              <div className="empty-state"><p>No messages yet. Say hi! 👋</p></div>
            )}
            <div ref={messagesEndRef} />
          </div>

          <form onSubmit={handleSendMessage} style={{ marginTop: 12, display: 'flex', gap: 8 }}>
            <input
              className="input"
              placeholder="Write a message..."
              style={{ flex: 1 }}
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
            />
            <button
              type="submit"
              className="btn btn-primary btn-sm"
              disabled={!chatInput.trim() || chatStatus !== 'connected'}
            >Send</button>
          </form>
        </>
      )}
    </div>
  )
}
