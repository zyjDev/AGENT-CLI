<script setup lang="ts">
/**
 * 全局唯一登录页。
 * 登录成功后默认落地用户端 /chat；若此前被守卫拦截，则回到 query.redirect 指定的原页。
 */
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import BrandLockup from '@/components/common/BrandLockup.vue'
import LoginForm from './LoginForm.vue'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const features = [
  {
    title: '拖拽式智能体编排',
    desc: '开始、智能体、客户端、模型、提示词、MCP 工具等节点，画布所见即所得',
    path: 'M12 3v6M12 15v6M5.5 7.5l3.5 3.5M15 13l3.5 3.5M18.5 7.5 15 11M9 13l-3.5 3.5',
    circle: true,
  },
  {
    title: '四阶段流式对话',
    desc: '分析、执行、监督、总结分步呈现，结果以 Markdown 实时渲染',
    path: 'M4 6h16M4 12h10M4 18h7',
    circle: false,
  },
  {
    title: '统一登录态',
    desc: '一次登录两端通用，登录态失效时明确提示并回到本页',
    path: 'M12 3 5 6.5v5c0 4.2 2.9 8 7 9.5 4.1-1.5 7-5.3 7-9.5v-5L12 3Z',
    circle: false,
  },
]

async function handleSuccess(): Promise<void> {
  message.success(`欢迎回来，${userStore.displayName}`)
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect ? route.query.redirect : '/chat'
  await router.replace(redirect)
}
</script>

<template>
  <div class="grid min-h-screen grid-cols-1 lg:grid-cols-[1.45fr_1fr]">
    <!-- 左侧品牌区 -->
    <section class="login-bg login-grid relative hidden flex-col justify-between overflow-hidden px-14 py-12 lg:flex">
      <div class="blob left-[-90px] top-[-60px] h-[320px] w-[320px] bg-brand"></div>
      <div class="blob bottom-[-80px] right-[-60px] h-[360px] w-[360px] bg-brand-violet" style="animation-delay: -6s"></div>

      <div class="relative">
        <BrandLockup tone="dark" :size="44" :radius="12" :title-size="17" />
      </div>

      <div class="relative max-w-[520px]">
        <p class="mb-4 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3 py-1 text-[12px] text-white/70">
          <span class="h-1.5 w-1.5 rounded-full bg-brand-violet"></span>统一入口 · 一套账号贯通两端
        </p>
        <h1 class="text-[40px] font-semibold leading-[1.22] text-white">
          智能体编排与<br />
          <span class="bg-gradient-to-r from-[#A5B4FC] to-[#D8B4FE] bg-clip-text text-transparent">智能对话一体化平台</span>
        </h1>
        <p class="mt-5 text-[14px] leading-7 text-white/60">
          私有知识库驱动，把智能体编排、知识问答与流式对话收进同一个工作台。<br />
          登录后默认进入智能对话，随时可无感切换到管理后台。
        </p>

        <ul class="mt-10 space-y-4">
          <li v-for="item in features" :key="item.title" class="flex items-start gap-3">
            <span class="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-white/10 ring-1 ring-white/15">
              <svg viewBox="0 0 24 24" class="h-4 w-4" fill="none" stroke="#C7D2FE" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path :d="item.path" />
                <circle v-if="item.circle" cx="12" cy="12" r="2.6" />
              </svg>
            </span>
            <div>
              <p class="text-[13.5px] font-medium text-white/90">{{ item.title }}</p>
              <p class="text-[12.5px] text-white/45">{{ item.desc }}</p>
            </div>
          </li>
        </ul>
      </div>

      <div class="relative flex items-center justify-between text-[11.5px] text-white/35">
        <span>v0.1.31 · 内部演示环境</span>
        <span>© 2026 智枢 · 智能体编排与知识问答平台</span>
      </div>
    </section>

    <!-- 右侧表单区 -->
    <section class="relative flex items-center justify-center bg-page px-6 py-12">
      <div class="pointer-events-none absolute left-1/2 top-0 h-[300px] w-[420px] -translate-x-1/2 rounded-full bg-brand/10 blur-[90px]"></div>

      <div class="glass-card relative w-full max-w-[404px] px-8 py-9">
        <div class="mb-7">
          <h2 class="text-[26px] font-semibold tracking-tight text-ink-900">欢迎登录</h2>
          <p class="mt-2 text-[13px] leading-6 text-ink-600">
            请输入账号和密码，登录后默认进入<span class="font-medium text-brand">智能对话</span>，可随时切换到管理后台。
          </p>
        </div>

        <LoginForm @success="handleSuccess" />
      </div>
    </section>
  </div>
</template>
