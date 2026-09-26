<script setup lang="ts">
/**
 * 登录 / 注册表单控件（同一入口切换，只有一处登录态来源）。
 * 只负责收集账号密码并调用 store，跳转逻辑交给父级。
 *
 * 注册即登录：后端 /register 直接签发 token，因此成功后走的是同一条 emit('success') 路径，
 * 用户不会被要求"注册完再登录一次"。
 */
import { computed, reactive, ref } from 'vue'
import { Button, Checkbox, Form, Input, type FormInstance } from 'ant-design-vue'
import { useUserStore } from '@/store/modules/user'

const emit = defineEmits<{ (e: 'success'): void }>()

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorText = ref('')
const mode = ref<'login' | 'register'>('login')

const isRegister = computed(() => mode.value === 'register')

const formState = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  remember: true,
})

/** 两次密码一致性校验：注册场景最容易输错的就是这里 */
const validateConfirmPassword = async (_rule: unknown, value: string): Promise<void> => {
  if (!isRegister.value) {
    return
  }
  if (!value) {
    return Promise.reject(new Error('请再次输入密码'))
  }
  if (value !== formState.password) {
    return Promise.reject(new Error('两次输入的密码不一致'))
  }
  return Promise.resolve()
}

const rules = {
  username: [
    { required: true, message: '请输入账号' },
    { min: 3, message: '账号至少 3 个字符' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, message: '密码至少 6 个字符' },
  ],
  // trigger 用字面量（不加 as const 会被推断成 string，不符合 antd 的 RuleObject 类型）
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'change' as const }],
}

/** 切换登录 / 注册：清掉上一种模式的报错，避免误导 */
function switchMode(next: 'login' | 'register'): void {
  errorText.value = ''
  mode.value = next
  formState.confirmPassword = ''
  formRef.value?.clearValidate?.()
}

/** 提交（快捷登录也走这里，先回填再提交） */
async function submit(): Promise<void> {
  errorText.value = ''

  try {
    await formRef.value?.validate()
  } catch (error) {
    // 校验失败：antd 已在字段下方内联提示，这里只记录不打扰用户
    console.error('[login] 表单校验未通过', error)
    return
  }

  loading.value = true
  try {
    if (isRegister.value) {
      await userStore.register({
        username: formState.username,
        password: formState.password,
        confirmPassword: formState.confirmPassword,
      })
    } else {
      await userStore.login({ username: formState.username, password: formState.password })
    }
    emit('success')
  } catch (error) {
    errorText.value = isRegister.value ? '注册失败：账号可能已被占用，或密码不符合要求' : '账号或密码错误，请重试'
    console.error('[login] 提交失败', error)
  } finally {
    loading.value = false
  }
}

/** 快捷登录：演示账号 admin / 123456 */
function quickLogin(): void {
  switchMode('login')
  formState.username = 'admin'
  formState.password = '123456'
  void submit()
}

defineExpose({ submit })
</script>

<template>
  <Form ref="formRef" :model="formState" :rules="rules" layout="vertical" @finish="submit">
    <Form.Item label="账号" name="username" class="!mb-4">
      <Input v-model:value="formState.username" size="large" placeholder="请输入账号" allow-clear>
        <template #prefix>
          <svg viewBox="0 0 24 24" class="h-4 w-4 text-ink-400" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <circle cx="12" cy="8" r="3.4" />
            <path d="M5 20c.8-3.5 3.6-5.4 7-5.4S18.2 16.5 19 20" />
          </svg>
        </template>
      </Input>
    </Form.Item>

    <Form.Item label="密码" name="password" class="!mb-3">
      <Input.Password
        v-model:value="formState.password"
        size="large"
        placeholder="请输入密码"
        @press-enter="submit"
      >
        <template #prefix>
          <svg viewBox="0 0 24 24" class="h-4 w-4 text-ink-400" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <rect x="4.5" y="10" width="15" height="10" rx="2.2" />
            <path d="M8 10V7.5a4 4 0 0 1 8 0V10" />
          </svg>
        </template>
      </Input.Password>
    </Form.Item>

    <Form.Item v-if="isRegister" label="确认密码" name="confirmPassword" class="!mb-3">
      <Input.Password
        v-model:value="formState.confirmPassword"
        size="large"
        placeholder="请再次输入密码"
        @press-enter="submit"
      >
        <template #prefix>
          <svg viewBox="0 0 24 24" class="h-4 w-4 text-ink-400" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <rect x="4.5" y="10" width="15" height="10" rx="2.2" />
            <path d="M9.6 14.2l1.6 1.6 3-3.2" />
          </svg>
        </template>
      </Input.Password>
    </Form.Item>

    <div class="mb-4 flex items-center justify-between">
      <Checkbox v-model:checked="formState.remember" class="text-[12.5px] text-ink-600">记住登录状态</Checkbox>
      <span
        class="cursor-pointer text-[12.5px] text-brand hover:underline"
        @click="switchMode(isRegister ? 'login' : 'register')"
      >
        {{ isRegister ? '已有账号？去登录' : '没有账号？注册' }}
      </span>
    </div>

    <p v-if="errorText" class="mb-3 rounded-lg bg-[#FEF4F4] px-3 py-2 text-[12.5px] text-err">{{ errorText }}</p>

    <Button type="primary" size="large" block class="btn-grad !h-11 text-[14px]" :loading="loading" html-type="submit">
      {{ isRegister ? '注册并进入' : '立即登录' }}
    </Button>
  </Form>

  <div class="my-6 flex items-center gap-3 text-[11.5px] text-ink-400">
    <span class="h-px flex-1 bg-line"></span>或<span class="h-px flex-1 bg-line"></span>
  </div>

  <Button block class="!h-10 !rounded-lg text-[13px]" :disabled="loading" @click="quickLogin">使用 admin 账号快捷登录</Button>

  <div class="mt-5 rounded-lg bg-page px-3.5 py-2.5 text-[11.5px] leading-5 text-ink-400">
    <template v-if="isRegister">
      注册后即刻登录，账号仅用于你自己：<span class="text-ink-600">智能体、客户端 / API Key、模型、知识库</span>
      都按账号隔离，彼此不可见。
    </template>
    <template v-else>
      演示账号：<span class="font-mono text-ink-600">admin</span> / 密码 <span class="font-mono text-ink-600">123456</span>。
      登录后同一账号即可访问智能对话与管理后台，无需二次输入密码。
    </template>
  </div>

  <p class="mt-5 text-center text-[11px] text-ink-400">登录即表示同意《服务条款》与《隐私政策》</p>
</template>
