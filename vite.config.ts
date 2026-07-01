import { fileURLToPath, URL } from 'node:url'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createRequire } from 'node:module'
import type { IncomingMessage } from 'node:http'
import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'

const mockRequire = createRequire(import.meta.url)

function localApiMock(): Plugin {
  const mockRoot = fileURLToPath(new URL('./mock/api', import.meta.url))

  function resolveMockFile(segments: string[]): string | null {
    function walk(dir: string, i: number): string | null {
      const isLast = i === segments.length - 1
      for (const seg of [segments[i], '_']) {
        if (isLast) {
          for (const ext of ['.cjs', '.json']) {
            const p = resolve(dir, `${seg}${ext}`)
            if (existsSync(p)) return p
          }
          continue
        }
        const p = resolve(dir, seg)
        if (existsSync(p)) {
          const found = walk(p, i + 1)
          if (found) return found
        }
      }
      return null
    }
    return segments.length ? walk(mockRoot, 0) : null
  }

  function readBody(req: IncomingMessage): Promise<unknown> {
    return new Promise((res) => {
      let raw = ''
      req.on('data', (c) => (raw += c))
      req.on('end', () => {
        try { res(raw ? JSON.parse(raw) : {}) }
        catch { res({}) }
      })
      req.on('error', () => res({}))
    })
  }

  return {
    name: 'workspace-local-api-mock',
    apply: 'serve',
    configureServer(server) {
      if (!existsSync(mockRoot)) return
      server.middlewares.use('/api', async (req, res, next) => {
        const path = (req.url ?? '').split('?')[0]
        const segments = path.split('/').filter(Boolean)
        if (!segments.length || segments.some((s) => s.startsWith('.'))) return next()
        const file = resolveMockFile(segments)
        if (!file) return next()
        const requestId = `mock-${Date.now()}`
        try {
          if (file.endsWith('.cjs')) {
            const body = await readBody(req)
            const query = Object.fromEntries(new URL(req.url ?? '', 'http://x').searchParams)
            delete mockRequire.cache[mockRequire.resolve(file)]
            const fn = mockRequire(file) as (ctx: { method?: string; body: unknown; query: Record<string, string> }) => { data?: unknown; error?: unknown; status?: number }
            const out = fn({ method: req.method, body, query }) ?? {}
            res.setHeader('Content-Type', 'application/json')
            if (out.error) {
              res.statusCode = out.status ?? 400
              res.end(JSON.stringify({ requestId, error: out.error }))
            } else {
              res.statusCode = out.status ?? 200
              res.end(JSON.stringify({ requestId, data: out.data ?? null }))
            }
            return
          }
          const data = JSON.parse(readFileSync(file, 'utf-8'))
          res.setHeader('Content-Type', 'application/json')
          res.end(JSON.stringify({ requestId, data }))
        } catch (e) {
          res.statusCode = 500
          res.end(JSON.stringify({ requestId: 'mock-error', error: { code: 'INTERNAL_ERROR', message: String(e), retryable: false } }))
        }
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), localApiMock()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
