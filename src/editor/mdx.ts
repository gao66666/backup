import { InputRule } from '@milkdown/kit/prose/inputrules'
import type { Node as ProseNode } from '@milkdown/kit/prose/model'
import { TextSelection } from '@milkdown/kit/prose/state'
import type { NodeViewConstructor } from '@milkdown/kit/prose/view'
import type { JSONValue, MarkdownNode } from '@milkdown/kit/transformer'
import {
  $inputRule,
  $nodeSchema,
  $remark,
  $view,
} from '@milkdown/kit/utils'
import { remark } from 'remark'
import remarkMdx from 'remark-mdx'

type MdxAttribute =
  | {
    type: 'mdxJsxAttribute'
    name: string
    value: string | null | {
      type: 'mdxJsxAttributeValueExpression'
      value: string
    }
  }
  | {
    type: 'mdxJsxExpressionAttribute'
    value: string
  }

type ParsedMdxElement = {
  name: string
  attributes: MdxAttribute[]
}

type ResolveMediaURL = (path: string) => string | Promise<string>

const mdxParser = remark().use(remarkMdx)

export const mdxRemark = $remark('workspaceMdx', () => remarkMdx)

function normalizeAttributes(value: unknown): MdxAttribute[] {
  if (!Array.isArray(value)) return []

  return value.flatMap((item): MdxAttribute[] => {
    if (!item || typeof item !== 'object') return []
    const candidate = item as Record<string, unknown>

    if (
      candidate.type === 'mdxJsxExpressionAttribute'
      && typeof candidate.value === 'string'
    ) {
      return [{
        type: 'mdxJsxExpressionAttribute',
        value: candidate.value,
      }]
    }

    if (
      candidate.type !== 'mdxJsxAttribute'
      || typeof candidate.name !== 'string'
    ) {
      return []
    }

    const rawValue = candidate.value
    if (rawValue === null || rawValue === undefined) {
      return [{
        type: 'mdxJsxAttribute',
        name: candidate.name,
        value: null,
      }]
    }
    if (typeof rawValue === 'string') {
      return [{
        type: 'mdxJsxAttribute',
        name: candidate.name,
        value: rawValue,
      }]
    }
    if (typeof rawValue === 'object') {
      const expression = rawValue as Record<string, unknown>
      if (
        expression.type === 'mdxJsxAttributeValueExpression'
        && typeof expression.value === 'string'
      ) {
        return [{
          type: 'mdxJsxAttribute',
          name: candidate.name,
          value: {
            type: 'mdxJsxAttributeValueExpression',
            value: expression.value,
          },
        }]
      }
    }

    return []
  })
}

function attributesToJSON(attributes: MdxAttribute[]): string {
  return JSON.stringify(attributes)
}

function attributesFromJSON(value: unknown): MdxAttribute[] {
  if (typeof value !== 'string') return []
  try {
    return normalizeAttributes(JSON.parse(value))
  } catch {
    return []
  }
}

function mdxNodeAttributes(node: MarkdownNode): MdxAttribute[] {
  return normalizeAttributes(node.attributes)
}

function mdxElementAttrs(node: MarkdownNode) {
  return {
    name: typeof node.name === 'string' ? node.name : '',
    attributes: attributesToJSON(mdxNodeAttributes(node)),
  }
}

function toMarkdownAttributes(node: ProseNode): JSONValue[] {
  return attributesFromJSON(node.attrs.attributes) as JSONValue[]
}

function mdxElementProps(node: ProseNode) {
  return {
    name: String(node.attrs.name || '') || null,
    attributes: toMarkdownAttributes(node),
  }
}

function mdxElementAtomSchema(
  id: 'mdxJsxFlowElementAtom' | 'mdxJsxTextElementAtom',
  mdastType: 'mdxJsxFlowElement' | 'mdxJsxTextElement',
  inline: boolean,
) {
  return $nodeSchema(id, () => ({
    atom: true,
    inline,
    group: inline ? 'inline' : 'block',
    defining: true,
    isolating: true,
    selectable: true,
    attrs: {
      name: { default: '', validate: 'string' },
      attributes: { default: '[]', validate: 'string' },
    },
    parseDOM: [{
      tag: `${inline ? 'span' : 'div'}[data-mdx-${inline ? 'text' : 'flow'}-atom]`,
      getAttrs: (dom) => ({
        name: dom.dataset.mdxName ?? '',
        attributes: dom.dataset.mdxAttributes ?? '[]',
      }),
    }],
    toDOM: (node) => [
      inline ? 'span' : 'div',
      {
        [`data-mdx-${inline ? 'text' : 'flow'}-atom`]: '',
        'data-mdx-name': node.attrs.name,
        'data-mdx-attributes': node.attrs.attributes,
      },
    ],
    parseMarkdown: {
      match: (node) => node.type === mdastType && !node.children?.length,
      runner: (state, node, type) => {
        state.addNode(type, mdxElementAttrs(node))
      },
    },
    toMarkdown: {
      match: (node) => node.type.name === id,
      runner: (state, node) => {
        state.addNode(mdastType, [], undefined, mdxElementProps(node))
      },
    },
  }))
}

export const mdxFlowElementAtomSchema = mdxElementAtomSchema(
  'mdxJsxFlowElementAtom',
  'mdxJsxFlowElement',
  false,
)

export const mdxTextElementAtomSchema = mdxElementAtomSchema(
  'mdxJsxTextElementAtom',
  'mdxJsxTextElement',
  true,
)

export const mdxFlowElementSchema = $nodeSchema(
  'mdxJsxFlowElement',
  () => ({
    content: 'block*',
    group: 'block',
    defining: true,
    isolating: true,
    attrs: {
      name: { default: '', validate: 'string' },
      attributes: { default: '[]', validate: 'string' },
    },
    parseDOM: [{
      tag: 'div[data-mdx-flow-element]',
      getAttrs: (dom) => ({
        name: dom.dataset.mdxName ?? '',
        attributes: dom.dataset.mdxAttributes ?? '[]',
      }),
    }],
    toDOM: (node) => [
      'div',
      {
        'data-mdx-flow-element': '',
        'data-mdx-name': node.attrs.name,
        'data-mdx-attributes': node.attrs.attributes,
      },
      0,
    ],
    parseMarkdown: {
      match: (node) => node.type === 'mdxJsxFlowElement' && Boolean(node.children?.length),
      runner: (state, node, type) => {
        state
          .openNode(type, mdxElementAttrs(node))
          .next(node.children)
          .closeNode()
      },
    },
    toMarkdown: {
      match: (node) => node.type.name === 'mdxJsxFlowElement',
      runner: (state, node) => {
        state
          .openNode('mdxJsxFlowElement', undefined, mdxElementProps(node))
          .next(node.content)
          .closeNode()
      },
    },
  })
)

export const mdxTextElementSchema = $nodeSchema(
  'mdxJsxTextElement',
  () => ({
    content: 'inline*',
    group: 'inline',
    inline: true,
    defining: true,
    isolating: true,
    attrs: {
      name: { default: '', validate: 'string' },
      attributes: { default: '[]', validate: 'string' },
    },
    parseDOM: [{
      tag: 'span[data-mdx-text-element]',
      getAttrs: (dom) => ({
        name: dom.dataset.mdxName ?? '',
        attributes: dom.dataset.mdxAttributes ?? '[]',
      }),
    }],
    toDOM: (node) => [
      'span',
      {
        'data-mdx-text-element': '',
        'data-mdx-name': node.attrs.name,
        'data-mdx-attributes': node.attrs.attributes,
      },
      0,
    ],
    parseMarkdown: {
      match: (node) => node.type === 'mdxJsxTextElement' && Boolean(node.children?.length),
      runner: (state, node, type) => {
        state
          .openNode(type, mdxElementAttrs(node))
          .next(node.children)
          .closeNode()
      },
    },
    toMarkdown: {
      match: (node) => node.type.name === 'mdxJsxTextElement',
      runner: (state, node) => {
        state
          .openNode('mdxJsxTextElement', undefined, mdxElementProps(node))
          .next(node.content)
          .closeNode()
      },
    },
  })
)

function expressionSchema(
  id: 'mdxFlowExpression' | 'mdxTextExpression',
  inline: boolean,
) {
  return $nodeSchema(id, () => ({
    atom: true,
    inline,
    group: inline ? 'inline' : 'block',
    selectable: true,
    attrs: {
      value: { default: '', validate: 'string' },
    },
    parseDOM: [{
      tag: `${inline ? 'span' : 'div'}[data-mdx-expression]`,
      getAttrs: (dom) => ({ value: dom.dataset.mdxExpression ?? '' }),
    }],
    toDOM: (node) => [
      inline ? 'span' : 'div',
      { 'data-mdx-expression': node.attrs.value },
      `{${node.attrs.value}}`,
    ],
    parseMarkdown: {
      match: ({ type }) => type === id,
      runner: (state, node, type) => {
        state.addNode(type, { value: String(node.value ?? '') })
      },
    },
    toMarkdown: {
      match: (node) => node.type.name === id,
      runner: (state, node) => {
        state.addNode(id, undefined, String(node.attrs.value ?? ''))
      },
    },
  }))
}

export const mdxFlowExpressionSchema = expressionSchema(
  'mdxFlowExpression',
  false,
)
export const mdxTextExpressionSchema = expressionSchema(
  'mdxTextExpression',
  true,
)

export const mdxEsmSchema = $nodeSchema('mdxjsEsm', () => ({
  atom: true,
  group: 'block',
  selectable: true,
  code: true,
  attrs: {
    value: { default: '', validate: 'string' },
  },
  parseDOM: [{
    tag: 'pre[data-mdx-esm]',
    getAttrs: (dom) => ({ value: dom.dataset.mdxEsm ?? '' }),
  }],
  toDOM: (node) => [
    'pre',
    { 'data-mdx-esm': node.attrs.value },
    ['code', {}, node.attrs.value],
  ],
  parseMarkdown: {
    match: ({ type }) => type === 'mdxjsEsm',
    runner: (state, node, type) => {
      state.addNode(type, { value: String(node.value ?? '') })
    },
  },
  toMarkdown: {
    match: (node) => node.type.name === 'mdxjsEsm',
    runner: (state, node) => {
      state.addNode('mdxjsEsm', undefined, String(node.attrs.value ?? ''))
    },
  },
}))

function parseSingleElement(source: string): ParsedMdxElement | null {
  try {
    const tree = mdxParser.parse(source) as MarkdownNode
    const node = tree.children?.[0]
    if (
      !node
      || !['mdxJsxFlowElement', 'mdxJsxTextElement'].includes(node.type)
      || typeof node.name !== 'string'
    ) {
      return null
    }

    return {
      name: node.name,
      attributes: mdxNodeAttributes(node),
    }
  } catch {
    return null
  }
}

function formatAttribute(attribute: MdxAttribute): string {
  if (attribute.type === 'mdxJsxExpressionAttribute') {
    return `{...${attribute.value}}`
  }
  if (attribute.value === null) return attribute.name
  if (typeof attribute.value === 'string') {
    return `${attribute.name}=${JSON.stringify(attribute.value)}`
  }
  return `${attribute.name}={${attribute.value.value}}`
}

function formatOpeningTag(node: ProseNode, selfClosing: boolean): string {
  const name = String(node.attrs.name || 'Fragment')
  const attributes = attributesFromJSON(node.attrs.attributes)
    .map(formatAttribute)
    .join(' ')
  return `<${name}${attributes ? ` ${attributes}` : ''}${selfClosing ? ' /' : ''}>`
}

function getStaticAttribute(node: ProseNode, name: string): string | null {
  const attribute = attributesFromJSON(node.attrs.attributes).find(
    (item) => item.type === 'mdxJsxAttribute' && item.name === name,
  )
  if (!attribute || attribute.type !== 'mdxJsxAttribute') return null
  return typeof attribute.value === 'string' ? attribute.value : null
}

function createComponentView(
  inline: boolean,
  resolveMediaURL?: ResolveMediaURL,
): NodeViewConstructor {
  return (initialNode, view, getPos) => {
    let currentNode = initialNode
    let renderVersion = 0
    const dom = document.createElement(inline ? 'span' : 'div')
    const header = document.createElement(inline ? 'span' : 'div')
    const contentDOM = document.createElement(inline ? 'span' : 'div')
    const initiallyHasContent = initialNode.childCount > 0

    header.contentEditable = 'false'
    header.className = 'mdx-component__header'
    contentDOM.className = 'mdx-component__content'

    const render = (node: ProseNode) => {
      const currentRender = ++renderVersion
      const name = String(node.attrs.name || 'Fragment')
      const selfClosing = node.childCount === 0
      const tone = getStaticAttribute(node, 'type')
        ?? getStaticAttribute(node, 'tone')
        ?? 'info'

      dom.className = inline
        ? `mdx-component mdx-component--inline mdx-component--${tone}`
        : `mdx-component mdx-component--flow mdx-component--${tone}`
      dom.dataset.mdxName = name
      dom.dataset.mdxAttributes = String(node.attrs.attributes || '[]')
      header.replaceChildren()

      if (name === 'video' || name === 'audio') {
        const media = document.createElement(name)
        const source = getStaticAttribute(node, 'src') ?? ''
        media.controls = true
        media.preload = 'metadata'
        if (media instanceof HTMLVideoElement) media.playsInline = true
        header.className = 'mdx-component__header mdx-component__header--media'
        dom.classList.add('mdx-component--media')
        Promise.resolve(resolveMediaURL?.(source) ?? source)
          .then((resolved) => {
            if (currentRender === renderVersion) media.src = resolved
          })
          .catch(() => {
            if (currentRender === renderVersion) media.src = source
          })
        header.append(media)
        return
      }

      header.className = 'mdx-component__header'
      header.textContent = name === 'Callout'
        ? `${tone === 'warning' ? '⚠' : 'ⓘ'} ${getStaticAttribute(node, 'title') ?? tone}`
        : formatOpeningTag(node, selfClosing)

      if (inline && name === 'Badge') {
        header.textContent = getStaticAttribute(node, 'label')
          ?? getStaticAttribute(node, 'text')
          ?? 'Badge'
      }
    }

    header.addEventListener('dblclick', (event) => {
      event.preventDefault()
      event.stopPropagation()
      const selfClosing = currentNode.childCount === 0
      const source = window.prompt(
        '编辑 MDX 组件标签',
        formatOpeningTag(currentNode, selfClosing),
      )
      if (!source) return

      const name = String(currentNode.attrs.name || '')
      const parsed = parseSingleElement(
        source.trimEnd().endsWith('/>')
          ? source
          : `${source}\n\n</${name}>`,
      )
      const position = getPos()
      if (!parsed || position === undefined) return

      view.dispatch(view.state.tr.setNodeMarkup(position, undefined, {
        name: parsed.name,
        attributes: attributesToJSON(parsed.attributes),
      }))
    })

    dom.append(header)
    if (initiallyHasContent) dom.append(contentDOM)
    else dom.contentEditable = 'false'
    render(initialNode)

    return {
      dom,
      contentDOM: initiallyHasContent ? contentDOM : undefined,
      update(nextNode) {
        if (
          nextNode.type !== initialNode.type
          || (nextNode.childCount > 0) !== initiallyHasContent
        ) {
          return false
        }
        currentNode = nextNode
        render(nextNode)
        return true
      },
    }
  }
}

function createSourceView(
  className: string,
  label: string,
  wrap: (value: string) => string,
): NodeViewConstructor {
  return (initialNode, view, getPos) => {
    let currentNode = initialNode
    const dom = document.createElement(initialNode.isInline ? 'span' : 'div')
    const badge = document.createElement('span')
    const code = document.createElement('code')

    dom.contentEditable = 'false'
    dom.className = className
    badge.className = 'mdx-source-node__label'
    badge.textContent = label
    dom.append(badge, code)

    const render = (node: ProseNode) => {
      code.textContent = wrap(String(node.attrs.value ?? ''))
    }

    dom.addEventListener('dblclick', (event) => {
      event.preventDefault()
      event.stopPropagation()
      const value = window.prompt(`编辑 ${label}`, String(currentNode.attrs.value ?? ''))
      const position = getPos()
      if (value === null || position === undefined) return
      view.dispatch(view.state.tr.setNodeMarkup(position, undefined, { value }))
    })

    render(initialNode)
    return {
      dom,
      update(nextNode) {
        if (nextNode.type !== initialNode.type) return false
        currentNode = nextNode
        render(nextNode)
        return true
      },
    }
  }
}

export const mdxFlowExpressionView = $view(
  mdxFlowExpressionSchema.node,
  () => createSourceView(
    'mdx-source-node mdx-source-node--flow-expression',
    'MDX',
    (value) => `{${value}}`,
  ),
)
export const mdxTextExpressionView = $view(
  mdxTextExpressionSchema.node,
  () => createSourceView(
    'mdx-source-node mdx-source-node--text-expression',
    'fx',
    (value) => `{${value}}`,
  ),
)
export const mdxEsmView = $view(
  mdxEsmSchema.node,
  () => createSourceView(
    'mdx-source-node mdx-source-node--esm',
    'MDX module',
    (value) => value,
  ),
)

const selfClosingComponentPattern =
  /(<[A-Z][\w.:-]*(?:\s+(?:[^>"']|"[^"]*"|'[^']*')*)?\s*\/>)$/

export const mdxSelfClosingInputRule = $inputRule(
  (ctx) => new InputRule(
    selfClosingComponentPattern,
    (state, match, start, end) => {
      const source = match[1]
      if (!source) return null
      const parsed = parseSingleElement(source)
      if (!parsed) return null

      const attributes = {
        name: parsed.name,
        attributes: attributesToJSON(parsed.attributes),
      }
      const { $from } = state.selection

      // InputRule handlers receive the document before the final typed
      // character is inserted, while `match` already includes it.
      if ($from.parentOffset + 1 === source.length) {
        const node = mdxFlowElementAtomSchema.type(ctx).create(attributes)
        return state.tr.replaceWith($from.before(), $from.after(), node)
      }

      const node = mdxTextElementAtomSchema.type(ctx).create(attributes)
      return state.tr.replaceWith(start, end, node)
    },
  ),
)

const openingComponentPattern =
  /^(<[A-Z][\w.:-]*(?:\s+(?:[^>"']|"[^"]*"|'[^']*')*)?\s*>)$/

export const mdxOpeningComponentInputRule = $inputRule(
  (ctx) => new InputRule(
    openingComponentPattern,
    (state, match) => {
      const source = match[1]
      const name = source?.match(/^<([A-Z][\w.:-]*)/)?.[1]
      if (!source || !name || source.startsWith('</')) return null

      const parsed = parseSingleElement(`${source}\n\n</${name}>`)
      if (!parsed) return null

      const paragraph = state.schema.nodes.paragraph?.create()
      if (!paragraph) return null
      const node = mdxFlowElementSchema.type(ctx).create(
        {
          name: parsed.name,
          attributes: attributesToJSON(parsed.attributes),
        },
        paragraph,
      )
      const { $from } = state.selection
      const blockStart = $from.before()
      const tr = state.tr.replaceWith(blockStart, $from.after(), node)
      return tr.setSelection(TextSelection.near(tr.doc.resolve(blockStart + 2)))
    },
  ),
)

const expressionPattern = /\{([^{}\n]+)\}$/

export const mdxExpressionInputRule = $inputRule(
  (ctx) => new InputRule(
    expressionPattern,
    (state, match, start, end) => {
      const value = match[1]
      if (!value || state.selection.$from.parent.type.spec.code) return null
      const { $from } = state.selection
      const textBefore = $from.parent.textBetween(
        0,
        $from.parentOffset,
        undefined,
        '\uFFFC',
      )
      if (textBefore.lastIndexOf('<') > textBefore.lastIndexOf('>')) {
        return null
      }

      if ($from.parentOffset + 1 === match[0].length) {
        const node = mdxFlowExpressionSchema.type(ctx).create({ value })
        return state.tr.replaceWith($from.before(), $from.after(), node)
      }

      const node = mdxTextExpressionSchema.type(ctx).create({ value })
      return state.tr.replaceWith(start, end, node)
    },
  ),
)

export function createMdxPlugins(resolveMediaURL?: ResolveMediaURL) {
  const mdxFlowElementView = $view(
    mdxFlowElementSchema.node,
    () => createComponentView(false, resolveMediaURL),
  )
  const mdxFlowElementAtomView = $view(
    mdxFlowElementAtomSchema.node,
    () => createComponentView(false, resolveMediaURL),
  )
  const mdxTextElementView = $view(
    mdxTextElementSchema.node,
    () => createComponentView(true, resolveMediaURL),
  )
  const mdxTextElementAtomView = $view(
    mdxTextElementAtomSchema.node,
    () => createComponentView(true, resolveMediaURL),
  )

  return [
    mdxRemark,
    mdxFlowElementAtomSchema,
    mdxTextElementAtomSchema,
    mdxFlowElementSchema,
    mdxTextElementSchema,
    mdxFlowExpressionSchema,
    mdxTextExpressionSchema,
    mdxEsmSchema,
    mdxSelfClosingInputRule,
    mdxOpeningComponentInputRule,
    mdxExpressionInputRule,
    mdxFlowElementAtomView,
    mdxTextElementAtomView,
    mdxFlowElementView,
    mdxTextElementView,
    mdxFlowExpressionView,
    mdxTextExpressionView,
    mdxEsmView,
  ].flat()
}
