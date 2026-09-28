<script setup lang="ts">
/**
 * 列表分页条。
 *
 * 后端 query-list 正从「裸数组 + 内存分页」迁到数据库物理分页（返回 total）：
 * - 知道 total：如实展示「共 N 条 / 第 X / Y 页」，下一页按总页数判断；
 * - 不知道 total（旧裸数组接口）：退回展示本页条数，用「本页是否取满」推断还有没有下一页，
 *   绝不伪造总数。
 */
import { computed } from 'vue'
import { Button, Select } from 'ant-design-vue'
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    pageNum: number
    pageSize: number
    /** 当前页实际返回条数（后端没给 total 时用来推断是否还有下一页） */
    rowCount: number
    /** 总条数；null 表示后端未返回（旧裸数组接口） */
    total?: number | null
    loading?: boolean
    pageSizeOptions?: number[]
  }>(),
  { loading: false, total: null, pageSizeOptions: () => [10, 20, 50] },
)

const emit = defineEmits<{
  (e: 'update:pageNum', value: number): void
  (e: 'update:pageSize', value: number): void
  (e: 'refresh'): void
}>()

const totalPages = computed(() => Math.max(1, Math.ceil((props.total ?? 0) / props.pageSize)))

/** 下一页是否可用：知道总数就按总页数判断，否则退回「本页取满则可能还有」 */
const hasNext = computed(() =>
  props.total === null ? props.rowCount >= props.pageSize : props.pageNum < totalPages.value,
)

const summary = computed(() =>
  props.total === null
    ? `第 ${props.pageNum} 页 · 本页 ${props.rowCount} 条（后端未返回总数，翻页以本页取满为准）`
    : `共 ${props.total} 条 · 第 ${props.pageNum} / ${totalPages.value} 页 · 本页 ${props.rowCount} 条`,
)

function prev(): void {
  if (props.pageNum <= 1) return
  emit('update:pageNum', props.pageNum - 1)
}

function next(): void {
  if (!hasNext.value) return
  emit('update:pageNum', props.pageNum + 1)
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-[#F1F3F9] px-5 py-3">
    <span class="text-[12px] text-ink-400">{{ summary }}</span>

    <div class="flex items-center gap-2">
      <Button class="!h-7 !px-2 text-[12px]" :disabled="pageNum <= 1 || loading" @click="prev">
        <ChevronLeft :size="13" />
      </Button>
      <span class="brand-grad flex h-7 min-w-[28px] items-center justify-center rounded-md px-2 text-[12px] font-medium text-white">
        {{ pageNum }}
      </span>
      <Button class="!h-7 !px-2 text-[12px]" :disabled="!hasNext || loading" @click="next">
        <ChevronRight :size="13" />
      </Button>

      <Select
        :value="pageSize"
        :options="pageSizeOptions.map((v) => ({ value: v, label: `${v} 条/页` }))"
        size="small"
        class="ml-2 w-[98px]"
        @change="(value: unknown) => emit('update:pageSize', Number(value))"
      />

      <Button class="!h-7 !px-2.5 text-[12px]" :loading="loading" @click="emit('refresh')">
        <RefreshCw :size="13" class="mr-1" />
        刷新
      </Button>
    </div>
  </div>
</template>
