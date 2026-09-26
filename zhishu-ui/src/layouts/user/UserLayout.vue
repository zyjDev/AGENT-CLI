<script setup lang="ts">
/**
 * 用户端外壳：顶栏（品牌 + 进入管理端 + 账号菜单）+ 内容区。
 * 登录后默认落地在这里（/chat）。
 */
import { RouterView, useRouter } from 'vue-router'
import { LayoutDashboard } from 'lucide-vue-next'
import BrandLockup from '@/components/common/BrandLockup.vue'
import AccountMenu from '@/layouts/components/AccountMenu.vue'

const router = useRouter()

function goAdmin(): void {
  void router.push('/admin/dashboard')
}
</script>

<template>
  <div class="flex h-screen flex-col overflow-hidden bg-page">
    <header class="z-40 flex h-14 shrink-0 items-center justify-between border-b border-line bg-white px-5">
      <div class="flex items-center gap-3">
        <BrandLockup :size="32" :radius="9" :title-size="15" subtitle="" />
      </div>

      <div class="flex items-center gap-2.5">
        <span class="hidden items-center gap-1.5 rounded-full bg-[#F2F4FB] px-2.5 py-1 text-[11.5px] text-ink-600 md:flex">
          <span class="h-1.5 w-1.5 rounded-full bg-ok"></span>服务正常 · 8099
        </span>

        <button
          type="button"
          class="btn-grad flex h-8 cursor-pointer items-center gap-1.5 px-3.5 text-[12.5px]"
          title="同一账号，免二次登录"
          @click="goAdmin"
        >
          <LayoutDashboard :size="13" />
          进入管理后台
        </button>

        <AccountMenu context="user" />
      </div>
    </header>

    <main class="min-h-0 flex-1">
      <RouterView />
    </main>
  </div>
</template>
