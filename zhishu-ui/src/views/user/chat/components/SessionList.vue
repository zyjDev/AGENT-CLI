<script setup lang="ts">
/**
 * 左侧会话栏：新建 / 切换 / 删除 / 清空 + 当前会话 ID。
 * 会话数据由 useSessions 持久化在 localStorage（与旧静态页行为一致）。
 */
import { message, Popconfirm } from 'ant-design-vue'
import { Copy, Plus, Trash2, X } from 'lucide-vue-next'
import type { ChatSession } from '@/types/chat'

const props = defineProps<{
  sessions: ChatSession[]
  currentId: string
}>()

const emit = defineEmits<{
  (e: 'create'): void
  (e: 'select', id: string): void
  (e: 'remove', id: string): void
  (e: 'clear'): void
}>()

/** 友好时间：今天显示时分，昨天显示「昨天 HH:mm」，更早显示月-日 */
function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const time = date.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  const isSameDay = date.toDateString() === now.toDateString()
  if (isSameDay) return time

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${time}`

  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${time}`
}

function roundSummary(session: ChatSession): string {
  if (!session.rounds.length) return '暂无对话'
  return `${session.rounds.length} 轮对话`
}

async function copySessionId(): Promise<void> {
  if (!props.currentId) return
  try {
    await navigator.clipboard.writeText(props.currentId)
    message.success('会话 ID 已复制到剪贴板')
  } catch (error) {
    console.error('[chat] 复制会话 ID 失败', error)
    message.error('复制失败，请手动选择')
  }
}
</script>

<template>
  <aside class="flex w-60 shrink-0 flex-col overflow-hidden border-r border-line bg-white">
    <div class="space-y-2 border-b border-[#F1F3F9] p-3">
      <button type="button" class="btn-grad flex h-9 w-full cursor-pointer items-center justify-center gap-1.5 text-[13px]" @click="emit('create')">
        <Plus :size="14" />
        新建对话
      </button>

      <Popconfirm
        title="清空全部会话？"
        description="所有本地会话记录将被删除，且不可恢复。"
        ok-text="确认清空"
        cancel-text="取消"
        :disabled="!sessions.length"
        @confirm="emit('clear')"
      >
        <button
          type="button"
          class="btn-ghost flex h-8 w-full items-center justify-center gap-1.5 text-[12.5px] text-err hover:!border-[#F5C2C4] hover:!text-err disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="!sessions.length"
        >
          <Trash2 :size="13" />
          清空全部
        </button>
      </Popconfirm>
    </div>

    <div class="scroll-thin flex-1 space-y-1 overflow-y-auto p-2">
      <p v-if="!sessions.length" class="px-3 py-6 text-center text-[12px] leading-6 text-ink-400">
        还没有会话<br />点击上方「新建对话」开始
      </p>

      <div v-for="session in sessions" :key="session.id" class="group relative">
        <button
          type="button"
          class="w-full cursor-pointer rounded-lg px-3 py-2.5 text-left transition"
          :class="session.id === currentId ? 'bg-[#F4F5FE]' : 'hover:bg-[#F8F9FE]'"
          @click="emit('select', session.id)"
        >
          <span
            v-if="session.id === currentId"
            class="absolute left-0 top-2.5 h-8 w-[3px] rounded-r bg-gradient-to-b from-brand to-brand-violet"
          ></span>
          <p class="truncate pr-6 text-[12.5px]" :class="session.id === currentId ? 'font-medium text-ink-900' : 'text-ink-600'">
            {{ session.title }}
          </p>
          <p class="mt-1 flex items-center gap-1.5 text-[11px] text-ink-400">
            <span>{{ roundSummary(session) }}</span>
            <span>·</span>
            <span>{{ formatTime(session.updatedAt) }}</span>
          </p>
        </button>

        <Popconfirm
          title="删除该会话？"
          ok-text="删除"
          cancel-text="取消"
          @confirm="emit('remove', session.id)"
        >
          <button
            type="button"
            class="absolute right-2 top-2.5 hidden h-5 w-5 cursor-pointer items-center justify-center rounded text-ink-400 transition hover:bg-white hover:text-err group-hover:flex"
            title="删除会话"
          >
            <X :size="12" />
          </button>
        </Popconfirm>
      </div>
    </div>

    <div class="border-t border-[#F1F3F9] p-3">
      <p class="mb-1.5 text-[11px] text-ink-400">当前会话 ID</p>
      <div class="flex items-center gap-1.5 rounded-lg bg-page px-2.5 py-2">
        <span class="flex-1 truncate font-mono text-[10.5px] text-ink-600">{{ currentId || '—' }}</span>
        <button
          type="button"
          class="cursor-pointer text-ink-400 transition hover:text-brand disabled:cursor-not-allowed"
          :disabled="!currentId"
          title="复制会话 ID"
          @click="copySessionId"
        >
          <Copy :size="13" />
        </button>
      </div>
    </div>
  </aside>
</template>
