// GET  /api/nodes?spaceId=xxx&parentId=yyy  → 根节点(parentId 缺省)或某父节点的子节点
// POST /api/nodes                            → 创建节点，返回新节点
//      body: { spaceId, parentId, type, title, content, sortOrder }
module.exports = ({ method, body, query }) => {
  const { spaceId, parentId } = query

  // 当前 dev server 会话内的节点状态。GET/POST/PUT 共享，用于测试编辑保存。
  if (!globalThis.__wsNodesByKey) globalThis.__wsNodesByKey = new Map()
  const nodeKey = (node) => `${node.space_id}:${node.id}`
  const rememberNode = (node) => {
    const key = nodeKey(node)
    const remembered = globalThis.__wsNodesByKey.get(key)
    const current = remembered ? { ...node, ...remembered } : node
    globalThis.__wsNodesByKey.set(key, current)
    return current
  }

  // 跨请求持久化的新建节点（globalThis 不受 require cache 重置影响，重启 dev 才清空）
  if (!globalThis.__wsCreatedNodes) globalThis.__wsCreatedNodes = []
  const createdForSpace = globalThis.__wsCreatedNodes
    .filter((n) => n.space_id === spaceId)
    .map(rememberNode)

  // ---- POST: 创建节点 ----
  if (method && method.toUpperCase() === 'POST') {
    globalThis.__wsNodeId = (globalThis.__wsNodeId || 0) + 1
    const now = '2026-06-01T00:00:00Z'
    const newNode = {
      id: `node-new-${globalThis.__wsNodeId}`,
      space_id: body.spaceId || spaceId,
      parent_id: body.parentId ?? null,
      type: body.type || 'doc',
      title: body.title || '未命名',
      content: body.content || '{}',
      properties: body.properties || '{}',
      caption: body.caption ?? null,
      description: '',
      sort_order: body.sortOrder ?? 1,
      is_deleted: false,
      created_by: 'user-1',
      created_at: now,
      updated_at: now,
      deleted_at: null,
      has_children: false,
    }
    globalThis.__wsCreatedNodes.push(newNode)
    return { data: rememberNode(newNode) }
  }

  const rootNodes = [
    {
      id: 'node-1',
      space_id: spaceId,
      parent_id: null,
      type: 'collection',
      title: 'docs',
      content: '{}',
      properties: '{}',
      description: 'Documents folder',
      sort_order: 1,
      is_deleted: false,
      created_by: 'user-1',
      created_at: '2026-06-01T00:00:00Z',
      updated_at: '2026-06-01T00:00:00Z',
      deleted_at: null,
      has_children: true,
    },
    {
      id: 'node-2',
      space_id: spaceId,
      parent_id: null,
      type: 'collection',
      title: 'design',
      content: '{}',
      properties: '{}',
      description: 'Design folder',
      sort_order: 2,
      is_deleted: false,
      created_by: 'user-1',
      created_at: '2026-06-01T00:00:00Z',
      updated_at: '2026-06-01T00:00:00Z',
      deleted_at: null,
      has_children: true,
    },
    {
      id: 'node-3',
      space_id: spaceId,
      parent_id: null,
      type: 'collection',
      title: 'media',
      content: '{}',
      properties: '{}',
      description: 'Media folder',
      sort_order: 3,
      is_deleted: false,
      created_by: 'user-1',
      created_at: '2026-06-01T00:00:00Z',
      updated_at: '2026-06-01T00:00:00Z',
      deleted_at: null,
      has_children: true,
    },
  ]

  const childNodes = {
    'node-1': [
      {
        id: 'node-1-1',
        space_id: spaceId,
        parent_id: 'node-1',
        type: 'doc',
        title: 'workspace-overview.md',
        content: '{"text": "# Overview"}',
        properties: '{"kind": "text"}',
        description: 'Overview document',
        sort_order: 1,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
      {
        id: 'node-1-2',
        space_id: spaceId,
        parent_id: 'node-1',
        type: 'doc',
        title: 'release-notes.md',
        content: '{"text": "# Release Notes"}',
        properties: '{"kind": "text"}',
        description: 'Release notes',
        sort_order: 2,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
    ],
    'node-2': [
      {
        id: 'node-2-1',
        space_id: spaceId,
        parent_id: 'node-2',
        type: 'doc',
        title: 'campaign-plan.fig',
        content: '{}',
        properties: '{"kind": "text"}',
        description: 'Campaign plan',
        sort_order: 1,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
      {
        id: 'node-2-2',
        space_id: spaceId,
        parent_id: 'node-2',
        type: 'doc',
        title: 'hero-layout.vue',
        content: '{"text": "<template>...</template>"}',
        properties: '{"kind": "text"}',
        description: 'Hero layout component',
        sort_order: 2,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
    ],
    'node-3': [
      {
        id: 'node-3-1',
        space_id: spaceId,
        parent_id: 'node-3',
        type: 'image',
        title: 'hero.png',
        content: '{}',
        properties: '{"kind": "image", "src": "https://picsum.photos/800/400"}',
        description: 'Hero image',
        sort_order: 1,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
      {
        id: 'node-3-2',
        space_id: spaceId,
        parent_id: 'node-3',
        type: 'video',
        title: 'folder-tour.mp4',
        content: '{}',
        properties: '{"kind": "video", "src": "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"}',
        description: 'Folder tour video',
        sort_order: 2,
        is_deleted: false,
        created_by: 'user-1',
        created_at: '2026-06-01T00:00:00Z',
        updated_at: '2026-06-01T00:00:00Z',
        deleted_at: null,
        has_children: false,
      },
    ],
  }

  // GET：把持久化的新建节点合并进对应层级
  const createdUnder = (pid) =>
    createdForSpace.filter((n) => (pid === null ? n.parent_id === null : n.parent_id === pid))

  if (!parentId) {
    return { data: [...rootNodes.map(rememberNode), ...createdUnder(null)] }
  }
  return {
    data: [
      ...(childNodes[parentId] || []).map(rememberNode),
      ...createdUnder(parentId),
    ],
  }
}
