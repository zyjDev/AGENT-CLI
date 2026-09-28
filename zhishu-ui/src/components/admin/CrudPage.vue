<script setup lang="ts">
/**
 * 列表页引擎：查询条 + 操作区 + 表格 + 分页 + 新增/编辑弹窗 + 删除二次确认。
 * 10 个资源模块共用本组件，只通过 descriptor 描述差异（字段、列、接口）。
 *
 * 分页说明：后端 query-list 正从「裸数组 + 内存分页」迁到数据库物理分页。
 * 本组件用 normalizePagePayload 同时吃下两种形态：
 * 拿到 total 就展示真实总数，拿不到（旧裸数组接口）才退回「本页是否取满」推断，不做总数伪装。
 */
import { computed, onMounted, ref } from 'vue'
import { Button, message, Popconfirm, Table, Tag, type TableColumnType } from 'ant-design-vue'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import FormModal from './FormModal.vue'
import QueryForm from './QueryForm.vue'
import TablePager from './TablePager.vue'
import type { CellRender, CrudDescriptor, RowAction } from './types'
import { normalizePagePayload } from '@/types/api'

type Row = Record<string, any>

const props = withDefaults(
  defineProps<{
    descriptor: CrudDescriptor
    /** 除通用列外是否允许编辑（部分模块只读） */
    allowEdit?: boolean
  }>(),
  { allowEdit: true },
)

const rows = ref<Row[]>([])
/** 符合条件的总条数；null = 后端没给（旧裸数组接口），此时不显示「共 N 条」 */
const total = ref<number | null>(null)
const loading = ref(false)
const queryValues = ref<Record<string, unknown>>({})
const pageNum = ref(1)
const pageSize = ref(10)

const modalOpen = ref(false)
const modalEditing = ref(false)
const submitting = ref(false)
const currentRecord = ref<Row | null>(null)
const modalInitial = ref<Row>({})

const canCreate = computed(() => !props.descriptor.readonly && Boolean(props.descriptor.api.create))
const canEdit = computed(() => props.allowEdit && !props.descriptor.readonly && Boolean(props.descriptor.api.update))
const canRemove = computed(() => !props.descriptor.readonly && Boolean(props.descriptor.api.remove))

/** 该行可见的行操作：rowActions 可用 visible 按行隐藏（如平台默认资源不给「修改」） */
function visibleRowActions(record: Row): RowAction[] {
  return (props.descriptor.rowActions ?? []).filter((action) => action.visible?.(record) ?? true)
}

/** 该行能否删除：模块级允许 + 描述符按行判定（列表里混着只读的平台默认资源时用） */
function canRemoveRow(record: Row): boolean {
  if (!canRemove.value) return false
  return props.descriptor.canRemoveRow ? props.descriptor.canRemoveRow(record) : true
}
const showActionColumn = computed(
  () => canEdit.value || canRemove.value || Boolean(props.descriptor.rowActions?.length),
)

/**
 * 操作列宽度：按实际按钮个数算。
 *
 * 原来固定 170px，只够「编辑 + 删除」两个；像客户端 API 那种带一个行操作
 * （绑定智能体 + 编辑 + 删除）就会挤成两三行，看起来很脏。
 * 每个操作按「图标 + 最多 5 个汉字」预留 76px，另留 28px 左右内边距。
 */
const actionColumnWidth = computed(() => {
  const actionCount =
    (props.descriptor.rowActions?.length ?? 0) + (canEdit.value ? 1 : 0) + (canRemove.value ? 1 : 0)
  return 28 + Math.max(actionCount, 1) * 76
})

const tableColumns = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = props.descriptor.columns.map((column) => ({
    title: column.title,
    dataIndex: column.dataIndex,
    key: column.dataIndex,
    width: column.width,
    ellipsis: column.render === 'truncate',
  }))
  if (showActionColumn.value) {
    // 钉在右侧：列多时横向滚动不会把「编辑 / 删除」挤出可视区
    columns.push({
      title: '操作',
      dataIndex: '__actions',
      key: '__actions',
      width: actionColumnWidth.value,
      ellipsis: false,
      fixed: 'right' as const,
    })
  }
  return columns
})

/**
 * 表格最小宽度。
 * 用固定数值而不是 'max-content'：后者会让「描述」这类自适应列按内容无限撑宽，
 * 把右侧「操作」列挤出可视区。给数值后 antd 走 table-layout: fixed，
 * 自适应列只在剩余空间内伸缩，必要时才出现横向滚动条。
 */
const tableMinWidth = computed(() => {
  const fixed = props.descriptor.columns.reduce((sum, column) => sum + (column.width ?? 200), 0)
  return fixed + (showActionColumn.value ? actionColumnWidth.value : 0)
})

const renderKindMap = computed<Record<string, CellRender>>(() =>
  props.descriptor.columns.reduce<Record<string, CellRender>>((acc, column) => {
    acc[column.dataIndex] = column.render ?? 'text'
    return acc
  }, {}),
)

/** 业务 ID 用 8 位随机数字生成，与旧实现保持一致的观感 */
/**
 * 生成与后端同格式的雪花 ID 字符串（19 位纯数字）。
 * 后端 MyBatis-Plus IdWorker 的起始纪元是 2010-11-04，这里保持一致，产出同一量级的值。
 * ⚠️ 必须用 BigInt 拼接：19 位数字远超 Number.MAX_SAFE_INTEGER（16 位），
 *    用 number 运算会丢精度、生成重复 ID，插入时直接撞唯一索引。
 */
const SNOWFLAKE_EPOCH = 1288834974657n
let snowflakeSequence = 0n

function generateBusinessId(): string {
  const timestamp = BigInt(Date.now()) - SNOWFLAKE_EPOCH
  snowflakeSequence = (snowflakeSequence + 1n) % 4096n
  // 41 位时间戳 + 10 位机器位 + 12 位序列，与雪花算法布局一致
  return ((timestamp << 22n) | (1n << 12n) | snowflakeSequence).toString()
}

function formatTime(value: unknown): string {
  if (!value) return '—'
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await props.descriptor.api.query({
      ...queryValues.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    // 裸数组 / { list, total } 两种形态都能吃：后端迁移期间新旧接口并存
    const page = normalizePagePayload<Row>(result)
    rows.value = page.list
    total.value = page.total
  } catch (error) {
    // 具体原因（网络 / 401）由请求层统一提示，这里只保证表格回到可用状态
    console.error(`[crud] 加载「${props.descriptor.title}」失败`, error)
    rows.value = []
    total.value = null
  } finally {
    loading.value = false
  }
}

function handleSearch(values: Record<string, unknown>): void {
  queryValues.value = values
  pageNum.value = 1
  void load()
}

function handleReset(): void {
  queryValues.value = {}
  pageNum.value = 1
  void load()
}

function handlePageNum(value: number): void {
  pageNum.value = value
  void load()
}

function handlePageSize(value: number): void {
  pageSize.value = value
  pageNum.value = 1
  void load()
}

function openCreate(): void {
  const initial: Row = {}
  const idField = props.descriptor.generateIdField
  if (idField) initial[idField] = generateBusinessId()
  if (props.descriptor.formFields.some((field) => field.name === 'status')) initial.status = 1
  modalInitial.value = initial
  currentRecord.value = null
  modalEditing.value = false
  modalOpen.value = true
}

function openEdit(record: Row): void {
  modalInitial.value = { ...record }
  currentRecord.value = record
  modalEditing.value = true
  modalOpen.value = true
}

async function handleSubmit(values: Row): Promise<void> {
  const api = props.descriptor.api
  submitting.value = true
  try {
    if (modalEditing.value) {
      if (!api.update) return
      // 统一以主键 id 更新（后端 update-by-id）
      await api.update({ ...values, id: currentRecord.value?.id })
    } else {
      if (!api.create) return
      await api.create(values)
    }
    message.success(modalEditing.value ? '更新成功' : '创建成功')
    modalOpen.value = false
    await load()
  } catch (error) {
    console.error(`[crud] 保存「${props.descriptor.title}」失败`, error)
  } finally {
    submitting.value = false
  }
}

async function handleRemove(record: Row): Promise<void> {
  if (!props.descriptor.api.remove) return
  try {
    await props.descriptor.api.remove(record)
    message.success('删除成功')
    if (rows.value.length === 1 && pageNum.value > 1) {
      pageNum.value -= 1
    }
    await load()
  } catch (error) {
    console.error(`[crud] 删除「${props.descriptor.title}」失败`, error)
  }
}

onMounted(load)

defineExpose({ reload: load })
</script>

<template>
  <div class="space-y-4">
    <QueryForm :fields="descriptor.queryFields" :loading="loading" @search="handleSearch" @reset="handleReset" />

    <div class="card-panel">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-[#F1F3F9] px-5 py-3.5">
        <div class="flex items-center gap-2.5">
          <h2 class="text-[14px] font-medium">{{ descriptor.title }}</h2>
          <span class="rounded-full bg-[#EEF0FE] px-2 py-0.5 text-[11px] text-brand">
            {{ total === null ? `本页 ${rows.length} 条` : `共 ${total} 条` }}
          </span>
        </div>

        <div class="flex items-center gap-2">
          <Button
            v-if="canCreate"
            type="primary"
            class="btn-grad !h-8 shrink-0 whitespace-nowrap text-[12.5px]"
            @click="openCreate"
          >
            <Plus :size="13" class="mr-1" />
            新增{{ descriptor.title }}
          </Button>
        </div>
      </div>

      <p v-if="descriptor.notice" class="border-b border-[#F1F3F9] bg-[#FCFCFE] px-5 py-2.5 text-[11.5px] leading-5 text-ink-400">
        {{ descriptor.notice }}
      </p>

      <Table
        :columns="tableColumns"
        :data-source="rows"
        :loading="loading"
        :row-key="descriptor.rowKey"
        :pagination="false"
        size="middle"
        :scroll="{ x: tableMinWidth }"
        class="admin-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === '__actions'">
            <div class="flex items-center justify-end gap-3 whitespace-nowrap">
              <button
                v-for="action in visibleRowActions(record as Row)"
                :key="action.label"
                type="button"
                class="shrink-0 cursor-pointer whitespace-nowrap text-[12.5px] text-brand hover:underline"
                @click="action.run(record as Row)"
              >
                {{ action.label }}
              </button>

              <button
                v-if="canEdit"
                type="button"
                class="flex shrink-0 cursor-pointer items-center gap-1 whitespace-nowrap text-[12.5px] text-brand hover:underline"
                @click="openEdit(record as Row)"
              >
                <Pencil :size="12" />编辑
              </button>

              <Popconfirm
                v-if="canRemoveRow(record as Row)"
                title="确认删除该记录？"
                description="删除后不可恢复，且可能影响已引用它的编排节点。"
                ok-text="确认删除"
                cancel-text="取消"
                @confirm="handleRemove(record as Row)"
              >
                <button
                  type="button"
                  class="flex shrink-0 cursor-pointer items-center gap-1 whitespace-nowrap text-[12.5px] text-err hover:underline"
                >
                  <Trash2 :size="12" />删除
                </button>
              </Popconfirm>
            </div>
          </template>

          <template v-else-if="renderKindMap[column.key as string] === 'status'">
            <Tag :color="record[column.key as string] === 1 ? 'success' : 'default'">
              {{ record[column.key as string] === 1 ? '启用' : '停用' }}
            </Tag>
          </template>

          <!-- 来源列：平台默认（owner 为空）人人可见但只有管理员能改，普通用户的行标「我的」 -->
          <template v-else-if="renderKindMap[column.key as string] === 'platform-default'">
            <Tag v-if="record[column.key as string]" color="blue">平台默认</Tag>
            <span v-else class="text-[12px] text-ink-400">我的</span>
          </template>

          <template v-else-if="renderKindMap[column.key as string] === 'time'">
            <span class="font-mono text-[12px] text-ink-400">{{ formatTime(record[column.key as string]) }}</span>
          </template>

          <!--
            mono 列放的都是机器串（雪花 ID、URL 路径）：
            19 位 ID 在窄列里会被折成两三行，很难看。这里改成单行 + 省略号，
            完整值挂在 title 上，鼠标悬停仍可看全。
          -->
          <template v-else-if="renderKindMap[column.key as string] === 'mono'">
            <span
              class="block truncate font-mono text-[12.5px] text-ink-600"
              :title="String(record[column.key as string] ?? '')"
            >
              {{ record[column.key as string] ?? '—' }}
            </span>
          </template>

          <template v-else>
            <span :title="String(record[column.key as string] ?? '')">{{ record[column.key as string] ?? '—' }}</span>
          </template>
        </template>

        <template #emptyText>
          <div class="py-10 text-center text-[12.5px] text-ink-400">
            暂无数据，可调整查询条件或点击「新增{{ descriptor.title }}」
          </div>
        </template>
      </Table>

      <TablePager
        :page-num="pageNum"
        :page-size="pageSize"
        :row-count="rows.length"
        :total="total"
        :loading="loading"
        @update:page-num="handlePageNum"
        @update:page-size="handlePageSize"
        @refresh="load"
      />
    </div>

    <FormModal
      v-model:open="modalOpen"
      :title="modalEditing ? `编辑${descriptor.title}` : `新增${descriptor.title}`"
      :fields="descriptor.formFields"
      :initial-values="modalInitial"
      :editing="modalEditing"
      :submitting="submitting"
      @submit="handleSubmit"
    />
  </div>
</template>
