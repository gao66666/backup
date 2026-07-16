import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

export type WorkspaceNode = {
  id: string
  title: string
  type: string
  parent_id: string | null
  has_children: boolean
  content?: string
  properties?: string
  caption?: string | null
  sort_order?: number
  is_deleted?: boolean
  updated_at?: string
  updated_by?: string
}

export type WorkspaceNodeEvent = {
  eventId: string
  type: 'node.created' | 'node.updated' | 'node.moved' | 'node.deleted'
  spaceId: string
  nodeId: string
  actorUserId: string
  occurredAt: string
  node: WorkspaceNode
  oldParent: WorkspaceNode | null
  newParent: WorkspaceNode | null
}

type WorkspaceEventsOptions = {
  spaceId: string
  getToken: () => Promise<string>
  onEvent: (event: WorkspaceNodeEvent) => void
  onConnected: () => void
  onDisconnected?: () => void
  onError?: (error: Error) => void
}

export type WorkspaceEventsConnection = {
  disconnect: () => Promise<void>
}

function getWorkspaceEventsURL(): string {
  const configured = import.meta.env.VITE_WORKSPACE_WEBSOCKET_URL?.trim()
  if (configured) return configured

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/workspace`
}

export function workspaceEventsEnabled(): boolean {
  if (import.meta.env.VITE_WORKSPACE_EVENTS_ENABLED === 'false') return false
  return Boolean(
    import.meta.env.PROD
    || import.meta.env.VITE_API_MODE === 'real'
    || import.meta.env.VITE_WORKSPACE_WEBSOCKET_URL,
  )
}

function parseEvent(message: IMessage): WorkspaceNodeEvent {
  const parsed = JSON.parse(message.body) as Partial<WorkspaceNodeEvent>
  if (
    typeof parsed.eventId !== 'string'
    || typeof parsed.type !== 'string'
    || typeof parsed.spaceId !== 'string'
    || typeof parsed.nodeId !== 'string'
    || !parsed.node
  ) {
    throw new Error('Invalid workspace node event payload')
  }
  return parsed as WorkspaceNodeEvent
}

export function connectWorkspaceEvents(
  options: WorkspaceEventsOptions,
): WorkspaceEventsConnection {
  let subscription: StompSubscription | null = null
  let active = true

  const client = new Client({
    brokerURL: getWorkspaceEventsURL(),
    reconnectDelay: 2_000,
    connectionTimeout: 10_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    discardWebsocketOnCommFailure: true,
    debug: () => {},
    beforeConnect: async (stompClient) => {
      const token = await options.getToken()
      stompClient.connectHeaders = {
        Authorization: `Bearer ${token}`,
      }
    },
    onConnect: () => {
      if (!active) return
      subscription?.unsubscribe()
      subscription = client.subscribe(
        `/topic/spaces/${options.spaceId}/nodes`,
        (message) => {
          try {
            options.onEvent(parseEvent(message))
          } catch (error) {
            options.onError?.(
              error instanceof Error ? error : new Error(String(error)),
            )
          }
        },
      )
      options.onConnected()
    },
    onStompError: (frame) => {
      options.onError?.(
        new Error(frame.headers.message || frame.body || 'STOMP broker error'),
      )
    },
    onWebSocketError: () => {
      options.onError?.(new Error('Workspace event WebSocket connection failed'))
    },
    onWebSocketClose: () => {
      subscription = null
      if (active) options.onDisconnected?.()
    },
  })

  client.activate()

  return {
    async disconnect() {
      if (!active) return
      active = false
      subscription?.unsubscribe()
      subscription = null
      await client.deactivate()
    },
  }
}
