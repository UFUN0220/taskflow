import { apiRequest, isBrowserAuthenticated } from '../api'

export type NotificationItem = {
  notificationId: number | null
  sourceMessageId: string
  notificationType: string
  title: string
  content: string
  aggregateType: string
  aggregateId: number | null
  status: 'UNREAD' | 'READ'
  readAt: string | null
  createdAt: string | null
}

export type NotificationSocketStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'ERROR'

type StompFrame = {
  command: string
  headers: Record<string, string>
  body: string
}

export function browserSessionAvailable(): boolean {
  return isBrowserAuthenticated()
}

export async function fetchNotifications(status: 'UNREAD' | 'READ' = 'UNREAD') {
  const page = await apiRequest<{ records: NotificationItem[] }>(`/api/notifications?page=1&size=50&status=${status}`)
  return page.records
}

export async function fetchUnreadCount() {
  return apiRequest<number>('/api/notifications/unread-count')
}

export async function markNotificationRead(notificationId: number) {
  await apiRequest<null>(`/api/notifications/${notificationId}/read`, { method: 'PATCH' })
}

export async function markAllNotificationsRead() {
  await apiRequest<number>('/api/notifications/read-all', { method: 'POST' })
}

export class NotificationRealtimeClient {
  private socket: WebSocket | null = null
  private buffer = ''
  private reconnectTimer: number | undefined
  private reconnectAttempts = 0
  private stopped = false

  constructor(
    private readonly onNotification: (notification: NotificationItem) => void,
    private readonly onStatus: (status: NotificationSocketStatus) => void,
  ) {}

  connect() {
    this.stopped = false
    this.open()
  }

  disconnect() {
    this.stopped = true
    if (this.reconnectTimer !== undefined) window.clearTimeout(this.reconnectTimer)
    this.reconnectTimer = undefined
    this.socket?.close()
    this.socket = null
    this.onStatus('DISCONNECTED')
  }

  private open() {
    if (this.stopped) return
    this.onStatus('CONNECTING')
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    this.socket = new WebSocket(`${protocol}//${window.location.host}/ws/notifications`)
    this.socket.onopen = () => {
      this.send('CONNECT', {
        'accept-version': '1.2',
        'heart-beat': '10000,10000',
      })
    }
    this.socket.onmessage = (event) => {
      if (typeof event.data === 'string') {
        this.receive(event.data)
      } else if (event.data instanceof ArrayBuffer) {
        this.receive(new TextDecoder().decode(event.data))
      } else if (ArrayBuffer.isView(event.data)) {
        this.receive(new TextDecoder().decode(event.data.buffer))
      } else if (event.data instanceof Blob) {
        void event.data.text().then((data) => this.receive(data))
      } else {
        void new Response(event.data).text().then((data) => this.receive(data))
      }
    }
    this.socket.onerror = () => this.onStatus('ERROR')
    this.socket.onclose = () => {
      this.socket = null
      if (!this.stopped && this.reconnectAttempts < 5) {
        const delay = Math.min(1000 * 2 ** this.reconnectAttempts, 15000)
        this.reconnectAttempts += 1
        this.reconnectTimer = window.setTimeout(() => this.open(), delay)
      } else if (!this.stopped) {
        this.onStatus('ERROR')
      } else {
        this.onStatus('DISCONNECTED')
      }
    }
  }

  private receive(data: string) {
    this.buffer += data
    this.buffer = this.buffer.replace(/^\n+/, '')
    while (this.buffer.includes('\0')) {
      const end = this.buffer.indexOf('\0')
      const raw = this.buffer.slice(0, end)
      this.buffer = this.buffer.slice(end + 1)
      if (!raw.trim()) continue
      const frame = parseFrame(raw)
      if (frame.command === 'CONNECTED') {
        this.reconnectAttempts = 0
        this.send('SUBSCRIBE', {
          id: 'notification-center',
          destination: '/user/queue/notifications',
          ack: 'auto',
        })
        this.send('SEND', {
          destination: '/app/notifications/ready',
          'content-type': 'application/json',
        }, '{}')
      } else if (frame.command === 'MESSAGE') {
        try {
          const message = JSON.parse(frame.body) as NotificationItem & { notificationType?: string }
          if (message.notificationType === 'SUBSCRIPTION_READY') {
            this.onStatus('CONNECTED')
          } else {
            this.onNotification(message)
          }
        } catch {
          this.onStatus('ERROR')
        }
      } else if (frame.command === 'ERROR') {
        this.onStatus('ERROR')
        this.disconnect()
      }
    }
  }

  private send(command: string, headers: Record<string, string>, body = '') {
    this.socket?.send(encodeFrame(command, headers, body))
  }
}

function encodeFrame(command: string, headers: Record<string, string>, body = '') {
  const headerLines = Object.entries(headers).map(([key, value]) => `${key}:${value}`)
  return `${command}\n${headerLines.join('\n')}\n\n${body}\0`
}

function parseFrame(raw: string): StompFrame {
  const separator = raw.indexOf('\n\n')
  const headerText = separator >= 0 ? raw.slice(0, separator) : raw
  const body = separator >= 0 ? raw.slice(separator + 2) : ''
  const lines = headerText.split('\n')
  const headers: Record<string, string> = {}
  for (const line of lines.slice(1)) {
    const index = line.indexOf(':')
    if (index > 0) headers[line.slice(0, index)] = line.slice(index + 1)
  }
  return { command: lines[0], headers, body }
}
