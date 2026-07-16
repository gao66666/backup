<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

type ApiNode = {
  id: string
  title: string
  type: string
  parent_id: string | null
  has_children: boolean
  dirty?: boolean
}

const props = defineProps<{
  node: ApiNode
  nodesMap: Map<string, ApiNode>
  expanded: Set<string>
  selected: string | null
  depth: number
  dropTargetId: string | null
  renameTargetId: string | null
}>()

const emit = defineEmits<{
  toggle: [nodeId: string]
  select: [nodeId: string]
  contextmenu: [event: MouseEvent, nodeId: string, type: 'collection' | 'doc' | 'image' | 'video' | 'audio', name: string]
  dragstart: [nodeId: string, event: DragEvent]
  dragover: [nodeId: string, event: DragEvent]
  drop: [nodeId: string, event: DragEvent]
  'rename-commit': [nodeId: string, newTitle: string]
  'rename-cancel': [nodeId: string]
}>()

function getChildren(parentId: string, nodesMap: Map<string, ApiNode>): ApiNode[] {
  return [...nodesMap.values()]
    .filter(n => n.parent_id === parentId)
    .sort((a, b) => {
      const aIsFolder = a.type === 'collection' ? 0 : 1
      const bIsFolder = b.type === 'collection' ? 0 : 1
      if (aIsFolder !== bIsFolder) return aIsFolder - bIsFolder
      return a.title.localeCompare(b.title)
    })
}

const isRenaming = computed(() => props.renameTargetId === props.node.id)
const isDirty = computed(() => props.node.dirty === true)
const editingTitle = ref(props.node.title)
const inputRef = ref<HTMLInputElement | null>(null)

// 进入 rename 态时,初始化为当前 title 并自动 focus + select
watch(isRenaming, async (on) => {
  if (on) {
    editingTitle.value = props.node.title
    await nextTick()
    inputRef.value?.focus()
    inputRef.value?.select()
  }
})

function commit() {
  emit('rename-commit', props.node.id, editingTitle.value.trim())
}
function cancel() {
  emit('rename-cancel', props.node.id)
}

function handleNodeClick() {
  if (isRenaming.value) return

  // 文件夹只负责展开/收起，不进入编辑器。
  if (props.node.type === 'collection') {
    if (props.node.has_children) emit('toggle', props.node.id)
    return
  }

  // 文件节点始终打开；若它也有子节点，同时展开/收起子节点。
  if (props.node.has_children) emit('toggle', props.node.id)
  emit('select', props.node.id)
}
</script>

<template>
  <li>
    <button
      type="button"
      class="tree-item"
      :class="{
        'tree-file': node.type !== 'collection',
        selected: selected === node.id && node.type !== 'collection',
        'drop-target': dropTargetId === node.id,
      }"
      :style="{ paddingLeft: `${8 + depth * 14}px` }"
      draggable="true"
      @click="handleNodeClick"
      @contextmenu="emit('contextmenu', $event, node.id, node.type as 'collection' | 'doc' | 'image' | 'video' | 'audio', node.title)"
      @dragstart="emit('dragstart', node.id, $event)"
      @dragover="emit('dragover', node.id, $event)"
      @drop="emit('drop', node.id, $event)"
    >
      <span class="chevron">
        {{ node.has_children ? (expanded.has(node.id) ? '▾' : '▸') : '' }}
      </span>
      <span class="node-icon">
        <svg v-if="node.type === 'collection'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>
        <svg v-else-if="node.type === 'doc'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
        <svg v-else-if="node.type === 'image'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
        <svg v-else-if="node.type === 'video'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
        <svg v-else-if="node.type === 'audio'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      </span>
      <span class="node-name">
        <input
          v-if="isRenaming"
          ref="inputRef"
          v-model="editingTitle"
          class="node-rename-input"
          @keydown.enter.prevent="commit"
          @keydown.esc.prevent="cancel"
          @blur="cancel"
        />
        <template v-else>
          {{ node.title }}
          <span v-if="isDirty" class="dirty-dot" title="未保存"></span>
        </template>
      </span>
    </button>

    <!-- Drop placeholder line above/below can be shown here -->
    <ul v-if="expanded.has(node.id)" class="tree-branch">
      <FileTreeItem
        v-for="child in getChildren(node.id, nodesMap)"
        :key="child.id"
        :node="child"
        :nodes-map="nodesMap"
        :expanded="expanded"
        :selected="selected"
        :depth="depth + 1"
        :drop-target-id="dropTargetId"
        :rename-target-id="renameTargetId"
        @toggle="emit('toggle', $event)"
        @select="emit('select', $event)"
        @contextmenu="(e: MouseEvent, id: string, t: 'collection' | 'doc' | 'image' | 'video' | 'audio', n: string) => emit('contextmenu', e, id, t, n)"
        @dragstart="(id: string, e: DragEvent) => emit('dragstart', id, e)"
        @dragover="(id: string, e: DragEvent) => emit('dragover', id, e)"
        @drop="(id: string, e: DragEvent) => emit('drop', id, e)"
        @rename-commit="(id: string, newTitle: string) => emit('rename-commit', id, newTitle)"
        @rename-cancel="(id: string) => emit('rename-cancel', id)"
      />
    </ul>
  </li>
</template>
