// PUT /api/nodes/{id}?spaceId=xxx → 更新节点，供默认 mock 模式测试编辑器保存。
module.exports = ({ method, body, query, segments }) => {
  if (!method || method.toUpperCase() !== 'PUT') {
    return { error: 'Method not supported by node detail mock', status: 405 }
  }

  const id = segments[1]
  const spaceId = query.spaceId
  if (!id || !spaceId) {
    return { error: 'Missing node id or spaceId', status: 400 }
  }

  if (!globalThis.__wsNodesByKey) globalThis.__wsNodesByKey = new Map()
  const key = `${spaceId}:${id}`
  const current = globalThis.__wsNodesByKey.get(key)
  if (!current) {
    return { error: 'Node not loaded in this mock session', status: 404 }
  }

  const updated = {
    ...current,
    title: body.title ?? current.title,
    content: body.content ?? current.content,
    properties: body.properties ?? current.properties,
    caption: Object.prototype.hasOwnProperty.call(body, 'caption')
      ? body.caption
      : current.caption,
    sort_order: body.sortOrder ?? current.sort_order,
    updated_at: new Date().toISOString(),
  }

  globalThis.__wsNodesByKey.set(key, updated)

  const createdIndex = (globalThis.__wsCreatedNodes || [])
    .findIndex((node) => node.space_id === spaceId && node.id === id)
  if (createdIndex !== -1) {
    globalThis.__wsCreatedNodes[createdIndex] = updated
  }

  return { data: updated }
}
