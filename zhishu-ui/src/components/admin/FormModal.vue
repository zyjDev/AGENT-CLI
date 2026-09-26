<script setup lang="ts">
/**
 * 新增 / 编辑弹窗：按 FormField 描述生成表单，字段校验与布局全站统一。
 */
import { reactive, ref, watch } from 'vue'
import { Form, FormItem, Input, InputNumber, Modal, Select } from 'ant-design-vue'
import type { FormField } from './types'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    fields: FormField[]
    initialValues?: Record<string, unknown>
    submitting?: boolean
    /** 是否为编辑态（用于 disabledOnEdit） */
    editing?: boolean
    width?: number
  }>(),
  { initialValues: () => ({}), submitting: false, editing: false, width: 580 },
)

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'submit', values: Record<string, unknown>): void
}>()

const formRef = ref()
// 同 QueryForm：表单值用 any，避免 antd 控件的 v-model 类型冲突
const model = reactive<Record<string, any>>({})

function buildValues(): Record<string, unknown> {
  // 先铺开初始值：其中可能含非表单字段的键（如新增时自动生成的 clientId），
  // 这些键必须一起提交，否则后端会因缺少业务主键返回错误。
  const values: Record<string, unknown> = { ...(props.initialValues ?? {}) }
  for (const field of props.fields) {
    const fromRecord = props.initialValues?.[field.name]
    if (fromRecord !== undefined && fromRecord !== null) {
      values[field.name] = fromRecord
    } else {
      values[field.name] = field.type === 'select' || field.type === 'number' ? undefined : ''
    }
  }
  return values
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    for (const key of Object.keys(model)) delete model[key]
    Object.assign(model, buildValues())
    // 打开时清掉上一次的校验残留
    formRef.value?.clearValidate?.()
  },
  { immediate: true },
)

/** 字段是否禁用：始终禁用 或 编辑态禁用 */
function isDisabled(field: FormField): boolean {
  return Boolean(field.disabled || (field.disabledOnEdit && props.editing))
}

function rulesOf(field: FormField) {
  if (!field.required) return undefined
  const action = field.type === 'select' ? '请选择' : '请输入'
  return [{ required: true, message: `${action}${field.label}` }]
}

function handleOk(): void {
  formRef.value
    ?.validate()
    .then(() => emit('submit', { ...model }))
    .catch((error: unknown) => {
      console.error('[form-modal] 校验未通过', error)
    })
}
</script>

<template>
  <Modal
    :open="open"
    :title="title"
    :width="width"
    :confirm-loading="submitting"
    ok-text="保存"
    cancel-text="取消"
    destroy-on-close
    @update:open="(value: boolean) => emit('update:open', value)"
    @ok="handleOk"
  >
    <Form ref="formRef" :model="model" layout="vertical" class="pt-2">
      <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
        <FormItem
          v-for="field in fields"
          :key="field.name"
          :label="field.label"
          :name="field.name"
          :rules="rulesOf(field)"
          :class="field.full ? 'sm:col-span-2' : ''"
          :extra="field.extra"
        >
          <Select
            v-if="field.type === 'select'"
            v-model:value="model[field.name]"
            :options="field.options ?? []"
            :placeholder="field.placeholder ?? `请选择${field.label}`"
            :disabled="isDisabled(field)"
          />
          <InputNumber
            v-else-if="field.type === 'number'"
            v-model:value="model[field.name]"
            class="w-full"
            :min="field.min"
            :max="field.max"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
          />
          <Input.TextArea
            v-else-if="field.type === 'textarea'"
            v-model:value="model[field.name] as string"
            :rows="field.rows ?? 4"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
          />
          <Input.Password
            v-else-if="field.type === 'password'"
            v-model:value="model[field.name] as string"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
          />
          <Input
            v-else
            v-model:value="model[field.name] as string"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            :disabled="isDisabled(field)"
          />
        </FormItem>
      </div>
    </Form>
  </Modal>
</template>
