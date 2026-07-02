// GET    /api/spaces/{id}  → 获取单个空间
// PUT    /api/spaces/{id}  → 更新空间名(body: { name })
// DELETE /api/spaces/{id}  → 删除空间
module.exports = ({ method, body, segments }) => {
  if (!globalThis.__wsSpaces) {
    return { error: 'Spaces not initialized', status: 500 }
  }
  // segments 示例: ['spaces', '00000000-0000-0000-0000-000000000001']
  const id = segments[1]
  if (!id) return { error: 'Missing space id', status: 400 }

  const list = globalThis.__wsSpaces
  const idx = list.findIndex((s) => s.id === id)

  // PUT: 重命名
  if (method && method.toUpperCase() === 'PUT') {
    if (idx === -1) return { error: 'Space not found', status: 404 }
    const name = (body && body.name) || list[idx].name
    list[idx] = { ...list[idx], name }
    return { data: list[idx] }
  }

  // DELETE
  if (method && method.toUpperCase() === 'DELETE') {
    if (idx === -1) return { error: 'Space not found', status: 404 }
    list.splice(idx, 1)
    return { data: { success: true, id } }
  }

  // GET
  if (idx === -1) return { error: 'Space not found', status: 404 }
  return { data: list[idx] }
}
