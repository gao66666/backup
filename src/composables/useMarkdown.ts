import MarkdownIt from 'markdown-it'

export interface ResolvedNode {
  id: string
  type: string   // 'image' | 'video' | 'audio' | 'doc' | 'collection'
  src: string    // MinIO presigned URL, 非上传文件为空
}

/**
 * 工作空间路径解析器。
 * 入参:用户写的路径,如 `/workspace/folder/file.pdf`
 * 返回:节点信息或 null
 */
export type NodeResolver = (path: string) => ResolvedNode | null

export function useMarkdown(resolveNode?: NodeResolver) {
  const md = new MarkdownIt({
    html: true,
    linkify: true,
    typographer: true,
  })

  // ── link_open ──
  const defaultLinkRender = md.renderer.rules.link_open || function (tokens, idx, options, _env, self) {
    return self.renderToken(tokens, idx, options)
  }

  md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
    const href = tokens[idx].attrGet('href')
    if (!href || /^https?:\/\//.test(href) || !resolveNode) {
      return defaultLinkRender(tokens, idx, options, env, self)
    }
    const node = resolveNode(href)
    if (!node) return defaultLinkRender(tokens, idx, options, env, self)

    if (node.type === 'image' || node.type === 'video' || node.type === 'audio') {
      if (node.src) tokens[idx].attrSet('href', node.src)
      return defaultLinkRender(tokens, idx, options, env, self)
    }

    // doc / collection / 其他:标记为内部导航链接
    tokens[idx].attrSet('data-workspace-node', node.id)
    return defaultLinkRender(tokens, idx, options, env, self)
  }

  // ── image: ![alt](path) ──
  const defaultImageRender = md.renderer.rules.image || function (tokens, idx, options, _env, self) {
    return self.renderToken(tokens, idx, options)
  }

  md.renderer.rules.image = function (tokens, idx, options, env, self) {
    const src = tokens[idx].attrGet('src')
    if (src && !/^https?:\/\//.test(src) && resolveNode) {
      const node = resolveNode(src)
      if (node?.src) tokens[idx].attrSet('src', node.src)
    }
    return defaultImageRender(tokens, idx, options, env, self)
  }

  // ── 后处理:原始 HTML <video>/<audio> src ──
  function resolveHtmlMediaSrc(html: string): string {
    if (!resolveNode) return html
    return html.replace(/(<(?:video|audio)\b[^>]*?\s)src="([^"]+)("[^>]*>)/gi, (match, before, src, after) => {
      if (/^https?:\/\//.test(src)) return match
      const node = resolveNode(src)
      if (node?.src) return before + 'src="' + node.src + after
      return match
    })
  }

  // ── 后处理:内部导航链接 href → # ──
  function resolveInternalLinks(html: string): string {
    return html.replace(/ href="[^"]*" data-workspace-node="([^"]+)"/g, ' href="#" data-workspace-node="$1"')
  }

  function render(content: string): string {
    if (!content) return ''
    let text: string
    try {
      const obj = JSON.parse(content)
      text = obj.text || content
    } catch {
      text = content
    }
    const raw = md.render(text)
    return resolveInternalLinks(resolveHtmlMediaSrc(raw))
  }

  return { render }
}
