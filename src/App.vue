<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import CollaborativeEditor from './components/CollaborativeEditor.vue'
import FileTreeItem from './components/FileTreeItem.vue'
import {
  connectWorkspaceEvents,
  workspaceEventsEnabled,
  type WorkspaceEventsConnection,
  type WorkspaceNode,
  type WorkspaceNodeEvent,
} from './services/workspaceEvents'

type ResolvedWorkspaceNode = {
  id: string
  type: string
  src: string
}

// Milkdown 中的 /workspace/... 链接和媒体地址需要按需加载文件树后再解析。
async function resolveNodeByPath(path: string): Promise<ResolvedWorkspaceNode | null> {
  const clean = path.replace(/^\/workspace\/?/, '')
  if (!clean) return null
  const segments = clean.split('/')
  let parentId: string | null = null

  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i]
    const isLast = i === segments.length - 1
    let found = [...nodesMap.value.values()]
      .find(n => n.parent_id === parentId && n.title === seg)

    if (!found) {
      if (parentId) {
        const parent = nodesMap.value.get(parentId)
        if (parent?.has_children) {
          await loadChildren(parentId)
          found = [...nodesMap.value.values()]
            .find(n => n.parent_id === parentId && n.title === seg)
        }
      }
      if (!found) return null
    }

    if (isLast) {
      return { id: found.id, type: found.type, src: getNodeSrc(found.id) }
    }

    if (
      found.has_children
      && ![...nodesMap.value.values()].some(n => n.parent_id === found.id)
    ) {
      await loadChildren(found.id)
    }
    parentId = found.id
  }

  return null
}

// ── 视图切换 ──
const currentView = ref<'spaces' | 'workspace'>('spaces')
const currentSpaceId = ref<string | null>(null)

// ── Spaces 列表 ──
type Space = {
  id: string
  name: string
  owner_id: string
  root_node_id: string
  created_at: string
}
const spaces = ref<Space[]>([])
const spacesLoading = ref(false)
const creatingSpace = ref(false)
const newSpaceName = ref('')
const renamingSpaceId = ref<string | null>(null)
const renameSpaceInput = ref('')

type ApiNode = WorkspaceNode & {
  dirty?: boolean   // 本地未保存标记,不入后端
}

const nodesMap = ref<Map<string, ApiNode>>(new Map())
const expanded = ref(new Set<string>())
const selected = ref<string | null>(null)
const renamingId = ref<string | null>(null)   // 当前正在重命名的节点 ID(FileTreeItem 据此切 input)

type CollaborativeEditorHandle = {
  getMarkdown: () => string
  insertMarkdown: (markdown: string, inline?: boolean) => void
  focus: () => void
}
const editorRef = ref<CollaborativeEditorHandle | null>(null)

// ── 标签页 ──
type OpenTab = { id: string; title: string; type: string }
const openTabs = ref<OpenTab[]>([])

function openTab(nodeId: string) {
  const node = nodesMap.value.get(nodeId)
  if (!node || node.type === 'collection') return
  if (!openTabs.value.find(t => t.id === nodeId)) {
    openTabs.value.push({ id: node.id, title: node.title, type: node.type })
  }
  selected.value = nodeId
}

function closeTab(nodeId: string) {
  const idx = openTabs.value.findIndex(t => t.id === nodeId)
  if (idx === -1) return

  if (selected.value === nodeId) {
    const latestContent = syncActiveEditorContent()
    const node = nodesMap.value.get(nodeId)
    if (node?.dirty) {
      void saveNodeContent(nodeId, latestContent, false)
    }
  }

  openTabs.value.splice(idx, 1)
  if (selected.value === nodeId) {
    // 切到相邻标签，没有则清空编辑区
    const next = openTabs.value[idx] || openTabs.value[idx - 1]
    selected.value = next?.id ?? null
  }
}
const loading = ref(false)
const editContent = ref('')
const savingNow = ref(false)               // 保存进行中(按钮 disabled 用)
const loadingContent = ref(false)          // 程序设置 editContent 时为 true(watch(editContent) 跳过,避免误标 dirty)

function syncActiveEditorContent(): string {
  const latestContent = editorRef.value?.getMarkdown() ?? editContent.value
  if (latestContent !== editContent.value) {
    editContent.value = latestContent
  }
  return latestContent
}

// 监听 editContent 变化,标记 dirty(直接 Map.set 不比较)
watch(editContent, () => {
  if (loadingContent.value) return   // 程序设置值,不标 dirty
  if (!selected.value) return
  const orig = nodesMap.value.get(selected.value)
  if (!orig || orig.dirty) return
  const newMap = new Map(nodesMap.value)
  newMap.set(selected.value, { ...orig, dirty: true })
  nodesMap.value = newMap
})
const draggingId = ref<string | null>(null)
const dropTargetId = ref<string | null>(null)

function onDragStart(nodeId: string, e: DragEvent) {
  draggingId.value = nodeId
  e.dataTransfer?.setData('application/x-workspace-node', nodeId)
  e.dataTransfer?.setData('text/plain', nodeId)
}

function onDragOver(nodeId: string, e: DragEvent) {
  e.preventDefault()
  dropTargetId.value = nodeId
}

function onDrop(targetId: string, e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()  // 防止冒泡到 root ul 触发 onDropRoot 造成双重移动
  const draggedId = e.dataTransfer?.getData('text/plain') || draggingId.value
  if (!draggedId || draggedId === targetId) {
    draggingId.value = null
    dropTargetId.value = null
    return
  }
  // Drop as first child of target
  const children = [...nodesMap.value.values()].filter(n => n.parent_id === targetId)
  const sortOrder = children.length === 0 ? 1.0 : (children[0].sort_order ?? 1) / 2
  const spaceId = currentSpaceId.value!
  nodeService.move(spaceId, draggedId, targetId, sortOrder)
  draggingId.value = null
  dropTargetId.value = null
}

function onDragOverRoot(e: DragEvent) {
  e.preventDefault()
  // 只在鼠标真的悬停在根 ul 空白区域时才标识为 root drop;
  // 子节点冒泡上来的 dragover 忽略(它们的 onDragOver 已设 dropTargetId)。
  if (e.target === e.currentTarget) {
    dropTargetId.value = null
  }
}

function onDropRoot(e: DragEvent) {
  e.preventDefault()
  const draggedId = e.dataTransfer?.getData('text/plain') || draggingId.value
  if (!draggedId) {
    draggingId.value = null
    dropTargetId.value = null
    return
  }
  // Drop to root:排到根节点末尾
  const rootNodes = [...nodesMap.value.values()]
    .filter(n => n.parent_id === null)
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
  const sortOrder = rootNodes.length === 0
    ? 1.0
    : (rootNodes[rootNodes.length - 1].sort_order ?? 0) + 1.0
  nodeService.move(currentSpaceId.value!, draggedId, null, sortOrder)
  draggingId.value = null
  dropTargetId.value = null
}

// ── 拖文件到编辑区 → 在 Milkdown 当前光标处插入 Markdown/HTML ──
function onEditorDrop(e: DragEvent) {
  const nodeId = e.dataTransfer?.getData('application/x-workspace-node')
    || e.dataTransfer?.getData('text/plain')
    || draggingId.value
  if (!nodeId) return
  const node = nodesMap.value.get(nodeId)
  if (!node || node.type === 'collection') return
  e.preventDefault()

  const path = getNodePath(nodeId)
  let syntax: string
  if (node.type === 'image') {
    syntax = `![${node.title}](${path})`
  } else if (node.type === 'video') {
    syntax = `<video src="${path}" controls></video>`
  } else if (node.type === 'audio') {
    syntax = `<audio src="${path}" controls></audio>`
  } else {
    syntax = `[${node.title}](${path})`
  }

  editorRef.value?.insertMarkdown(syntax, true)
  draggingId.value = null
  dropTargetId.value = null
}

async function onEditorNavigate(path: string) {
  const node = await resolveNodeByPath(path)
  if (node) handleSelect(node.id)
}

async function resolveEditorMedia(path: string): Promise<string> {
  if (!path.startsWith('/workspace')) return path
  const node = await resolveNodeByPath(path)
  return node?.src || path
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const fileUploadTarget = ref<string | null>(null)  // 上传到的父节点 ID(null = 根)

/**
 * 根据文件名后缀推断节点 type。无后缀 → collection(文件夹)。
 * 真实类型映射(简易版,够 workspace 当前用)。
 */
function inferNodeTypeFromName(name: string): { type: ApiNode['type']; kind: string } {
  const lower = name.toLowerCase()
  // 文档
  if (/\.(md|txt|doc|docx|pdf|json|log)$/.test(lower)) return { type: 'doc', kind: 'text' }
  // 图片
  if (/\.(png|jpe?g|gif|webp|svg|bmp)$/.test(lower)) return { type: 'image', kind: 'image' }
  // 视频
  if (/\.(mp4|mov|avi|webm|mkv)$/.test(lower)) return { type: 'video', kind: 'video' }
  // 音频
  if (/\.(mp3|wav|ogg|flac|m4a)$/.test(lower)) return { type: 'audio', kind: 'audio' }
  // 其他后缀或无后缀(没有 . 的)=> 兜底 collection
  if (!/\./.test(name)) return { type: 'collection', kind: '' }
  return { type: 'doc', kind: 'text' }
}

function triggerUpload() {
  fileUploadTarget.value = null  // 顶部按钮 → 根
  fileInputRef.value?.click()
}

function triggerUploadToFolder(nodeId: string) {
  fileUploadTarget.value = nodeId
  fileInputRef.value?.click()
}

async function onFileSelected(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files?.length) return
  const parentId = fileUploadTarget.value
  for (const file of Array.from(files)) {
    await uploadAndCreateNode(parentId, file)
  }
  ;(e.target as HTMLInputElement).value = ''
  fileUploadTarget.value = null   // 重置,避免影响下次点击
}

/**
 * 上传文件到 MinIO,然后在指定父节点(parentId)下创建一个节点。
 * parentId = null 表示放在根。
 */
async function uploadAndCreateNode(parentId: string | null, file: File) {
  const token = await getToken()
  const form = new FormData()
  form.append('file', file)
  let url: string
  try {
    const res = await fetch('/api/uploads', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },
      body: form,
    })
    if (!res.ok) {
      console.error('upload failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    url = json.data?.url
    if (!url) {
      console.error('upload response missing url')
      return
    }
  } catch (err) {
    console.error('upload error:', err)
    return
  }

  // 推断 type 和 kind
  const mime = file.type
  let type: 'image' | 'video' | 'audio' | 'doc' = 'doc'
  let kind = 'text'
  if (mime.startsWith('image/')) { type = 'image'; kind = 'image' }
  else if (mime.startsWith('video/')) { type = 'video'; kind = 'video' }
  else if (mime.startsWith('audio/')) { type = 'audio'; kind = 'audio' }

  // 找 sibling 末尾,作为新节点的 sort_order
  const siblings = [...nodesMap.value.values()]
    .filter(n => n.parent_id === parentId)
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
  const sortOrder = siblings.length === 0
    ? 1.0
    : (siblings[siblings.length - 1].sort_order ?? 0) + 1.0

  try {
    const res = await fetch(`/api/nodes?spaceId=${currentSpaceId.value}`, {
      method: 'POST',
      headers: await authHeaders(),
      body: JSON.stringify({
        parentId,
        type,
        title: file.name,
        content: '{}',
        properties: JSON.stringify({ kind, src: url }),
        sortOrder,
      }),
    })
    if (!res.ok) {
      console.error('create node after upload failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    const node = json.data as ApiNode | undefined
    if (!node) return
    // 乐观更新:加到 Map,展开父
    const newMap = new Map(nodesMap.value)
    newMap.set(node.id, node)
    if (parentId) {
      const parent = newMap.get(parentId)
      if (parent) newMap.set(parentId, { ...parent, has_children: true })
      expanded.value.add(parentId)
    }
    nodesMap.value = newMap
    selected.value = node.id
  } catch (err) {
    console.error('create node error:', err)
  }
}

// ⚠️ 开发期鉴权:启动时调 /api/dev/login 拿 JWT,后续 fetch 全用 Bearer token。
// 真合并 Monsora 时,这块换成 Monsora 的登录端点。
const DEV_USER_ID = '00000000-0000-0000-0000-0000000000aa'
let cachedToken: string | null = null

async function getToken(): Promise<string> {
  if (cachedToken) return cachedToken
  const res = await fetch(`/api/dev/login?userId=${DEV_USER_ID}`)
  if (!res.ok) throw new Error(`dev login failed: ${res.status}`)
  const json = await res.json()
  cachedToken = json.data.token
  return cachedToken!
}

async function authHeaders(): Promise<HeadersInit> {
  const token = await getToken()
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  }
}

let workspaceEventsConnection: WorkspaceEventsConnection | null = null
let workspaceEventsGeneration = 0
let workspaceTreeEventVersion = 0
const seenWorkspaceEventIds = new Set<string>()

function rememberWorkspaceEvent(eventId: string): boolean {
  if (seenWorkspaceEventIds.has(eventId)) return false
  seenWorkspaceEventIds.add(eventId)
  if (seenWorkspaceEventIds.size > 512) {
    const oldest = seenWorkspaceEventIds.values().next().value
    if (oldest) seenWorkspaceEventIds.delete(oldest)
  }
  return true
}

function mergeNodeIntoMap(target: Map<string, ApiNode>, incoming: WorkspaceNode) {
  const existing = target.get(incoming.id)
  const merged: ApiNode = {
    ...existing,
    ...incoming,
  }
  if (existing?.dirty) {
    merged.dirty = true
    merged.content = existing.content
  }
  target.set(incoming.id, merged)
}

function syncOpenTabsFromNodes() {
  openTabs.value = openTabs.value.map((tab) => {
    const node = nodesMap.value.get(tab.id)
    return node
      ? { id: node.id, title: node.title, type: node.type }
      : tab
  })
}

function collectSubtreeIds(
  rootId: string,
  source: Map<string, ApiNode>,
): Set<string> {
  const result = new Set<string>()
  const pending = [rootId]
  while (pending.length) {
    const current = pending.pop()!
    if (result.has(current)) continue
    result.add(current)
    for (const node of source.values()) {
      if (node.parent_id === current) pending.push(node.id)
    }
  }
  return result
}

function removeNodeIdsFromUi(removedIds: Set<string>) {
  if (!removedIds.size) return

  const selectedTabIndex = selected.value
    ? openTabs.value.findIndex(tab => tab.id === selected.value)
    : -1
  openTabs.value = openTabs.value.filter(tab => !removedIds.has(tab.id))

  const nextExpanded = new Set(expanded.value)
  removedIds.forEach(id => nextExpanded.delete(id))
  expanded.value = nextExpanded

  if (renamingId.value && removedIds.has(renamingId.value)) {
    renamingId.value = null
  }

  if (selected.value && removedIds.has(selected.value)) {
    const nextIndex = Math.min(
      Math.max(selectedTabIndex, 0),
      openTabs.value.length - 1,
    )
    selected.value = nextIndex >= 0 ? openTabs.value[nextIndex]?.id ?? null : null
  }
}

function removeNodeSubtree(rootId: string) {
  const removedIds = collectSubtreeIds(rootId, nodesMap.value)
  const newMap = new Map(nodesMap.value)
  removedIds.forEach(id => newMap.delete(id))
  nodesMap.value = newMap
  removeNodeIdsFromUi(removedIds)
}

function applyWorkspaceNodeEvent(event: WorkspaceNodeEvent) {
  if (
    event.spaceId !== currentSpaceId.value
    || !rememberWorkspaceEvent(event.eventId)
  ) {
    return
  }

  workspaceTreeEventVersion += 1

  if (event.type === 'node.deleted') {
    removeNodeSubtree(event.nodeId)
    if (event.oldParent) {
      const newMap = new Map(nodesMap.value)
      mergeNodeIntoMap(newMap, event.oldParent)
      nodesMap.value = newMap
    }
    syncOpenTabsFromNodes()
    return
  }

  const newMap = new Map(nodesMap.value)
  mergeNodeIntoMap(newMap, event.node)

  if (event.type === 'node.created' && event.node.parent_id) {
    const parent = newMap.get(event.node.parent_id)
    if (parent) {
      newMap.set(parent.id, { ...parent, has_children: true })
    }
  }
  if (event.oldParent) mergeNodeIntoMap(newMap, event.oldParent)
  if (event.newParent) mergeNodeIntoMap(newMap, event.newParent)

  nodesMap.value = newMap
  syncOpenTabsFromNodes()
}

async function fetchNodeScope(
  spaceId: string,
  parentId: string | null,
): Promise<ApiNode[]> {
  const parentQuery = parentId
    ? `&parentId=${encodeURIComponent(parentId)}`
    : ''
  const res = await fetch(
    `/api/nodes?spaceId=${encodeURIComponent(spaceId)}${parentQuery}`,
    { headers: await authHeaders() },
  )
  if (!res.ok) {
    throw new Error(`load node scope failed: ${res.status}`)
  }
  const json = await res.json()
  return (json.data ?? []) as ApiNode[]
}

async function fetchNodeIfPresent(
  spaceId: string,
  nodeId: string,
): Promise<ApiNode | null | undefined> {
  try {
    const res = await fetch(
      `/api/nodes/${encodeURIComponent(nodeId)}?spaceId=${encodeURIComponent(spaceId)}`,
      { headers: await authHeaders() },
    )
    if (res.status === 404) return null
    if (!res.ok) return undefined
    const json = await res.json()
    return json.data as ApiNode | undefined
  } catch {
    return undefined
  }
}

async function reconcileNodeScopes(
  spaceId: string,
  parentIds: Array<string | null>,
  retryOnConcurrentEvent = true,
) {
  const uniqueScopes = [...new Map(
    parentIds.map(parentId => [parentId ?? '__root__', parentId]),
  ).values()]
  const eventVersionAtStart = workspaceTreeEventVersion
  const snapshot = new Map(nodesMap.value)
  const results = await Promise.all(
    uniqueScopes.map(async parentId => ({
      parentId,
      nodes: await fetchNodeScope(spaceId, parentId),
    })),
  )

  if (spaceId !== currentSpaceId.value) return
  if (
    retryOnConcurrentEvent
    && eventVersionAtStart !== workspaceTreeEventVersion
  ) {
    await reconcileNodeScopes(spaceId, parentIds, false)
    return
  }

  const missingIds = new Set<string>()
  for (const { parentId, nodes } of results) {
    const freshIds = new Set(nodes.map(node => node.id))
    for (const current of snapshot.values()) {
      if (current.parent_id === parentId && !freshIds.has(current.id)) {
        missingIds.add(current.id)
      }
    }
  }

  const missingStates = new Map<string, ApiNode | null | undefined>()
  await Promise.all([...missingIds].map(async (nodeId) => {
    missingStates.set(nodeId, await fetchNodeIfPresent(spaceId, nodeId))
  }))
  if (spaceId !== currentSpaceId.value) return

  const newMap = new Map(nodesMap.value)
  const removedIds = new Set<string>()
  for (const [nodeId, serverNode] of missingStates) {
    if (serverNode === null) {
      collectSubtreeIds(nodeId, newMap).forEach(id => removedIds.add(id))
      continue
    }
    if (serverNode) mergeNodeIntoMap(newMap, serverNode)
  }
  removedIds.forEach(id => newMap.delete(id))

  for (const { nodes } of results) {
    nodes.forEach(node => mergeNodeIntoMap(newMap, node))
  }

  nodesMap.value = newMap
  removeNodeIdsFromUi(removedIds)
  syncOpenTabsFromNodes()
}

function disconnectWorkspaceEvents() {
  workspaceEventsGeneration += 1
  const connection = workspaceEventsConnection
  workspaceEventsConnection = null
  if (connection) {
    void connection.disconnect().catch((error) => {
      console.warn('Workspace event disconnect failed:', error)
    })
  }
}

function connectWorkspaceEventsForSpace(spaceId: string) {
  disconnectWorkspaceEvents()
  seenWorkspaceEventIds.clear()
  if (!workspaceEventsEnabled()) return

  const generation = workspaceEventsGeneration
  workspaceEventsConnection = connectWorkspaceEvents({
    spaceId,
    getToken,
    onEvent: (event) => {
      if (
        generation !== workspaceEventsGeneration
        || currentSpaceId.value !== spaceId
      ) {
        return
      }
      applyWorkspaceNodeEvent(event)
    },
    onConnected: () => {
      if (
        generation !== workspaceEventsGeneration
        || currentSpaceId.value !== spaceId
      ) {
        return
      }
      const scopes: Array<string | null> = [
        null,
        ...[...expanded.value].filter(id => nodesMap.value.has(id)),
      ]
      void reconcileNodeScopes(spaceId, scopes).catch((error) => {
        console.error('Workspace tree resync failed:', error)
      })
    },
    onError: (error) => {
      if (generation === workspaceEventsGeneration) {
        console.error('Workspace event socket error:', error)
      }
    },
  })
}

async function createNode(placeholderName: string, parentIdOverride?: string) {
  // 父节点：右键指定 > 选中文件夹 > 根(顶部按钮显式传 null 时强制根)
  let parentId: string | null = parentIdOverride ?? null
  if (parentIdOverride === undefined) {
    const sel = selected.value ? nodesMap.value.get(selected.value) : undefined
    if (sel && sel.type === 'collection') {
      parentId = sel.id
    }
  }

  // 用占位文件名后缀推断 type
  const inferred = inferNodeTypeFromName(placeholderName)

  // 放在同级末尾
  const siblings = [...nodesMap.value.values()]
    .filter(n => n.parent_id === parentId)
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
  const sortOrder = siblings.length === 0
    ? 1.0
    : (siblings[siblings.length - 1].sort_order ?? 0) + 1.0

  try {
    const res = await fetch(`/api/nodes?spaceId=${currentSpaceId.value}`, {
      method: 'POST',
      headers: await authHeaders(),
      body: JSON.stringify({
        parentId,
        type: inferred.type,
        title: placeholderName,
        content: '{}',
        properties: JSON.stringify({ kind: inferred.kind }),
        sortOrder,
      }),
    })
    if (!res.ok) return
    const json = await res.json()
    const node: ApiNode | undefined = json.data
    if (!node) return

    // 用返回的新节点更新本地状态（与 nodeService.move 同样的 new Map 模式）
    const newMap = new Map(nodesMap.value)
    newMap.set(node.id, node)
    if (parentId) {
      const parent = newMap.get(parentId)
      if (parent) newMap.set(parentId, { ...parent, has_children: true })
      expanded.value.add(parentId) // 自动展开父文件夹
    }
    nodesMap.value = newMap
    selected.value = node.id
    // 创建后自动进入 rename 态,沿用 inline input 改名
    renamingId.value = node.id
  } catch (e) {
    console.error('create node failed:', e)
  }
}

const nodeService = {
  async move(spaceId: string, nodeId: string, newParentId: string | null, sortOrder: number) {
    const res = await fetch(`/api/nodes/${nodeId}/move?spaceId=${spaceId}`, {
      method: 'PATCH',
      headers: await authHeaders(),
      body: JSON.stringify({ newParentId, sortOrder })
    })
    if (!res.ok) return
    const json = await res.json()
    // 响应结构: data = { movedNode, oldParent, newParent } —— 后端保证 has_children 准确
    const payload = json.data ?? {}
    const newMap = new Map(nodesMap.value)
    if (payload.movedNode) mergeNodeIntoMap(newMap, payload.movedNode)
    if (payload.oldParent) mergeNodeIntoMap(newMap, payload.oldParent)
    if (payload.newParent) mergeNodeIntoMap(newMap, payload.newParent)
    nodesMap.value = newMap
    syncOpenTabsFromNodes()
  }
}

watch(selected, (nodeId: string | null) => {
  loadingContent.value = true   // 保护:下面给 editContent 赋值不会触发 watch 误标 dirty
  if (nodeId) {
    const node = nodesMap.value.get(nodeId)
    try {
      const obj = JSON.parse(node?.content || '{}')
      editContent.value = obj.text || ''
    } catch {
      editContent.value = node?.content || ''
    }
  } else {
    editContent.value = ''
  }
  nextTick(() => { loadingContent.value = false })
})

async function loadChildren(parentId: string) {
  const spaceId = currentSpaceId.value
  if (!spaceId) return
  loading.value = true
  try {
    await reconcileNodeScopes(spaceId, [parentId])
  } finally {
    loading.value = false
  }
}

async function loadRootNodes() {
  const spaceId = currentSpaceId.value
  if (!spaceId) return
  loading.value = true
  try {
    const data = await fetchNodeScope(spaceId, null)
    if (spaceId !== currentSpaceId.value) return
    nodesMap.value = new Map(data.map(node => [node.id, node]))
    expanded.value.clear()
    selected.value = null
  } finally {
    loading.value = false
  }
}

async function handleToggle(nodeId: string) {
  if (expanded.value.has(nodeId)) {
    expanded.value.delete(nodeId)
  } else {
    const node = nodesMap.value.get(nodeId)
    if (node?.has_children) {
      await loadChildren(nodeId)
    }
    expanded.value.add(nodeId)
  }
}

function handleSelect(nodeId: string) {
  const node = nodesMap.value.get(nodeId)
  if (!node) return

  // 文件夹只 toggle，不打开标签
  if (node.type === 'collection') {
    return
  }

  // 切换前自动保存旧节点的 dirty 内容
  if (selected.value && selected.value !== nodeId) {
    const latestContent = syncActiveEditorContent()
    const old = nodesMap.value.get(selected.value)
    if (old?.dirty) {
      void saveNodeContent(selected.value, latestContent, false)
    }
  }

  openTab(nodeId)
}

async function saveActiveEditor(content: string) {
  if (!selected.value) return
  if (content !== editContent.value) {
    editContent.value = content
  }
  await saveNodeContent(selected.value, content, true)
}

/**
 * 保存节点 content(纯前端 → 后端 PUT)。
 * showFeedback: true 时清 dirty + 显示反馈;false 时静默(切换节点用)。
 */
async function saveNodeContent(nodeId: string, content: string, showFeedback = true) {
  const orig = nodesMap.value.get(nodeId)
  if (!orig) return
  savingNow.value = true
  try {
    const res = await fetch(`/api/nodes/${nodeId}?spaceId=${currentSpaceId.value}`, {
      method: 'PUT',
      headers: await authHeaders(),
      body: JSON.stringify({
        title: orig.title,
        content: JSON.stringify({ text: content }),
        properties: orig.properties,
        caption: orig.caption,
      }),
    })
    if (!res.ok) {
      console.error('save failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    const fresh = json.data as ApiNode | undefined
    if (fresh) {
      const newMap = new Map(nodesMap.value)
      newMap.set(nodeId, fresh)
      nodesMap.value = newMap
    }
    if (showFeedback) {
      const newMap2 = new Map(nodesMap.value)
      const cur = newMap2.get(nodeId)
      if (cur) newMap2.set(nodeId, { ...cur, dirty: false })
      nodesMap.value = newMap2
    }
  } catch (err) {
    console.error('save error:', err)
  } finally {
    savingNow.value = false
  }
}

function getRootNodes(): ApiNode[] {
  return [...nodesMap.value.values()]
    .filter(n => n.parent_id === null)
    .sort((a, b) => {
      const aIsFolder = a.type === 'collection' ? 0 : 1
      const bIsFolder = b.type === 'collection' ? 0 : 1
      if (aIsFolder !== bIsFolder) return aIsFolder - bIsFolder
      return a.title.localeCompare(b.title)
    })
}

function getNodeSrc(nodeId: string): string {
  const props = nodesMap.value.get(nodeId)?.properties
  if (!props) return ''
  try {
    return JSON.parse(props).src || ''
  } catch {
    return ''
  }
}

function getNodePath(nodeId: string | null): string {
  if (!nodeId) return '/workspace'
  const parts: string[] = []
  let current = nodesMap.value.get(nodeId)
  while (current) {
    parts.unshift(current.title)
    current = current.parent_id ? nodesMap.value.get(current.parent_id) : undefined
  }
  return '/workspace/' + parts.join('/')
}

// ── Spaces CRUD ──
async function loadSpaces() {
  spacesLoading.value = true
  try {
    const res = await fetch('/api/spaces', { headers: await authHeaders() })
    const json = await res.json()
    spaces.value = (json.data ?? []) as Space[]
  } catch (err) {
    console.error('loadSpaces error:', err)
  } finally {
    spacesLoading.value = false
  }
}

async function createSpace() {
  const name = newSpaceName.value.trim()
  if (!name) return
  creatingSpace.value = true
  try {
    const res = await fetch('/api/spaces', {
      method: 'POST',
      headers: await authHeaders(),
      body: JSON.stringify({ name }),
    })
    const json = await res.json()
    const space = json.data as Space | undefined
    if (space) {
      spaces.value.push(space)
      newSpaceName.value = ''
      enterSpace(space.id)
    }
  } catch (err) {
    console.error('createSpace error:', err)
  } finally {
    creatingSpace.value = false
  }
}

async function renameSpace(id: string) {
  const name = renameSpaceInput.value.trim()
  if (!name) { renamingSpaceId.value = null; return }
  try {
    const res = await fetch(`/api/spaces/${id}`, {
      method: 'PUT',
      headers: await authHeaders(),
      body: JSON.stringify({ name }),
    })
    const json = await res.json()
    const updated = json.data as Space | undefined
    if (updated) {
      const idx = spaces.value.findIndex(s => s.id === id)
      if (idx !== -1) spaces.value[idx] = updated
    }
  } catch (err) {
    console.error('renameSpace error:', err)
  } finally {
    renamingSpaceId.value = null
    renameSpaceInput.value = ''
  }
}

async function deleteSpace(id: string) {
  try {
    const res = await fetch(`/api/spaces/${id}`, {
      method: 'DELETE',
      headers: await authHeaders(),
    })
    if (res.ok) {
      spaces.value = spaces.value.filter(s => s.id !== id)
    }
  } catch (err) {
    console.error('deleteSpace error:', err)
  }
}

function enterSpace(id: string) {
  disconnectWorkspaceEvents()
  currentSpaceId.value = id
  nodesMap.value.clear()
  expanded.value.clear()
  selected.value = null
  editContent.value = ''
  openTabs.value = []
  currentView.value = 'workspace'
  nextTick(() => {
    void loadRootNodes()
    connectWorkspaceEventsForSpace(id)
  })
}

async function backToSpaces() {
  // 离开前保存当前节点的 dirty 内容
  if (selected.value) {
    const latestContent = syncActiveEditorContent()
    const node = nodesMap.value.get(selected.value)
    if (node?.dirty) {
      await saveNodeContent(selected.value, latestContent, false)
    }
  }
  disconnectWorkspaceEvents()
  currentView.value = 'spaces'
  currentSpaceId.value = null
  nodesMap.value.clear()
  selected.value = null
  editContent.value = ''
}

// init
loadSpaces()
onBeforeUnmount(disconnectWorkspaceEvents)

type ContextMenu = {
  visible: boolean
  x: number
  y: number
  type: 'collection' | 'doc' | 'image' | 'video' | 'audio' | null
  name: string
  targetId: string | null   // 右键点中的节点 ID(action 里要用)
}

const contextMenu = ref<ContextMenu>({
  visible: false,
  x: 0,
  y: 0,
  type: null,
  name: '',
  targetId: null,
})

function showContextMenu(event: MouseEvent, nodeId: string, type: 'collection' | 'doc' | 'image' | 'video' | 'audio', name: string) {
  event.preventDefault()
  contextMenu.value = { visible: true, x: event.clientX, y: event.clientY, type, name, targetId: nodeId }
}

function hideContextMenu() {
  contextMenu.value.visible = false
}

// 内嵌 SVG 图标(尺寸 14×14,跟 toolbar-btn 风格一致)
// 跟 src/components/FileTreeItem.vue 里的图标同款(简化版)
const ICONS = {
  doc: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>',
  folder: '<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>',
  edit: '<path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>',
  copy: '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>',
  trash: '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>',
  open: '<path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/><polyline points="9 14 12 11 15 14"/><line x1="12" y1="11" x2="12" y2="17"/>',
}

function svg(name: keyof typeof ICONS): string {
  return `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[name]}</svg>`
}

const menuItems = computed(() => {
  if (!contextMenu.value.type) return []
  const targetId = contextMenu.value.targetId
  if (contextMenu.value.type === 'collection') {
    return [
      { label: 'New Doc', svg: svg('doc'), action: () => { if (targetId) createNode('temp.md', targetId) } },
      { label: 'New Collection', svg: svg('folder'), action: () => { if (targetId) createNode('untitled', targetId) } },
      { label: 'Upload', svg: svg('upload'), action: () => { if (targetId) triggerUploadToFolder(targetId) } },
      { label: 'Rename', svg: svg('edit'), action: () => { if (targetId) startRename(targetId) } },
      { label: 'Delete', svg: svg('trash'), action: () => { if (targetId) deleteNode(targetId) } },
    ]
  }
  return [
    { label: 'Upload', svg: svg('upload'), action: () => { if (targetId) triggerUploadToFolder(targetId) } },
    { label: 'Duplicate', svg: svg('copy'), action: () => { if (targetId) duplicateNode(targetId) } },
    { label: 'Rename', svg: svg('edit'), action: () => { if (targetId) startRename(targetId) } },
    { label: 'Delete', svg: svg('trash'), action: () => { if (targetId) deleteNode(targetId) } },
  ]
})

/**
 * 把节点切到重命名态(FileTreeItem 看到 renameTargetId === node.id 就显示 input 全选)
 */
function startRename(targetId: string) {
  renamingId.value = targetId
}

/**
 * FileTreeItem 回车时调:PUT 后端,成功后退出编辑态
 */
async function commitRename(targetId: string, newTitle: string) {
  renamingId.value = null
  const orig = nodesMap.value.get(targetId)
  if (!orig) return
  if (!newTitle || newTitle === orig.title) return

  // 按新名字后缀推断 type + kind(后缀变了 → type 跟变)
  const inferred = inferNodeTypeFromName(newTitle)
  // properties 保留 orig,只覆盖 kind 字段(让 src 之类不动)
  const origProps = orig.properties ? JSON.parse(orig.properties) : {}
  const newProps = { ...origProps, kind: inferred.kind }

  try {
    const res = await fetch(`/api/nodes/${targetId}?spaceId=${currentSpaceId.value}`, {
      method: 'PUT',
      headers: await authHeaders(),
      body: JSON.stringify({
        title: newTitle,
        type: inferred.type,
        content: orig.content,
        properties: JSON.stringify(newProps),
        caption: orig.caption,
      }),
    })
    if (!res.ok) {
      console.error('rename failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    const fresh = json.data as ApiNode | undefined
    if (!fresh) return
    const newMap = new Map(nodesMap.value)
    mergeNodeIntoMap(newMap, fresh)
    nodesMap.value = newMap
    syncOpenTabsFromNodes()
  } catch (err) {
    console.error('rename error:', err)
  }
}

/**
 * FileTreeItem Esc 或 blur 时调:只退出编辑态,不改
 */
function cancelRename() {
  renamingId.value = null
}

/**
 * 删除节点(后端走软删)。
 * 弹窗确认;成功后更新本地 Map + 父节点的 has_children。
 */
async function deleteNode(targetId: string) {
  const orig = nodesMap.value.get(targetId)
  if (!orig) return
  try {
    const res = await fetch(`/api/nodes/${targetId}?spaceId=${currentSpaceId.value}`, {
      method: 'DELETE',
      headers: await authHeaders(),
    })
    if (!res.ok) {
      console.error('delete failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    const payload = json.data ?? {}
    removeNodeSubtree(targetId)
    // 更新父节点的 has_children(后端响应里带)
    if (payload.oldParent) {
      const newMap = new Map(nodesMap.value)
      mergeNodeIntoMap(newMap, payload.oldParent)
      nodesMap.value = newMap
    }
    syncOpenTabsFromNodes()
  } catch (err) {
    console.error('delete error:', err)
  }
}

/**
 * 复制节点(不复制子节点)。
 * title 加 "_copy" 后缀,放同父节点末尾。
 */
async function duplicateNode(targetId: string) {
  const orig = nodesMap.value.get(targetId)
  if (!orig) return
  // 算 sortOrder:同父兄弟最大 sort_order + 1
  const siblings = [...nodesMap.value.values()]
    .filter(n => n.parent_id === orig.parent_id)
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
  const sortOrder = siblings.length === 0
    ? 1.0
    : (siblings[siblings.length - 1].sort_order ?? 0) + 1.0

  try {
    const res = await fetch(`/api/nodes?spaceId=${currentSpaceId.value}`, {
      method: 'POST',
      headers: await authHeaders(),
      body: JSON.stringify({
        parentId: orig.parent_id,
        type: orig.type,
        title: `${orig.title}_copy`,
        content: orig.content ?? '{}',
        properties: orig.properties ?? '{}',
        caption: orig.caption ?? null,
        sortOrder,
      }),
    })
    if (!res.ok) {
      console.error('duplicate failed:', res.status, await res.text())
      return
    }
    const json = await res.json()
    const node = json.data as ApiNode | undefined
    if (!node) return
    // 乐观更新
    const newMap = new Map(nodesMap.value)
    newMap.set(node.id, node)
    if (orig.parent_id) {
      const parent = newMap.get(orig.parent_id)
      if (parent) newMap.set(orig.parent_id, { ...parent, has_children: true })
    }
    nodesMap.value = newMap
    selected.value = node.id
  } catch (err) {
    console.error('duplicate error:', err)
  }
}
</script>

<template>
  <!-- ═══ Spaces 列表页 ═══ -->
  <main v-if="currentView === 'spaces'" class="spaces-view">
    <div class="spaces-container">
      <header class="spaces-header">
        <h1>My Workspaces</h1>
        <p v-if="spaces.length" class="spaces-subtitle">Select a workspace or create a new one</p>
      </header>

      <p v-if="spacesLoading" class="spaces-loading">Loading...</p>

      <div v-else class="spaces-grid">
        <div
          v-for="space in spaces"
          :key="space.id"
          class="space-card"
          @click="enterSpace(space.id)"
        >
          <div class="space-card-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <input
            v-if="renamingSpaceId === space.id"
            v-model="renameSpaceInput"
            class="space-rename-input"
            @keydown.enter="renameSpace(space.id)"
            @keydown.esc="renamingSpaceId = null"
            @blur="renameSpace(space.id)"
            @click.stop
            autofocus
          />
          <div v-else class="space-card-name">{{ space.name }}</div>
          <div class="space-card-date">{{ new Date(space.created_at).toLocaleDateString() }}</div>
          <div class="space-card-actions" @click.stop>
            <button
              class="space-action-btn"
              title="Rename"
              @click="renamingSpaceId = space.id; renameSpaceInput = space.name"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            <button
              class="space-action-btn space-action-btn--danger"
              title="Delete"
              @click="deleteSpace(space.id)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
            </button>
          </div>
        </div>

        <!-- Create card -->
        <div v-if="!creatingSpace" class="space-card create-space-card" @click="creatingSpace = true">
          <span class="create-space-plus">+</span>
          <span>Create Workspace</span>
        </div>
        <div v-else class="space-card create-space-form" @click.stop>
          <input
            v-model="newSpaceName"
            class="space-create-input"
            placeholder="Workspace name..."
            @keydown.enter="createSpace()"
            @keydown.esc="creatingSpace = false; newSpaceName = ''"
            autofocus
          />
          <div class="create-space-actions">
            <button class="space-action-btn space-action-btn--primary" :disabled="!newSpaceName.trim()" @click="createSpace()">Create</button>
            <button class="space-action-btn" @click="creatingSpace = false; newSpaceName = ''">Cancel</button>
          </div>
        </div>
      </div>
    </div>
  </main>

  <!-- ═══ Workspace 视图(现有) ═══ -->
  <main v-else-if="currentView === 'workspace'" class="workspace">
    <!-- ═══ 标签栏(全宽) ═══ -->
    <div v-if="openTabs.length" class="tabs-bar">
      <div
        v-for="tab in openTabs"
        :key="tab.id"
        class="tab-item"
        :class="{ active: selected === tab.id }"
        role="button"
        tabindex="0"
        @click="handleSelect(tab.id)"
        @keydown.enter="handleSelect(tab.id)"
        @keydown.space.prevent="handleSelect(tab.id)"
      >
        <span class="tab-icon">
          <svg v-if="tab.type === 'image'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
          <svg v-else-if="tab.type === 'video'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
          <svg v-else-if="tab.type === 'audio'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
          <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        </span>
        <span class="tab-title">{{ tab.title }}</span>
        <span v-if="nodesMap.get(tab.id)?.dirty" class="tab-dirty" title="未保存"></span>
        <button type="button" class="tab-close" title="关闭" @click.stop="closeTab(tab.id)">×</button>
      </div>
    </div>
    <aside class="sidebar panel">
      <div class="panel-head">
        <div>
          <button type="button" class="back-btn" title="Back to Spaces" @click="backToSpaces">←</button>
          <span class="eyebrow">Workspace</span>
          <h1>{{ spaces.find(s => s.id === currentSpaceId)?.name || 'workspace' }}</h1>
        </div>
        <button type="button" class="icon-button">+</button>
      </div>

      <div class="searchbar">
        <span>⌘K</span>
        <p>Search</p>
      </div>

      <section class="sidebar-block">
        <span class="block-label">File tree</span>
        <div class="tree-toolbar">
          <button type="button" class="toolbar-btn" title="New File" @click="createNode('temp.md')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
          </button>
          <button type="button" class="toolbar-btn" title="New Folder" @click="createNode('untitled')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          </button>
          <button type="button" class="toolbar-btn" title="Upload" @click="triggerUpload">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          </button>
          <input ref="fileInputRef" type="file" multiple style="display:none" @change="onFileSelected">
        </div>
        <p v-if="loading" class="tree-loading">Loading...</p>
        <ul v-else class="tree-root" @dragover.prevent="onDragOverRoot" @drop="onDropRoot">
          <FileTreeItem
            v-for="node in getRootNodes()"
            :key="node.id"
            :node="node"
            :nodes-map="nodesMap"
            :expanded="expanded"
            :selected="selected"
            :depth="0"
            :drop-target-id="dropTargetId"
            :rename-target-id="renamingId"
            :dirty="node.dirty"
            @toggle="handleToggle"
            @select="handleSelect"
            @contextmenu="showContextMenu"
            @dragstart="onDragStart"
            @dragover="onDragOver"
            @drop="onDrop"
            @rename-commit="(id: string, newTitle: string) => commitRename(id, newTitle)"
            @rename-cancel="cancelRename"
          />
        </ul>
      </section>
    </aside>

    <section class="content panel">
      <header class="topbar">
        <span class="eyebrow">{{ getNodePath(selected) }}</span>
      </header>

      <section class="editor-grid">
        <article class="workspace-card editor-pane">
          <div
            v-if="selected && ['image', 'video', 'audio'].includes(nodesMap.get(selected)?.type || '')"
            class="editor-content-only"
          >
            <div v-if="nodesMap.get(selected)?.type === 'image'" class="preview-media image-preview">
              <img :src="getNodeSrc(selected)" alt="preview" />
            </div>
            <div v-else-if="nodesMap.get(selected)?.type === 'video'" class="preview-media video-preview">
              <video :src="getNodeSrc(selected)" controls playsinline />
            </div>
            <div v-else class="preview-media audio-preview">
              <audio :src="getNodeSrc(selected)" controls />
            </div>
          </div>
          <CollaborativeEditor
            v-else-if="selected"
            :key="`${currentSpaceId}:${selected}`"
            ref="editorRef"
            v-model="editContent"
            :space-id="currentSpaceId ?? ''"
            :document-id="selected"
            :dirty="Boolean(nodesMap.get(selected)?.dirty)"
            :saving="savingNow"
            :resolve-media="resolveEditorMedia"
            @save="saveActiveEditor"
            @navigate="onEditorNavigate"
            @external-drop="onEditorDrop"
          />
        </article>
      </section>
    </section>
  </main>

  <!-- Context menu -->
  <Teleport v-if="currentView === 'workspace'" to="body">
    <div v-if="contextMenu.visible" class="context-menu-backdrop" @click="hideContextMenu" @contextmenu.prevent="hideContextMenu">
      <ul class="context-menu" :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }" @click.stop>
        <li v-for="item in menuItems" :key="item.label" class="context-menu-item" @click="item.action(); hideContextMenu()">
          <span class="context-menu-icon" v-html="item.svg"></span>
          <span>{{ item.label }}</span>
        </li>
      </ul>
    </div>
  </Teleport>
</template>
