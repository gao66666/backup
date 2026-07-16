import { Server } from '@hocuspocus/server'
import { encodeStateAsUpdate } from 'yjs'

const defaultInternalToken =
  'dev-only-collaboration-internal-token-change-me'
const port = Number.parseInt(process.env.HOCUSPOCUS_PORT ?? '1234', 10)
const address = process.env.HOCUSPOCUS_HOST?.trim() || '0.0.0.0'
const schemaVersion = Number.parseInt(
  process.env.YJS_SCHEMA_VERSION ?? '1',
  10,
)
const persistenceTimeoutMs = Number.parseInt(
  process.env.COLLABORATION_PERSISTENCE_TIMEOUT_MS ?? '5000',
  10,
)
const persistenceMaxAttempts = Number.parseInt(
  process.env.COLLABORATION_PERSISTENCE_MAX_ATTEMPTS ?? '3',
  10,
)
const maxDocumentBytes = Number.parseInt(
  process.env.COLLABORATION_MAX_DOCUMENT_BYTES ?? '16777216',
  10,
)
const internalToken = process.env.COLLABORATION_INTERNAL_TOKEN
  ?? defaultInternalToken
const backendURL = new URL(
  process.env.COLLABORATION_BACKEND_URL?.trim()
    || 'http://127.0.0.1:8080',
)

if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  throw new Error(`Invalid HOCUSPOCUS_PORT: ${process.env.HOCUSPOCUS_PORT}`)
}
if (!Number.isInteger(schemaVersion) || schemaVersion <= 0 || schemaVersion > 32767) {
  throw new Error(`Invalid YJS_SCHEMA_VERSION: ${process.env.YJS_SCHEMA_VERSION}`)
}
if (
  !Number.isInteger(persistenceTimeoutMs)
  || persistenceTimeoutMs < 250
  || persistenceTimeoutMs > 60_000
) {
  throw new Error(
    `Invalid COLLABORATION_PERSISTENCE_TIMEOUT_MS: ${
      process.env.COLLABORATION_PERSISTENCE_TIMEOUT_MS
    }`,
  )
}
if (
  !Number.isInteger(persistenceMaxAttempts)
  || persistenceMaxAttempts < 1
  || persistenceMaxAttempts > 10
) {
  throw new Error(
    `Invalid COLLABORATION_PERSISTENCE_MAX_ATTEMPTS: ${
      process.env.COLLABORATION_PERSISTENCE_MAX_ATTEMPTS
    }`,
  )
}
if (
  !Number.isInteger(maxDocumentBytes)
  || maxDocumentBytes <= 0
  || maxDocumentBytes > 2_147_483_647
) {
  throw new Error(
    `Invalid COLLABORATION_MAX_DOCUMENT_BYTES: ${
      process.env.COLLABORATION_MAX_DOCUMENT_BYTES
    }`,
  )
}
if (!['http:', 'https:'].includes(backendURL.protocol)) {
  throw new Error(
    `COLLABORATION_BACKEND_URL must use http or https: ${backendURL}`,
  )
}
if (!internalToken.trim()) {
  throw new Error('COLLABORATION_INTERNAL_TOKEN must not be blank')
}
if (
  process.env.NODE_ENV === 'production'
  && internalToken === defaultInternalToken
) {
  throw new Error(
    'COLLABORATION_INTERNAL_TOKEN must be configured in production',
  )
}

const uuidPattern =
  '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
const documentNamePattern = new RegExp(
  `^space:(${uuidPattern}):node:(${uuidPattern})$`,
  'i',
)

function parseDocumentName(documentName) {
  const match = documentNamePattern.exec(documentName)
  if (!match) {
    throw new Error(`Invalid collaboration document name: ${documentName}`)
  }

  return {
    spaceId: match[1],
    nodeId: match[2],
  }
}

function wait(milliseconds) {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds)
  })
}

function shouldRetry(response) {
  return response.status === 408
    || response.status === 429
    || response.status >= 500
}

async function requestPersistence(path, options = {}) {
  const method = options.method ?? 'GET'
  let lastError = null

  for (let attempt = 1; attempt <= persistenceMaxAttempts; attempt += 1) {
    try {
      const response = await fetch(new URL(path, backendURL), {
        ...options,
        headers: {
          'X-Collaboration-Token': internalToken,
          ...options.headers,
        },
        signal: AbortSignal.timeout(persistenceTimeoutMs),
      })

      if (!shouldRetry(response) || attempt === persistenceMaxAttempts) {
        return response
      }

      await response.arrayBuffer()
      lastError = new Error(`HTTP ${response.status}`)
    } catch (error) {
      lastError = error
      if (attempt === persistenceMaxAttempts) break
    }

    await wait(Math.min(100 * (2 ** (attempt - 1)), 1_000))
  }

  throw new Error(
    `Persistence request failed after ${persistenceMaxAttempts} attempts: `
      + `${method} ${path}: ${lastError?.message ?? 'unknown error'}`,
    { cause: lastError },
  )
}

async function requireSuccess(response, operation) {
  if (response.ok) return

  const responseText = (await response.text()).slice(0, 500)
  throw new Error(
    `${operation} failed with HTTP ${response.status}`
      + (responseText ? `: ${responseText}` : ''),
  )
}

function documentPath(spaceId, nodeId) {
  return `/internal/collaboration/documents/${encodeURIComponent(nodeId)}`
    + `?spaceId=${encodeURIComponent(spaceId)}`
}

const server = new Server({
  name: 'workspace-collaboration',
  port,
  address,
  stopOnSignals: false,
  quiet: true,
  timeout: 60_000,
  debounce: 2_000,
  maxDebounce: 10_000,
  unloadImmediately: false,
  websocketOptions: {
    // A sync message contains the Yjs state plus protocol/document-name bytes.
    maxPayload: maxDocumentBytes + (64 * 1024),
  },
  onListen({ port: listeningPort }) {
    console.log(
      `[collaboration] Hocuspocus listening on ws://${address}:${listeningPort}`,
    )
    console.log(
      `[collaboration] Persistence backend: ${backendURL.origin}`,
    )
  },
  async onLoadDocument({ documentName }) {
    const { spaceId, nodeId } = parseDocumentName(documentName)
    const response = await requestPersistence(
      documentPath(spaceId, nodeId),
      {
        headers: {
          Accept: 'application/octet-stream',
        },
      },
    )

    if (response.status === 204) {
      return null
    }

    await requireSuccess(response, `Load ${documentName}`)
    const persistedSchemaVersion = Number.parseInt(
      response.headers.get('X-Yjs-Schema-Version') ?? '',
      10,
    )
    if (
      !Number.isInteger(persistedSchemaVersion)
      || persistedSchemaVersion !== schemaVersion
    ) {
      throw new Error(
        `Unsupported collaboration schema version `
          + `${response.headers.get('X-Yjs-Schema-Version') ?? 'missing'} `
          + `for ${documentName}; expected ${schemaVersion}`,
      )
    }

    const state = new Uint8Array(await response.arrayBuffer())
    if (state.byteLength === 0) {
      throw new Error(`Loaded empty Yjs state for ${documentName}`)
    }
    if (state.byteLength > maxDocumentBytes) {
      throw new Error(
        `Loaded Yjs state for ${documentName} exceeds `
          + `${maxDocumentBytes} bytes`,
      )
    }
    return state
  },
  async onStoreDocument({ documentName, document }) {
    const { spaceId, nodeId } = parseDocumentName(documentName)
    const state = encodeStateAsUpdate(document)
    if (state.byteLength > maxDocumentBytes) {
      throw new Error(
        `Yjs state for ${documentName} exceeds ${maxDocumentBytes} bytes`,
      )
    }
    const response = await requestPersistence(
      documentPath(spaceId, nodeId),
      {
        method: 'PUT',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/octet-stream',
          'X-Yjs-Schema-Version': String(schemaVersion),
        },
        body: state,
      },
    )

    await requireSuccess(response, `Store ${documentName}`)
  },
})

async function ensurePersistenceReady() {
  const response = await requestPersistence(
    '/internal/collaboration/health',
    {
      headers: {
        Accept: 'application/json',
      },
    },
  )
  await requireSuccess(response, 'Collaboration persistence health check')
}

let shutdownPromise = null

async function shutdown(signal) {
  if (shutdownPromise) return shutdownPromise

  shutdownPromise = (async () => {
    console.log(`[collaboration] Received ${signal}, shutting down...`)
    await server.destroy()
  })()

  try {
    await shutdownPromise
    process.exit(0)
  } catch (error) {
    console.error('[collaboration] Graceful shutdown failed:', error)
    process.exit(1)
  }
}

for (const signal of ['SIGINT', 'SIGQUIT', 'SIGTERM']) {
  process.once(signal, () => {
    void shutdown(signal)
  })
}

await ensurePersistenceReady()
await server.listen()
