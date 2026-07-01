import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
})

// Intercept link_open to handle @[video](url) and @[audio](url) syntax
const defaultRender = md.renderer.rules.link_open || function (tokens, idx, options, _env, self) {
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
  const href = tokens[idx].attrGet('href')
  if (!href) return defaultRender(tokens, idx, options, env, self)

  // @[video](url) -> <video>
  const videoMatch = href.match(/^@\[video\]\((.+)\)$/)
  if (videoMatch) {
    return `<video src="${videoMatch[1]}" controls playsinline style="width:100%;border-radius:8px;margin:8px 0;"></video>`
  }

  // @[audio](url) -> <audio>
  const audioMatch = href.match(/^@\[audio\]\((.+)\)$/)
  if (audioMatch) {
    return `<audio src="${audioMatch[1]}" controls style="width:100%;margin:8px 0;"></audio>`
  }

  // @[file](url) -> file card
  const fileMatch = href.match(/^@\[file\]\((.+)\)$/)
  if (fileMatch) {
    const url = fileMatch[1]
    const filename = url.split('/').pop() || 'file'
    return `<a href="${url}" target="_blank" style="display:inline-flex;align-items:center;gap:8px;padding:8px 12px;border-radius:8px;border:1px solid var(--color-border-subtle);background:var(--color-surface-panel);color:var(--color-ink-secondary);text-decoration:none;margin:4px 0;font-size:0.9rem;">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      <span>${filename}</span>
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
    </a>`
  }

  return defaultRender(tokens, idx, options, env, self)
}

export function useMarkdown() {
  function render(content: string): string {
    if (!content) return ''
    try {
      const obj = JSON.parse(content)
      return md.render(obj.text || content)
    } catch {
      return md.render(content)
    }
  }

  return { render }
}
