/**
 * Markdown 渲染链路：marked（解析）→ highlight.js（代码高亮）→ DOMPurify（净化）。
 * 与旧静态页保持一致，但把「代码块 + 复制按钮」结构化输出，便于样式与交互统一。
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'

const HTML_ESCAPES: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

const escapeHtml = (text: string): string => text.replace(/[&<>"']/g, (char) => HTML_ESCAPES[char] ?? char)

const renderer = new marked.Renderer()

/** 代码块：带语言标签与复制按钮（复制按钮通过 data 属性被事件委托识别） */
renderer.code = (token) => {
  const language = (token.lang ?? '').trim()
  const highlighted =
    language && hljs.getLanguage(language)
      ? hljs.highlight(token.text, { language }).value
      : hljs.highlightAuto(token.text).value

  return [
    '<div class="md-pre">',
    '<div class="md-pre-bar">',
    `<span>${escapeHtml(language || 'text')}</span>`,
    '<button type="button" data-md-copy>复制</button>',
    '</div>',
    `<pre><code class="hljs">${highlighted}</code></pre>`,
    '</div>',
  ].join('')
}

marked.setOptions({ renderer, gfm: true, breaks: true })

/**
 * DOMPurify 默认不允许 button：这里显式放行我们这个不含用户数据的复制按钮。
 * 代码正文仍会被完整净化，安全性不受影响。
 */
const PURIFY_CONFIG = { ADD_TAGS: ['button'], ADD_ATTR: ['data-md-copy', 'type'] }

/** Markdown 原文 → 可直接 v-html 的安全 HTML */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  const html = marked.parse(source) as string
  return DOMPurify.sanitize(html, PURIFY_CONFIG)
}
