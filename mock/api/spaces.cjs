// GET  /api/spaces     → 列出当前用户所在空间
// POST /api/spaces     → 创建空间(body: { name })
module.exports = ({ method, body }) => {
  if (!globalThis.__wsSpaces) {
    globalThis.__wsSpaces = [
      {
        id: '00000000-0000-0000-0000-000000000001',
        name: 'My Workspace',
        owner_id: '00000000-0000-0000-0000-0000000000aa',
        root_node_id: 'root-node-1',
        created_at: '2026-06-01T00:00:00Z',
      },
    ]
    globalThis.__wsSpaceIdCounter = 1
  }

  if (method && method.toUpperCase() === 'POST') {
    const name = (body && body.name) || 'Untitled Space'
    globalThis.__wsSpaceIdCounter += 1
    const id = `00000000-0000-0000-0000-${String(globalThis.__wsSpaceIdCounter).padStart(12, '0')}`
    const now = new Date().toISOString()
    const space = {
      id,
      name,
      owner_id: '00000000-0000-0000-0000-0000000000aa',
      root_node_id: 'root-node-1',
      created_at: now,
    }
    globalThis.__wsSpaces.push(space)
    return { data: space }
  }

  // GET
  return { data: [...globalThis.__wsSpaces] }
}
