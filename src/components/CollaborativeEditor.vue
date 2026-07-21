<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { HocuspocusProvider, WebSocketStatus } from '@hocuspocus/provider'
import {
  defaultValueCtx,
  Editor,
  editorViewCtx,
  editorViewOptionsCtx,
  rootCtx,
  type CmdKey,
} from '@milkdown/kit/core'
import {
  commonmark,
  createCodeBlockCommand,
  htmlSchema,
  insertHrCommand,
  insertImageCommand,
  toggleEmphasisCommand,
  toggleInlineCodeCommand,
  toggleLinkCommand,
  toggleStrongCommand,
  turnIntoTextCommand,
  wrapInBlockquoteCommand,
  wrapInBulletListCommand,
  wrapInHeadingCommand,
  wrapInOrderedListCommand,
} from '@milkdown/kit/preset/commonmark'
import {
  gfm,
  insertTableCommand,
  toggleStrikethroughCommand,
} from '@milkdown/kit/preset/gfm'
import { clipboard } from '@milkdown/kit/plugin/clipboard'
import { cursor } from '@milkdown/kit/plugin/cursor'
import { indent } from '@milkdown/kit/plugin/indent'
import { listener, listenerCtx } from '@milkdown/kit/plugin/listener'
import { trailing } from '@milkdown/kit/plugin/trailing'
import { collab, collabServiceCtx } from '@milkdown/plugin-collab'
import {
  imageInlineComponent,
  inlineImageConfig,
} from '@milkdown/kit/component/image-inline'
import {
  $view,
  $prose,
  callCommand,
  getMarkdown as readMarkdown,
  insert as insertMarkdownContent,
} from '@milkdown/kit/utils'
import type { Node as ProseNode } from '@milkdown/kit/prose/model'
import { Plugin, type EditorState } from '@milkdown/kit/prose/state'
import type { EditorView, NodeViewConstructor } from '@milkdown/kit/prose/view'
import {
  redoCommand as collaborationRedoCommand,
  undoCommand as collaborationUndoCommand,
} from 'y-prosemirror'
import { Doc } from 'yjs'

import { createMdxPlugins } from '../editor/mdx'

import '@milkdown/kit/prose/view/style/prosemirror.css'
import '@milkdown/kit/prose/gapcursor/style/gapcursor.css'
import '@milkdown/kit/prose/tables/style/tables.css'

type ResolveMedia = (path: string) => string | Promise<string>
type SlashCommandId =
  | 'paragraph'
  | 'heading-1'
  | 'heading-2'
  | 'heading-3'
  | 'bullet-list'
  | 'ordered-list'
  | 'blockquote'
  | 'code-block'
  | 'image'
  | 'video'
  | 'audio'
  | 'table'
  | 'divider'
  | 'callout'
  | 'mdx-component'

type SlashCommandOption = {
  id: SlashCommandId
  label: string
  description: string
  icon: string
}
type CollaborationStatus =
  | 'connecting'
  | 'syncing'
  | 'synced'
  | 'disconnected'
  | 'error'
type CollaborationUser = {
  name: string
  color: string
}

const slashCommands: SlashCommandOption[] = [
  { id: 'paragraph', label: '正文', description: '普通文本段落', icon: 'P' },
  { id: 'heading-1', label: '一级标题', description: '页面主标题', icon: 'H1' },
  { id: 'heading-2', label: '二级标题', description: '章节标题', icon: 'H2' },
  { id: 'heading-3', label: '三级标题', description: '小节标题', icon: 'H3' },
  { id: 'bullet-list', label: '无序列表', description: '创建项目符号列表', icon: '•' },
  { id: 'ordered-list', label: '有序列表', description: '创建编号列表', icon: '1.' },
  { id: 'blockquote', label: '引用', description: '突出显示引用内容', icon: '❯' },
  { id: 'code-block', label: '代码块', description: '插入多行代码', icon: '{ }' },
  { id: 'image', label: '图片', description: '通过地址插入图片', icon: '▧' },
  { id: 'video', label: '视频', description: '通过地址插入视频', icon: '▶' },
  { id: 'audio', label: '音频', description: '通过地址插入音频', icon: '♪' },
  { id: 'table', label: '表格', description: '插入 3 × 3 表格', icon: '▦' },
  { id: 'divider', label: '分隔线', description: '插入水平分隔线', icon: '—' },
  { id: 'callout', label: 'MDX 提示块', description: '插入可编辑的 Callout 组件', icon: 'ⓘ' },
  { id: 'mdx-component', label: 'MDX 组件', description: '插入自定义 JSX 组件', icon: '<>' },
]

const collaborationColors = [
  '#38bdf8',
  '#a78bfa',
  '#34d399',
  '#fb7185',
  '#fbbf24',
  '#22d3ee',
]
const collaborationProfileStorageKey = 'workspace-collaboration-profile'

function createCollaborationUser(): CollaborationUser {
  const id = globalThis.crypto?.randomUUID?.()
    ?? Math.random().toString(36).slice(2)
  const colorIndex = Array.from(id)
    .reduce((sum, character) => sum + character.charCodeAt(0), 0)
    % collaborationColors.length

  return {
    name: `访客-${id.slice(0, 4).toUpperCase()}`,
    color: collaborationColors[colorIndex],
  }
}

function getCollaborationUser(): CollaborationUser {
  try {
    const stored = sessionStorage.getItem(collaborationProfileStorageKey)
    if (stored) {
      const parsed = JSON.parse(stored) as Partial<CollaborationUser>
      if (
        typeof parsed.name === 'string'
        && /^#[0-9a-f]{6}$/i.test(parsed.color ?? '')
      ) {
        return {
          name: parsed.name,
          color: parsed.color!,
        }
      }
    }

    const profile = createCollaborationUser()
    sessionStorage.setItem(
      collaborationProfileStorageKey,
      JSON.stringify(profile),
    )
    return profile
  } catch {
    return createCollaborationUser()
  }
}

function getCollaborationURL(): string {
  const configuredURL = import.meta.env.VITE_HOCUSPOCUS_URL?.trim()
  if (configuredURL) return configuredURL.replace(/\/+$/, '')

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const hostname = window.location.hostname || '127.0.0.1'
  return `${protocol}//${hostname}:1234`
}

const props = defineProps<{
  modelValue: string
  spaceId: string
  documentId: string
  dirty?: boolean
  saving?: boolean
  resolveMedia?: ResolveMedia
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  save: [value: string]
  navigate: [path: string]
  'external-drop': [event: DragEvent]
}>()

const editorRoot = ref<HTMLDivElement | null>(null)
const slashMenuRef = ref<HTMLDivElement | null>(null)
const ready = ref(false)
const initError = ref('')
const collaborationStatus = ref<CollaborationStatus>('connecting')
const slashMenuOpen = ref(false)
const slashActiveIndex = ref(0)
const slashMenuPosition = ref({ top: 0, left: 0 })
const collaborationStatusLabel = computed(() => ({
  connecting: '连接中',
  syncing: '同步中',
  synced: '已同步',
  disconnected: '连接断开',
  error: '协作异常',
})[collaborationStatus.value])
const collaborationDocumentName = computed(
  () => `space:${props.spaceId}:node:${props.documentId}`,
)
const collaborationUser = getCollaborationUser()

let editor: Editor | null = null
let collaborationDoc: Doc | null = null
let collaborationProvider: HocuspocusProvider | null = null
let disposed = false
let editorCreated = false
let editorEditable = false
let providerSynced = false
let collaborationConnected = false
let providerSocketStatus = WebSocketStatus.Connecting
let slashEditorView: EditorView | null = null
let slashTriggerFrom: number | null = null
let suppressSlashDetection = false

function closeSlashMenu() {
  slashMenuOpen.value = false
  slashActiveIndex.value = 0
  slashTriggerFrom = null
}

function positionSlashMenu(view: EditorView, position: number) {
  const coords = view.coordsAtPos(position)
  const menuWidth = 310
  const menuHeight = 360
  const viewportPadding = 12
  const left = Math.max(
    viewportPadding,
    Math.min(coords.left, window.innerWidth - menuWidth - viewportPadding),
  )
  const spaceBelow = window.innerHeight - coords.bottom
  const top = spaceBelow >= Math.min(menuHeight, 260)
    ? coords.bottom + 8
    : Math.max(viewportPadding, coords.top - menuHeight - 8)

  slashMenuPosition.value = { top, left }
}

function updateSlashMenu(view: EditorView, prevState: EditorState) {
  slashEditorView = view

  if (suppressSlashDetection) {
    closeSlashMenu()
    return
  }

  const { selection } = view.state
  if (
    slashMenuOpen.value
    && (
      !selection.empty
      || slashTriggerFrom === null
      || selection.from !== slashTriggerFrom + 1
    )
  ) {
    closeSlashMenu()
  }

  if (view.state.doc.eq(prevState.doc)) return
  if (!view.hasFocus() || !selection.empty) {
    closeSlashMenu()
    return
  }

  const { $from } = selection
  if (!$from.parent.isTextblock || $from.parentOffset < 1) {
    closeSlashMenu()
    return
  }

  const insertedCharacter = $from.parent.textBetween(
    $from.parentOffset - 1,
    $from.parentOffset,
    undefined,
    '\uFFFC',
  )

  if (insertedCharacter !== '/') {
    closeSlashMenu()
    return
  }

  slashTriggerFrom = selection.from - 1
  slashActiveIndex.value = 0
  positionSlashMenu(view, selection.from)
  slashMenuOpen.value = true
}

const slashMenuPlugin = $prose(() => new Plugin({
  view: (view) => {
    slashEditorView = view
    return {
      update: updateSlashMenu,
      destroy: () => {
        slashEditorView = null
        closeSlashMenu()
      },
    }
  },
}))

function resolveMediaURL(path: string): Promise<string> {
  try {
    return Promise.resolve(props.resolveMedia?.(path) ?? path)
  } catch {
    return Promise.resolve(path)
  }
}

const mediaHtmlView = $view(
  htmlSchema.node,
  (): NodeViewConstructor => (initialNode) => {
    const dom = document.createElement('span')
    const nodeType = initialNode.type
    let renderVersion = 0

    const render = (node: ProseNode) => {
      const value = String(node.attrs.value ?? '')
      const trimmed = value.trim()
      const closingMediaMatch = trimmed.match(/^<\/(video|audio)>\s*$/i)
      const mediaMatch = trimmed.match(/^<(video|audio)\b([^>]*)>(?:\s*<\/\1>)?\s*$/is)
      const srcMatch = mediaMatch?.[2].match(/\bsrc\s*=\s*(["'])(.*?)\1/i)
      const currentVersion = ++renderVersion

      dom.replaceChildren()
      dom.contentEditable = 'false'
      dom.hidden = false

      if (closingMediaMatch) {
        dom.className = 'milkdown-html-node milkdown-html-node--closing-media'
        dom.hidden = true
        return
      }

      if (!mediaMatch || !srcMatch) {
        dom.className = 'milkdown-html-node'
        const code = document.createElement('code')
        code.textContent = value
        dom.append(code)
        return
      }

      const tagName = mediaMatch[1].toLowerCase() as 'video' | 'audio'
      const source = srcMatch[2]
      const media = document.createElement(tagName)

      dom.className = `milkdown-html-node milkdown-html-node--${tagName}`
      media.controls = true
      media.preload = 'metadata'
      if (media instanceof HTMLVideoElement) {
        media.playsInline = true
      }

      void resolveMediaURL(source)
        .then((resolved) => {
          if (currentVersion === renderVersion) media.src = resolved
        })
        .catch(() => {
          if (currentVersion === renderVersion) media.src = source
        })

      dom.append(media)
    }

    render(initialNode)

    return {
      dom,
      update(nextNode) {
        if (nextNode.type !== nodeType) return false
        render(nextNode)
        return true
      },
    }
  },
)

function getMdx(): string {
  return editor?.action(readMarkdown()) ?? props.modelValue
}

function publishMdx(): string {
  const mdx = getMdx()
  if (mdx !== props.modelValue) emit('update:modelValue', mdx)
  return mdx
}

function focus() {
  editor?.action((ctx) => {
    ctx.get(editorViewCtx).focus()
  })
}

function runCommand<T>(key: CmdKey<T>, payload?: T) {
  if (!editor || !ready.value) return
  editor.action(callCommand(key, payload))
  publishMdx()
  focus()
}

function runCollaborationHistory(command: typeof collaborationUndoCommand) {
  if (!editor || !ready.value) return

  editor.action((ctx) => {
    const view = ctx.get(editorViewCtx)
    command(
      view.state,
      (transaction) => view.dispatch(transaction),
      view,
    )
    view.focus()
  })
  publishMdx()
}

function consumeSlashTrigger(): boolean {
  const view = slashEditorView
  const from = slashTriggerFrom
  if (!view || from === null) {
    closeSlashMenu()
    return false
  }

  const trigger = view.state.doc.textBetween(from, from + 1, '', '\uFFFC')
  if (trigger !== '/') {
    closeSlashMenu()
    return false
  }

  closeSlashMenu()
  suppressSlashDetection = true
  view.dispatch(view.state.tr.delete(from, from + 1).scrollIntoView())
  suppressSlashDetection = false
  return true
}

function requestSlashMedia(type: 'image' | 'video' | 'audio') {
  const labels = {
    image: '图片',
    video: '视频',
    audio: '音频',
  }
  const src = window.prompt(`${labels[type]}地址（可使用 /workspace/...）`)
  if (!src) {
    closeSlashMenu()
    focus()
    return
  }

  if (!consumeSlashTrigger()) return

  if (type === 'image') {
    const alt = window.prompt('图片说明') ?? ''
    runCommand(insertImageCommand.key, { src, alt })
    return
  }

  insertMdx(`<${type} src="${src}" controls></${type}>`, true)
}

function executeSlashCommand(command: SlashCommandOption) {
  if (command.id === 'image' || command.id === 'video' || command.id === 'audio') {
    requestSlashMedia(command.id)
    return
  }

  if (!consumeSlashTrigger()) return

  switch (command.id) {
    case 'paragraph':
      runCommand(turnIntoTextCommand.key)
      break
    case 'heading-1':
      runCommand(wrapInHeadingCommand.key, 1)
      break
    case 'heading-2':
      runCommand(wrapInHeadingCommand.key, 2)
      break
    case 'heading-3':
      runCommand(wrapInHeadingCommand.key, 3)
      break
    case 'bullet-list':
      runCommand(wrapInBulletListCommand.key)
      break
    case 'ordered-list':
      runCommand(wrapInOrderedListCommand.key)
      break
    case 'blockquote':
      runCommand(wrapInBlockquoteCommand.key)
      break
    case 'code-block':
      runCommand(createCodeBlockCommand.key)
      break
    case 'table':
      runCommand(insertTableCommand.key, { row: 3, col: 3 })
      break
    case 'divider':
      runCommand(insertHrCommand.key)
      break
    case 'callout':
      insertMdx('<Callout type="info" title="提示">\n\n请输入内容。\n\n</Callout>')
      break
    case 'mdx-component': {
      const name = window.prompt('组件名称（例如 Chart）')?.trim()
      if (!name || !/^[A-Z][\w.:-]*$/.test(name)) {
        focus()
        break
      }
      insertMdx(`<${name} />`)
      break
    }
  }
}

function moveSlashSelection(direction: 1 | -1) {
  const total = slashCommands.length
  slashActiveIndex.value = (slashActiveIndex.value + direction + total) % total
  requestAnimationFrame(() => {
    slashMenuRef.value
      ?.querySelector<HTMLElement>(`[data-slash-index="${slashActiveIndex.value}"]`)
      ?.scrollIntoView({ block: 'nearest' })
  })
}

function selectActiveSlashCommand() {
  const command = slashCommands[slashActiveIndex.value]
  if (command) executeSlashCommand(command)
}

function requestLink() {
  const href = window.prompt('链接地址')
  if (!href) return
  runCommand(toggleLinkCommand.key, { href })
}

function requestImage() {
  const src = window.prompt('图片地址（可使用 /workspace/...）')
  if (!src) return
  const alt = window.prompt('图片说明') ?? ''
  runCommand(insertImageCommand.key, { src, alt })
}

function save() {
  emit('save', publishMdx())
}

function insertMdx(mdx: string, inline = false) {
  if (!editor || !ready.value) return
  editor.action(insertMarkdownContent(mdx, inline))
  publishMdx()
  focus()
}

function handleClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof Element)) return
  const anchor = target.closest<HTMLAnchorElement>('a[href]')
  const href = anchor?.getAttribute('href')
  if (!href?.startsWith('/workspace')) return
  event.preventDefault()
  event.stopPropagation()
  emit('navigate', href)
}

function isWorkspaceNodeDrag(event: DragEvent): boolean {
  return Array.from(event.dataTransfer?.types ?? [])
    .includes('application/x-workspace-node')
}

function handleDragover(event: DragEvent) {
  if (!isWorkspaceNodeDrag(event)) return
  event.preventDefault()
  event.stopPropagation()
}

function handleDrop(event: DragEvent) {
  if (!isWorkspaceNodeDrag(event)) return
  event.preventDefault()
  event.stopPropagation()
  emit('external-drop', event)
}

function handleKeydown(event: KeyboardEvent) {
  if (slashMenuOpen.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      event.stopPropagation()
      moveSlashSelection(1)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      event.stopPropagation()
      moveSlashSelection(-1)
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      event.stopPropagation()
      selectActiveSlashCommand()
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      closeSlashMenu()
      return
    }
  }

  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save()
  }
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (!slashMenuOpen.value) return
  const target = event.target
  if (target instanceof Node && slashMenuRef.value?.contains(target)) return
  closeSlashMenu()
}

function handleDocumentScroll(event: Event) {
  const target = event.target
  if (target instanceof Node && slashMenuRef.value?.contains(target)) return
  closeSlashMenu()
}

function connectCollaboration() {
  if (
    disposed
    || collaborationConnected
    || !editorCreated
    || !providerSynced
    || !editor
    || !collaborationDoc
    || !collaborationProvider?.awareness
  ) {
    return
  }

  try {
    const doc = collaborationDoc
    const awareness = collaborationProvider.awareness
    const xmlFragment = doc.getXmlFragment('prosemirror')

    editorEditable = true
    editor.action((ctx) => {
      ctx.get(collabServiceCtx)
        .bindDoc(doc)
        .setAwareness(awareness)
        .applyTemplate(
          props.modelValue,
          () => xmlFragment.length === 0,
        )
        .connect()
    })

    collaborationConnected = true
    ready.value = true
    collaborationStatus.value = providerSocketStatus === WebSocketStatus.Disconnected
      ? 'disconnected'
      : 'synced'
  } catch (error) {
    editorEditable = false
    console.error('Milkdown collaboration initialization failed:', error)
    collaborationStatus.value = 'error'
    initError.value = '协作编辑初始化失败，请查看控制台。'
  }
}

onMounted(async () => {
  if (!editorRoot.value) return

  document.addEventListener('pointerdown', handleDocumentPointerDown, true)
  document.addEventListener('scroll', handleDocumentScroll, true)
  window.addEventListener('resize', closeSlashMenu)

  collaborationDoc = new Doc()
  try {
    collaborationProvider = new HocuspocusProvider({
      url: getCollaborationURL(),
      name: collaborationDocumentName.value,
      document: collaborationDoc,
      onStatus: ({ status }) => {
        if (disposed) return
        providerSocketStatus = status

        if (status === WebSocketStatus.Connecting) {
          collaborationStatus.value = 'connecting'
          return
        }
        if (status === WebSocketStatus.Disconnected) {
          collaborationStatus.value = 'disconnected'
          return
        }

        collaborationStatus.value = providerSynced
          ? collaborationProvider?.hasUnsyncedChanges
            ? 'syncing'
            : 'synced'
          : 'syncing'
      },
      onSynced: ({ state }) => {
        if (disposed || !state) return
        providerSynced = true
        connectCollaboration()
        if (collaborationConnected) collaborationStatus.value = 'synced'
      },
      onUnsyncedChanges: ({ number }) => {
        if (
          disposed
          || !collaborationConnected
          || providerSocketStatus !== WebSocketStatus.Connected
        ) {
          return
        }
        collaborationStatus.value = number > 0 ? 'syncing' : 'synced'
      },
      onAuthenticationFailed: ({ reason }) => {
        if (disposed) return
        console.error('Hocuspocus authentication failed:', reason)
        collaborationStatus.value = 'error'
      },
    })
    collaborationProvider.setAwarenessField('user', collaborationUser)
  } catch (error) {
    console.error('Hocuspocus provider initialization failed:', error)
    collaborationStatus.value = 'error'
    initError.value = '协作服务初始化失败，请查看控制台。'
    return
  }

  editor = Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, editorRoot.value!)
      ctx.set(defaultValueCtx, props.modelValue)
      ctx.get(listenerCtx).markdownUpdated((_ctx, markdown) => {
        if (!disposed && markdown !== props.modelValue) {
          emit('update:modelValue', markdown)
        }
      })
      ctx.update(editorViewOptionsCtx, (options) => ({
        ...options,
        editable: () => editorEditable,
      }))
      ctx.update(inlineImageConfig.key, (config) => ({
        ...config,
        proxyDomURL: resolveMediaURL,
      }))
    })
    .use(commonmark)
    .use(createMdxPlugins(resolveMediaURL))
    .use(listener)
    .use(collab)
    .use(indent)
    .use(trailing)
    .use(clipboard)
    .use(cursor)
    .use(gfm)
    .use(imageInlineComponent)
    .use(mediaHtmlView)
    .use(slashMenuPlugin)

  try {
    await editor.create()
    if (disposed) {
      await editor.destroy()
      editor = null
      return
    }
    editorCreated = true
    connectCollaboration()
  } catch (error) {
    console.error('Milkdown editor initialization failed:', error)
    initError.value = '编辑器初始化失败，请查看控制台。'
  }
})

onBeforeUnmount(() => {
  disposed = true
  ready.value = false
  editorEditable = false
  editorCreated = false
  providerSynced = false
  document.removeEventListener('pointerdown', handleDocumentPointerDown, true)
  document.removeEventListener('scroll', handleDocumentScroll, true)
  window.removeEventListener('resize', closeSlashMenu)
  closeSlashMenu()

  if (editor && collaborationConnected) {
    try {
      editor.action((ctx) => {
        ctx.get(collabServiceCtx).disconnect()
      })
    } catch (error) {
      console.warn('Milkdown collaboration disconnect failed:', error)
    }
  }
  collaborationConnected = false

  collaborationProvider?.destroy()
  collaborationProvider = null
  collaborationDoc?.destroy()
  collaborationDoc = null

  if (editor) {
    void editor.destroy()
    editor = null
  }
})

defineExpose({
  focus,
  getMdx,
  insertMdx,
})
</script>

<template>
  <div
    class="collaborative-editor"
    @click="handleClick"
    @dragover.capture="handleDragover"
    @drop.capture="handleDrop"
    @keydown.capture="handleKeydown"
  >
    <div class="collaborative-editor__toolbar" role="toolbar" aria-label="MDX formatting">
      <div class="editor-tool-group">
        <button type="button" class="editor-tool-btn" title="撤销" :disabled="!ready" @mousedown.prevent="runCollaborationHistory(collaborationUndoCommand)">↶</button>
        <button type="button" class="editor-tool-btn" title="重做" :disabled="!ready" @mousedown.prevent="runCollaborationHistory(collaborationRedoCommand)">↷</button>
      </div>

      <div class="editor-tool-group">
        <button type="button" class="editor-tool-btn" title="正文" :disabled="!ready" @mousedown.prevent="runCommand(turnIntoTextCommand.key)">P</button>
        <button type="button" class="editor-tool-btn" title="一级标题" :disabled="!ready" @mousedown.prevent="runCommand(wrapInHeadingCommand.key, 1)">H1</button>
        <button type="button" class="editor-tool-btn" title="二级标题" :disabled="!ready" @mousedown.prevent="runCommand(wrapInHeadingCommand.key, 2)">H2</button>
        <button type="button" class="editor-tool-btn" title="三级标题" :disabled="!ready" @mousedown.prevent="runCommand(wrapInHeadingCommand.key, 3)">H3</button>
      </div>

      <div class="editor-tool-group">
        <button type="button" class="editor-tool-btn editor-tool-btn--strong" title="粗体" :disabled="!ready" @mousedown.prevent="runCommand(toggleStrongCommand.key)">B</button>
        <button type="button" class="editor-tool-btn editor-tool-btn--emphasis" title="斜体" :disabled="!ready" @mousedown.prevent="runCommand(toggleEmphasisCommand.key)">I</button>
        <button type="button" class="editor-tool-btn editor-tool-btn--strike" title="删除线" :disabled="!ready" @mousedown.prevent="runCommand(toggleStrikethroughCommand.key)">S</button>
        <button type="button" class="editor-tool-btn editor-tool-btn--code" title="行内代码" :disabled="!ready" @mousedown.prevent="runCommand(toggleInlineCodeCommand.key)">&lt;/&gt;</button>
      </div>

      <div class="editor-tool-group">
        <button type="button" class="editor-tool-btn" title="引用" :disabled="!ready" @mousedown.prevent="runCommand(wrapInBlockquoteCommand.key)">❯</button>
        <button type="button" class="editor-tool-btn" title="无序列表" :disabled="!ready" @mousedown.prevent="runCommand(wrapInBulletListCommand.key)">• List</button>
        <button type="button" class="editor-tool-btn" title="有序列表" :disabled="!ready" @mousedown.prevent="runCommand(wrapInOrderedListCommand.key)">1. List</button>
        <button type="button" class="editor-tool-btn" title="代码块" :disabled="!ready" @mousedown.prevent="runCommand(createCodeBlockCommand.key)">{ }</button>
      </div>

      <div class="editor-tool-group">
        <button type="button" class="editor-tool-btn" title="链接" :disabled="!ready" @mousedown.prevent="requestLink">Link</button>
        <button type="button" class="editor-tool-btn" title="图片" :disabled="!ready" @mousedown.prevent="requestImage">Image</button>
        <button type="button" class="editor-tool-btn" title="表格" :disabled="!ready" @mousedown.prevent="runCommand(insertTableCommand.key, { row: 3, col: 3 })">Table</button>
        <button type="button" class="editor-tool-btn" title="分隔线" :disabled="!ready" @mousedown.prevent="runCommand(insertHrCommand.key)">—</button>
      </div>

      <span class="editor-save-state" :class="{ dirty }">
        {{ saving ? '保存中…' : dirty ? '未保存' : '已保存' }}
      </span>
      <span
        class="editor-collaboration-state"
        :class="`is-${collaborationStatus}`"
        :title="`实时协作：${collaborationStatusLabel}`"
      >
        <span class="editor-collaboration-state__dot"></span>
        {{ collaborationStatusLabel }}
      </span>
      <button
        type="button"
        class="editor-save-btn"
        :disabled="!dirty || saving || !ready"
        title="保存（⌘/Ctrl + S）"
        @mousedown.prevent="save"
      >
        {{ saving ? 'Saving…' : 'Save' }}
      </button>
    </div>

    <div class="collaborative-editor__body">
      <div v-if="!ready && !initError" class="editor-loading">
        正在连接协作服务…
      </div>
      <div v-if="initError" class="editor-error">{{ initError }}</div>
      <div ref="editorRoot" class="collaborative-editor__root"></div>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="slashMenuOpen"
      ref="slashMenuRef"
      class="slash-command-menu"
      :style="{
        top: `${slashMenuPosition.top}px`,
        left: `${slashMenuPosition.left}px`,
      }"
      role="listbox"
      aria-label="斜杠命令"
      @mousedown.prevent
    >
      <div class="slash-command-menu__header">
        <span>基础模块</span>
        <kbd>↑↓ 选择 · Enter 确认 · Esc 关闭</kbd>
      </div>
      <div class="slash-command-menu__options">
        <button
          v-for="(command, index) in slashCommands"
          :key="command.id"
          type="button"
          class="slash-command-item"
          :class="{ active: slashActiveIndex === index }"
          :data-slash-index="index"
          role="option"
          :aria-selected="slashActiveIndex === index"
          @mouseenter="slashActiveIndex = index"
          @mousedown.prevent.stop="executeSlashCommand(command)"
        >
          <span class="slash-command-item__icon">{{ command.icon }}</span>
          <span class="slash-command-item__copy">
            <strong>{{ command.label }}</strong>
            <small>{{ command.description }}</small>
          </span>
        </button>
      </div>
    </div>
  </Teleport>
</template>
