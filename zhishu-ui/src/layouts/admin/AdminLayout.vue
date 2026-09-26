<script setup lang="ts">
/**
 * 管理端外壳：侧边分组导航 + 顶栏（面包屑 / 返回智能对话 / 账号菜单）+ 内容区。
 * 由用户端导航进入，复用同一登录态，不需要二次输入密码。
 */
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import BrandLockup from '@/components/common/BrandLockup.vue'
import AccountMenu from '@/layouts/components/AccountMenu.vue'
import { ADMIN_MENU, ADMIN_TITLE_MAP } from './menu'

const route = useRoute()
const router = useRouter()

const currentTitle = computed(() => ADMIN_TITLE_MAP[route.path] ?? '管理后台')

/** 编排页需要整屏画布，隐藏侧边与内边距 */
const isFullBleed = computed(() => route.path === '/admin/agent-config')

function goChat(): void {
  void router.push('/chat')
}
</script>

<template>
  <div class="flex h-screen overflow-hidden bg-page">
    <!-- 侧边导航 -->
    <aside v-if="!isFullBleed" class="flex w-60 shrink-0 flex-col border-r border-line bg-white">
      <div class="flex h-14 items-center border-b border-[#F1F3F9] px-4">
        <BrandLockup :size="32" :radius="9" :title-size="14" subtitle="管理后台" />
      </div>

      <nav class="scroll-thin flex-1 overflow-y-auto py-1">
        <template v-for="group in ADMIN_MENU" :key="group.label">
          <p class="side-group">{{ group.label }}</p>
          <div class="space-y-0.5 px-2">
            <RouterLink
              v-for="item in group.items"
              :key="item.path"
              :to="item.path"
              class="side-link"
              :class="{ active: route.path === item.path }"
            >
              <component :is="item.icon" :size="16" />
              {{ item.title }}
            </RouterLink>
          </div>
        </template>
      </nav>

      <div class="border-t border-[#F1F3F9] p-3">
        <button type="button" class="btn-ghost flex h-9 w-full cursor-pointer items-center justify-center gap-1.5 text-[12.5px]" @click="goChat">
          <ArrowLeft :size="14" />
          返回智能对话
        </button>
      </div>
    </aside>

    <!-- 主区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-line bg-white px-5">
        <div class="flex items-center gap-2 text-[12.5px]">
          <span class="text-ink-400">智枢</span>
          <span class="text-[#D9DEF0]">/</span>
          <span class="font-medium text-ink-900">{{ currentTitle }}</span>
        </div>

        <div class="flex items-center gap-2.5">
          <button type="button" class="btn-ghost flex h-8 cursor-pointer items-center gap-1.5 px-3 text-[12.5px]" @click="goChat">
            <ArrowLeft :size="13" />
            返回智能对话
          </button>
          <AccountMenu context="admin" />
        </div>
      </header>

      <main class="scroll-thin min-h-0 flex-1 overflow-y-auto" :class="isFullBleed ? '' : 'p-5'">
        <RouterView />
      </main>
    </div>
  </div>
</template>
