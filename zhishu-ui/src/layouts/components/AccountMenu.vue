<script setup lang="ts">
/**
 * 账号菜单：展示当前账号、提供「修改密码」「两端无感切换」与退出登录。
 * 切换走的是同一份登录态（同一 token），因此不需要二次输入密码。
 */
import { computed, reactive, ref } from 'vue'
import { Dropdown, Form, Input, Menu, MenuDivider, MenuItem, Modal, message, type FormInstance } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, KeyRound, LayoutDashboard, LogOut, MessageSquare } from 'lucide-vue-next'
import { AdminUserApi } from '@/api/admin-user'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  /** 当前所在端，决定菜单里展示哪一个「切换」入口 */
  context: 'user' | 'admin'
}>()

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

/** 当前所处的端（用户端 / 管理端），只影响菜单里展示哪个切换入口 */
const inAdminContext = computed(() => props.context === 'admin')

/**
 * 账号角色（与后端 admin_user.user_role 一致）。
 * 权限差别：管理员可以修改「公共资源」，普通用户只能改自己的资源。
 */
const isRoleAdmin = computed(() => userStore.userRole === 'admin')

async function goChat(): Promise<void> {
  await router.push('/chat')
}

async function goAdmin(): Promise<void> {
  await router.push('/admin/dashboard')
}

async function handleLogout(): Promise<void> {
  userStore.logout()
  message.success('已退出登录')
  await router.replace('/login')
}

/* ---------------------------- 修改密码 ---------------------------- */

const pwdOpen = ref(false)
const pwdLoading = ref(false)
const pwdError = ref('')
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, message: '新密码至少 6 位' },
    {
      // 与原密码相同直接拦下：后端也会拒，但能在字段下提示更清楚
      validator: (_rule: unknown, value: string) =>
        value && value === pwdForm.oldPassword ? Promise.reject('新密码不能与原密码相同') : Promise.resolve(),
      trigger: 'change' as const,
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: (_rule: unknown, value: string) =>
        value && value !== pwdForm.newPassword ? Promise.reject('两次输入的新密码不一致') : Promise.resolve(),
      trigger: 'change' as const,
    },
  ],
}

function openPasswordModal(): void {
  pwdError.value = ''
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate?.()
  pwdOpen.value = true
}

async function submitPassword(): Promise<void> {
  pwdError.value = ''

  try {
    await pwdFormRef.value?.validate()
  } catch {
    // antd 已在字段下方内联提示；不打印异常对象（里面会带明文密码）
    return
  }

  pwdLoading.value = true
  try {
    await AdminUserApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
      confirmPassword: pwdForm.confirmPassword,
    })
    pwdOpen.value = false
    message.success('密码已修改，下次登录请使用新密码')
  } catch (error) {
    // 接口设了 silent，失败原因在这里内联展示（原密码错 / 新密码不合规）
    pwdError.value = error instanceof Error ? error.message : '修改失败，请稍后重试'
  } finally {
    pwdLoading.value = false
  }
}
</script>

<template>
  <Dropdown placement="bottomRight" :trigger="['click']">
    <button
      type="button"
      class="flex cursor-pointer items-center gap-2 rounded-lg px-1.5 py-1 transition hover:bg-[#F2F4FB]"
    >
      <span class="brand-grad flex h-7 w-7 items-center justify-center rounded-full text-[11.5px] font-medium text-white">
        {{ userStore.initials }}
      </span>
      <span class="text-[12.5px] text-ink-600">{{ userStore.displayName }}</span>
      <span
        class="rounded px-1.5 py-0.5 text-[10.5px]"
        :class="isRoleAdmin ? 'bg-[#EEF0FE] text-brand' : 'bg-[#F2F4FB] text-ink-400'"
      >
        {{ isRoleAdmin ? '管理员' : '普通用户' }}
      </span>
      <ChevronDown :size="13" class="text-ink-400" />
    </button>

    <template #overlay>
      <Menu class="min-w-[220px]">
        <MenuItem key="info" disabled>
          <span class="text-[12.5px] font-medium">{{ userStore.displayName }}</span>
          <span class="ml-2 text-[11px] text-ink-400">{{ inAdminContext ? '管理后台' : '智能对话' }}</span>
        </MenuItem>
        <MenuItem key="scope" disabled>
          <span class="text-[11px] text-ink-400">
            {{ isRoleAdmin ? '可维护公共资源与账号' : '只能修改自己创建的资源' }}
          </span>
        </MenuItem>
        <MenuDivider />
        <MenuItem v-if="!inAdminContext" key="to-admin" @click="goAdmin">
          <span class="flex items-center gap-2"><LayoutDashboard :size="14" />进入管理后台</span>
        </MenuItem>
        <MenuItem v-else key="to-chat" @click="goChat">
          <span class="flex items-center gap-2"><MessageSquare :size="14" />返回智能对话</span>
        </MenuItem>
        <MenuItem key="password" @click="openPasswordModal">
          <span class="flex items-center gap-2"><KeyRound :size="14" />修改密码</span>
        </MenuItem>
        <MenuItem key="logout" danger @click="handleLogout">
          <span class="flex items-center gap-2"><LogOut :size="14" />退出登录</span>
        </MenuItem>
      </Menu>
    </template>
  </Dropdown>

  <!-- 修改密码：身份由后端从 JWT 取，这里不传账号，避免"改到别人密码"的想象空间 -->
  <Modal
    v-model:open="pwdOpen"
    title="修改密码"
    :confirm-loading="pwdLoading"
    ok-text="确认修改"
    cancel-text="取消"
    ok-type="primary"
    @ok="submitPassword"
  >
    <Form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" layout="vertical" class="pt-1">
      <Form.Item label="原密码" name="oldPassword" class="!mb-3">
        <Input.Password v-model:value="pwdForm.oldPassword" placeholder="请输入当前密码" autocomplete="current-password" />
      </Form.Item>
      <Form.Item label="新密码" name="newPassword" class="!mb-3">
        <Input.Password v-model:value="pwdForm.newPassword" placeholder="至少 6 位" autocomplete="new-password" />
      </Form.Item>
      <Form.Item label="确认新密码" name="confirmPassword" class="!mb-2">
        <Input.Password v-model:value="pwdForm.confirmPassword" placeholder="请再次输入新密码" autocomplete="new-password" />
      </Form.Item>
    </Form>

    <p v-if="pwdError" class="rounded-lg bg-[#FEF4F4] px-3 py-2 text-[12.5px] text-err">{{ pwdError }}</p>
    <p class="mt-1 text-[11.5px] leading-5 text-ink-400">
      修改成功后当前登录仍然有效，下次登录请使用新密码。
    </p>
  </Modal>
</template>
