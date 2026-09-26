<script setup lang="ts">
/**
 * 知识库文件上传：POST /v1/admin/ai-client-rag-order/file/upload（multipart：name / tag / files）。
 */
import { reactive, ref } from 'vue'
import { Button, Form, Input, message } from 'ant-design-vue'
import { Upload } from 'lucide-vue-next'
import { AiClientRagOrderApi, RAG_ALLOWED_EXTENSIONS, RAG_MAX_FILE_SIZE_MB } from '@/api/ai-client-rag-order'

const emit = defineEmits<{ (e: 'uploaded'): void }>()

const formRef = ref()
const form = reactive({ name: '', tag: '' })
const files = ref<File[]>([])
const uploading = ref(false)
const inputKey = ref(0)

const rules = {
  name: [
    { required: true, message: '请输入知识库名称' },
    { min: 2, max: 50, message: '知识库名称长度需在 2-50 之间' },
  ],
  tag: [
    { required: true, message: '请输入知识标签' },
    { min: 2, max: 30, message: '知识标签长度需在 2-30 之间' },
  ],
}

function handleFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files ?? [])

  const unsupported = selected.find(
    (file) => !RAG_ALLOWED_EXTENSIONS.some((ext) => file.name.toLowerCase().endsWith(ext)),
  )
  if (unsupported) {
    message.error(`不支持的文件类型：${unsupported.name}（仅支持 ${RAG_ALLOWED_EXTENSIONS.join(' / ')}）`)
    input.value = ''
    return
  }

  const oversize = selected.find((file) => file.size > RAG_MAX_FILE_SIZE_MB * 1024 * 1024)
  if (oversize) {
    message.error(`文件「${oversize.name}」超过 ${RAG_MAX_FILE_SIZE_MB}MB 上限`)
    input.value = ''
    return
  }

  files.value = selected
}

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch (error) {
    console.error('[rag-upload] 表单校验未通过', error)
    return
  }

  if (!files.value.length) {
    message.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  try {
    await AiClientRagOrderApi.uploadFiles(form.name, form.tag, files.value)
    message.success('上传成功，知识库正在后台解析')
    form.name = ''
    form.tag = ''
    files.value = []
    inputKey.value += 1
    emit('uploaded')
  } catch (error) {
    // 失败原因已由请求层提示
    console.error('[rag-upload] 上传失败', error)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="card-panel p-5">
    <div class="flex items-center gap-2">
      <h2 class="text-[14px] font-medium">上传知识库文件</h2>
      <span class="rounded-full bg-[#E4F6EA] px-2 py-0.5 text-[11px] text-[#15803D]">支持增量更新</span>
    </div>
    <p class="mt-1.5 text-[12px] leading-6 text-ink-400">
      支持 {{ RAG_ALLOWED_EXTENSIONS.join(' / ') }}，单文件不超过 {{ RAG_MAX_FILE_SIZE_MB }}MB；上传后按「知识标签」建立索引，供 RagAnswer 顾问召回。
    </p>

    <Form ref="formRef" :model="form" :rules="rules" layout="vertical" class="mt-4">
      <div class="flex flex-wrap items-end gap-4">
        <Form.Item label="知识库名称" name="name" class="!mb-0">
          <Input v-model:value="form.name" placeholder="如：Spring 性能调优手册" class="w-[240px]" />
        </Form.Item>

        <Form.Item label="知识标签" name="tag" class="!mb-0">
          <Input v-model:value="form.tag" placeholder="如：spring_perf" class="w-[200px]" />
        </Form.Item>

        <Form.Item label="文件" class="!mb-0">
          <input
            :key="inputKey"
            type="file"
            multiple
            :accept="RAG_ALLOWED_EXTENSIONS.join(',')"
            class="h-8 w-[300px] cursor-pointer rounded-lg border border-line bg-white text-[12.5px] leading-8 file:mr-3 file:h-8 file:cursor-pointer file:rounded-l-lg file:border-0 file:bg-[#F2F4FB] file:px-3 file:text-[12.5px] file:text-ink-600"
            @change="handleFiles"
          />
        </Form.Item>

        <Button type="primary" class="btn-grad !h-9 px-4 text-[12.5px]" :loading="uploading" @click="submit">
          <Upload :size="13" class="mr-1" />
          开始上传
        </Button>
      </div>

      <p v-if="files.length" class="mt-3 text-[11.5px] text-ink-600">
        已选择 {{ files.length }} 个文件：{{ files.map((file) => file.name).join('、') }}
      </p>
    </Form>
  </div>
</template>
