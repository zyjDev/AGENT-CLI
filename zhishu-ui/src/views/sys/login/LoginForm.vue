<script setup lang="ts">
/**
 * 登录表单控件。
 * 只负责收集账号密码并调用 store，跳转逻辑交给父级。
 */
import { reactive, ref } from 'vue'
import { Button, Checkbox, Form, Input, type FormInstance } from 'ant-design-vue'
import { useUserStore } from '@/store/modules/user'

const emit = defineEmits<{ (e: 'success'): void }>()

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorText = ref('')

const formState = reactive({
  username: '',
  password: '',
  remember: true,
})

const rules = {
  username: [
    { required: true, message: '请输入账号' },
    { min: 3, message: '账号至少 3 个字符' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, message: '密码至少 6 个字符' },
  ],
}

/** 提交登录（快捷登录也走这里，先回填再提交） */
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
    await userStore.login({ username: formState.username, password: formState.password })
    emit('success')
  } catch (error) {
    // 登录接口已设 silent，失败原因统一在这里以内联文案呈现
    errorText.value = '账号或密码错误，请重试'
    console.error('[login] 登录失败', error)
  } finally {
    loading.value = false
  }
}

/** 快捷登录：演示账号 admin / 123456 */
function quickLogin(): void {
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
      <Input.Password v-model:value="formState.password" size="large" placeholder="请输入密码" @press-enter="submit">
        <template #prefix>
          <svg viewBox="0 0 24 24" class="h-4 w-4 text-ink-400" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <rect x="4.5" y="10" width="15" height="10" rx="2.2" />
            <path d="M8 10V7.5a4 4 0 0 1 8 0V10" />
          </svg>
        </template>
      </Input.Password>
    </Form.Item>

    <div class="mb-4 flex items-center justify-between">
      <Checkbox v-model:checked="formState.remember" class="text-[12.5px] text-ink-600">记住登录状态</Checkbox>
      <span class="cursor-pointer text-[12.5px] text-brand hover:underline">忘记密码？</span>
    </div>

    <p v-if="errorText" class="mb-3 rounded-lg bg-[#FEF4F4] px-3 py-2 text-[12.5px] text-err">{{ errorText }}</p>

    <Button type="primary" size="large" block class="btn-grad !h-11 text-[14px]" :loading="loading" html-type="submit">
      立即登录
    </Button>
  </Form>

  <div class="my-6 flex items-center gap-3 text-[11.5px] text-ink-400">
    <span class="h-px flex-1 bg-line"></span>或<span class="h-px flex-1 bg-line"></span>
  </div>

  <Button block class="!h-10 !rounded-lg text-[13px]" :disabled="loading" @click="quickLogin">使用 admin 账号快捷登录</Button>

  <div class="mt-5 rounded-lg bg-page px-3.5 py-2.5 text-[11.5px] leading-5 text-ink-400">
    演示账号：<span class="font-mono text-ink-600">admin</span> / 密码 <span class="font-mono text-ink-600">123456</span>。
    登录后同一账号即可访问智能对话与管理后台，无需二次输入密码。
  </div>

  <p class="mt-5 text-center text-[11px] text-ink-400">登录即表示同意《服务条款》与《隐私政策》</p>
</template>
