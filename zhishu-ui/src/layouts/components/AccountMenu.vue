<script setup lang="ts">
/**
 * 账号菜单：展示当前账号、提供「两端无感切换」入口与退出登录。
 * 切换走的是同一份登录态（同一 token），因此不需要二次输入密码。
 */
import { computed } from 'vue'
import { Dropdown, Menu, MenuDivider, MenuItem, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronDown, LayoutDashboard, LogOut, MessageSquare } from 'lucide-vue-next'
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
        <MenuItem key="logout" danger @click="handleLogout">
          <span class="flex items-center gap-2"><LogOut :size="14" />退出登录</span>
        </MenuItem>
      </Menu>
    </template>
  </Dropdown>
</template>
