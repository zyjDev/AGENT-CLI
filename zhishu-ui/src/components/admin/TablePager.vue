<script setup lang="ts">
/**
 * 列表分页条。
 *
 * 后端所有 query-list 统一返回「裸数组 + 内存分页」，响应里没有 total，
 * 因此这里不做「共 N 条 / 共 M 页」的伪装，只如实展示当前页条数，
 * 并用「本页是否取满」推断是否还有下一页。
 */
import { Button, Select } from 'ant-design-vue'
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    pageNum: number
    pageSize: number
    /** 当前页实际返回条数 */
    rowCount: number
    loading?: boolean
    pageSizeOptions?: number[]
  }>(),
  { loading: false, pageSizeOptions: () => [10, 20, 50] },
)

const emit = defineEmits<{
  (e: 'update:pageNum', value: number): void
  (e: 'update:pageSize', value: number): void
  (e: 'refresh'): void
}>()

function prev(): void {
  if (props.pageNum <= 1) return
  emit('update:pageNum', props.pageNum - 1)
}

function next(): void {
  emit('update:pageNum', props.pageNum + 1)
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-[#F1F3F9] px-5 py-3">
    <span class="text-[12px] text-ink-400">
      第 {{ pageNum }} 页 · 本页 {{ rowCount }} 条
      <span class="ml-1 text-ink-300">（后端 query-list 不返回总数，翻页以本页取满为准）</span>
    </span>

    <div class="flex items-center gap-2">
      <Button class="!h-7 !px-2 text-[12px]" :disabled="pageNum <= 1 || loading" @click="prev">
        <ChevronLeft :size="13" />
      </Button>
      <span class="brand-grad flex h-7 min-w-[28px] items-center justify-center rounded-md px-2 text-[12px] font-medium text-white">
        {{ pageNum }}
      </span>
      <Button class="!h-7 !px-2 text-[12px]" :disabled="rowCount < pageSize || loading" @click="next">
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
