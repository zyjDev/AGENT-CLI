<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { renderMarkdown } from '@/utils/markdown'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string }>()

const html = computed(() => renderMarkdown(props.content))

/** 代码块复制走事件委托，避免为每个代码块单独挂监听 */
async function handleClick(event: MouseEvent): Promise<void> {
  const target = event.target as HTMLElement | null
  const button = target?.closest('[data-md-copy]')
  if (!button) return

  const code = button.closest('.md-pre')?.querySelector('code')?.textContent ?? ''
  if (!code) return

  try {
    await navigator.clipboard.writeText(code)
    message.success('代码已复制')
  } catch (error) {
    console.error('[markdown] 复制失败', error)
    message.error('复制失败，请手动选择代码')
  }
}
</script>

<template>
  <div class="md-body" @click="handleClick" v-html="html"></div>
</template>
