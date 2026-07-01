<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import FileTreeItem from './components/FileTreeItem.vue'
import { useMarkdown } from './composables/useMarkdown'

const { render } = useMarkdown()

const SPACE_ID = '00000000-0000-0000-0000-000000000001'

type ApiNode = {
  id: string
  title: string
  type: string
  parent_id: string | null
  has_children: boolean
  content?: string
  properties?: string
  sort_order?: number
}

const nodesMap = ref<Map<string, ApiNode>>(new Map())
const expanded = ref(new Set<string>())
const selected = ref<string | null>(null)
const loading = ref(false)
const isPreview = ref(false)
const editContent = ref('')
const draggingId = ref<string | null>(null)
const dropTargetId = ref<string | null>(null)

function onDragStart(nodeId: string, e: DragEvent) {
  draggingId.value = nodeId
  e.dataTransfer?.setData('text/plain', nodeId)
}

function onDragOver(nodeId: string, e: DragEvent) {
  e.preventDefault()
  dropTargetId.value = nodeId
}

function onDrop(targetId: string, e: DragEvent) {
  e.preventDefault()
  const draggedId = e.dataTransfer?.getData('text/plain') || draggingId.value
  if (!draggedId || draggedId === targetId) {
    draggingId.value = null
    dropTargetId.value = null
    return
  }
  // Drop as first child of target
  const children = [...nodesMap.value.values()].filter(n => n.parent_id === targetId)
  const sortOrder = children.length === 0 ? 1.0 : (children[0].sort_order ?? 1) / 2
  nodeService.move(SPACE_ID, draggedId, targetId, sortOrder)
  draggingId.value = null
  dropTargetId.value = null
}

const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFileSelected(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files?.length) return
  const file = files[0]
  const token = await getToken()
  const form = new FormData()
  form.append('file', file)
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
    const url = json.data?.url as string | undefined
    if (url) {
      console.log('uploaded:', url)
      // TODO: 后面把 url 存到某个 image/video 节点的 properties.src,
      //       当前只 log 看效果
    }
  } catch (err) {
    console.error('upload error:', err)
  } finally {
    // 清 input value,允许重复选同一文件
    (e.target as HTMLInputElement).value = ''
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

async function createNode(type: string) {
  // 父节点：选中文件夹则建在其下，否则根目录
  let parentId: string | null = null
  const sel = selected.value ? nodesMap.value.get(selected.value) : undefined
  if (sel && sel.type === 'collection') {
    parentId = sel.id
  }

  // 弹窗命名，取消则不创建
  const defaultName = type === 'collection' ? '未命名文件夹' : '未命名文档'
  const title = window.prompt('请输入名称', defaultName)
  if (!title) return

  // 放在同级末尾
  const siblings = [...nodesMap.value.values()]
    .filter(n => n.parent_id === parentId)
    .sort((a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0))
  const sortOrder = siblings.length === 0
    ? 1.0
    : (siblings[siblings.length - 1].sort_order ?? 0) + 1.0

  try {
    const res = await fetch('/api/nodes', {
      method: 'POST',
      headers: await authHeaders(),
      body: JSON.stringify({
        spaceId: SPACE_ID,
        parentId,
        type,
        title,
        content: '{}',
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
  } catch (e) {
    console.error('create node failed:', e)
  }
}

const nodeService = {
  async move(spaceId: string, nodeId: string, newParentId: string, sortOrder: number) {
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
    if (payload.movedNode) newMap.set(payload.movedNode.id, payload.movedNode)
    if (payload.oldParent) newMap.set(payload.oldParent.id, payload.oldParent)
    if (payload.newParent) newMap.set(payload.newParent.id, payload.newParent)
    nodesMap.value = newMap
  }
}

watch(selected, (nodeId: string | null) => {
  isPreview.value = false
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
})

async function loadChildren(parentId: string) {
  if ([...nodesMap.value.values()].some(n => n.parent_id === parentId)) return
  loading.value = true
  try {
    const res = await fetch(`/api/nodes?spaceId=${SPACE_ID}&parentId=${parentId}`, {
      headers: await authHeaders(),
    })
    const json = await res.json()
    const data: ApiNode[] = json.data ?? []
    data.forEach(node => nodesMap.value.set(node.id, node))
  } finally {
    loading.value = false
  }
}

async function loadRootNodes() {
  loading.value = true
  try {
    const res = await fetch(`/api/nodes?spaceId=${SPACE_ID}`, {
      headers: await authHeaders(),
    })
    const json = await res.json()
    const data: ApiNode[] = json.data ?? []
    nodesMap.value.clear()
    expanded.value.clear()
    selected.value = null
    data.forEach(node => nodesMap.value.set(node.id, node))
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
  selected.value = nodeId
}

function getRootNodes(): ApiNode[] {
  return [...nodesMap.value.values()].filter(n => n.parent_id === null)
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

// init
loadRootNodes()

type ContextMenu = {
  visible: boolean
  x: number
  y: number
  type: 'collection' | 'doc' | 'image' | 'video' | 'audio' | null
  name: string
}

const contextMenu = ref<ContextMenu>({
  visible: false,
  x: 0,
  y: 0,
  type: null,
  name: '',
})

function showContextMenu(event: MouseEvent, type: 'collection' | 'doc' | 'image' | 'video' | 'audio', name: string) {
  event.preventDefault()
  contextMenu.value = { visible: true, x: event.clientX, y: event.clientY, type, name }
}

function hideContextMenu() {
  contextMenu.value.visible = false
}

const menuItems = computed(() => {
  if (!contextMenu.value.type) return []
  if (contextMenu.value.type === 'collection') {
    return [
      { label: 'New Doc', icon: '📄', action: () => {} },
      { label: 'New Collection', icon: '📁', action: () => {} },
      { label: 'Upload', icon: '⬆️', action: () => {} },
      { label: 'Rename', icon: '✏️', action: () => {} },
      { label: 'Delete', icon: '🗑️', action: () => {} },
    ]
  }
  return [
    { label: 'Open', icon: '📂', action: () => {} },
    { label: 'Upload', icon: '⬆️', action: () => {} },
    { label: 'Duplicate', icon: '📋', action: () => {} },
    { label: 'Rename', icon: '✏️', action: () => {} },
    { label: 'Delete', icon: '🗑️', action: () => {} },
  ]
})
</script>

<template>
  <main class="workspace">
    <aside class="sidebar panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Workspace</span>
          <h1>workspace</h1>
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
          <button type="button" class="toolbar-btn" title="New File" @click="createNode('doc')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="15" y2="15"/></svg>
          </button>
          <button type="button" class="toolbar-btn" title="New Folder" @click="createNode('collection')">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
          </button>
          <button type="button" class="toolbar-btn" title="Upload" @click="triggerUpload">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
          </button>
          <input ref="fileInputRef" type="file" multiple style="display:none" @change="onFileSelected">
        </div>
        <p v-if="loading" class="tree-loading">Loading...</p>
        <ul v-else class="tree-root">
          <FileTreeItem
            v-for="node in getRootNodes()"
            :key="node.id"
            :node="node"
            :nodes-map="nodesMap"
            :expanded="expanded"
            :selected="selected"
            :depth="0"
            :drop-target-id="dropTargetId"
            @toggle="handleToggle"
            @select="handleSelect"
            @contextmenu="showContextMenu"
            @dragstart="onDragStart"
            @dragover="onDragOver"
            @drop="onDrop"
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
          <div v-if="selected && (nodesMap.get(selected)?.type === 'image' || nodesMap.get(selected)?.type === 'video')" class="editor-content-only">
            <div v-if="nodesMap.get(selected)?.type === 'image'" class="preview-media image-preview">
              <img :src="getNodeSrc(selected)" alt="preview" />
            </div>
            <div v-else class="preview-media video-preview">
              <video :src="getNodeSrc(selected)" controls playsinline />
            </div>
          </div>
          <template v-else-if="selected">
            <div class="editor-toolbar">
              <button type="button" class="mode-btn" :class="{ active: !isPreview }" @click="isPreview = false">Edit</button>
              <button type="button" class="mode-btn" :class="{ active: isPreview }" @click="isPreview = true">Preview</button>
            </div>
            <textarea
              v-if="!isPreview"
              v-model="editContent"
              class="editor-textarea"
              placeholder="Write markdown here..."
            ></textarea>
            <div v-else class="text-editor" v-html="render(editContent)"></div>
          </template>
        </article>
      </section>
    </section>
  </main>

  <!-- Context menu -->
  <Teleport to="body">
    <div v-if="contextMenu.visible" class="context-menu-backdrop" @click="hideContextMenu" @contextmenu.prevent="hideContextMenu">
      <ul class="context-menu" :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }" @click.stop>
        <li v-for="item in menuItems" :key="item.label" class="context-menu-item" @click="item.action(); hideContextMenu()">
          <span class="context-menu-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </li>
      </ul>
    </div>
  </Teleport>
</template>
