<script setup lang="ts">
/**
 * 查询条：按 FormField 描述生成内联查询表单。
 * 字段样式与交互全站统一，各模块只提供字段描述。
 */
import { reactive, watch } from 'vue'
import { Button, Input, InputNumber, Select } from 'ant-design-vue'
import { RotateCcw, Search } from 'lucide-vue-next'
import type { FormField } from './types'

const props = defineProps<{
  fields: FormField[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'search', values: Record<string, unknown>): void
  (e: 'reset'): void
}>()

// 用 any 承载表单值：antd 的 v-model 需要具体值类型，unknown 会导致 Select/InputNumber 赋值报错
const model = reactive<Record<string, any>>({})

function blankValues(): Record<string, unknown> {
  return props.fields.reduce<Record<string, unknown>>((acc, field) => {
    // select 用 undefined 以显示 placeholder；文本类用空串
    acc[field.name] = field.type === 'select' ? undefined : ''
    return acc
  }, {})
}

function syncBlank(): void {
  for (const key of Object.keys(model)) delete model[key]
  Object.assign(model, blankValues())
}

watch(() => props.fields, syncBlank, { immediate: true })

function handleSearch(): void {
  emit('search', { ...model })
}

function handleReset(): void {
  syncBlank()
  emit('reset')
}

/** 去掉空值，避免把 '' / undefined 传给后端影响 like 查询 */
function normalized(): Record<string, unknown> {
  return Object.fromEntries(
    Object.entries(model).filter(([, value]) => value !== '' && value !== undefined && value !== null),
  )
}

defineExpose({ normalized })
</script>

<template>
  <div class="card-panel flex flex-wrap items-end gap-4 px-5 py-4">
    <div v-for="field in fields" :key="field.name" class="flex flex-col">
      <label :for="`query-${field.name}`" class="mb-1.5 text-[12px] text-ink-400">{{ field.label }}</label>

      <Select
        v-if="field.type === 'select'"
        :id="`query-${field.name}`"
        v-model:value="model[field.name]"
        :options="field.options ?? []"
        :placeholder="field.placeholder ?? '全部'"
        :allow-clear="true"
        :style="{ width: `${field.width ?? 150}px` }"
      />

      <InputNumber
        v-else-if="field.type === 'number'"
        :id="`query-${field.name}`"
        v-model:value="model[field.name]"
        :placeholder="field.placeholder ?? '请输入'"
        :style="{ width: `${field.width ?? 150}px` }"
      />

      <Input
        v-else
        :id="`query-${field.name}`"
        v-model:value="model[field.name] as string"
        :placeholder="field.placeholder ?? '请输入'"
        :style="{ width: `${field.width ?? 190}px` }"
        allow-clear
        @press-enter="handleSearch"
      />
    </div>

    <div class="ml-auto flex items-center gap-2">
      <Button type="primary" class="btn-grad !h-9 px-4 text-[12.5px]" :loading="loading" @click="handleSearch">
        <Search :size="13" class="mr-1" />
        查询
      </Button>
      <Button class="!h-9 px-4 text-[12.5px]" @click="handleReset">
        <RotateCcw :size="13" class="mr-1" />
        重置
      </Button>
    </div>
  </div>
</template>
