<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

dayjs.locale('zh-cn')

/** antd 主题令牌：与设计规范（原型）保持一致，保证原生组件与自定义样式同色系 */
const theme = {
  token: {
    colorPrimary: '#4F46E5',
    colorInfo: '#2563EB',
    colorSuccess: '#16A34A',
    colorWarning: '#F59E0B',
    colorError: '#E5484D',
    colorTextBase: '#1F2430',
    colorBorderSecondary: '#E6E9F2',
    borderRadius: 8,
    fontFamily: '"PingFang SC", "HarmonyOS Sans SC", "Microsoft YaHei", Inter, system-ui, sans-serif',
  },
}
// 组件级样式（表格表头、布局底色等）统一用 Tailwind 类控制，避免与 antd 令牌版本差异打架
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="theme">
    <RouterView />
  </ConfigProvider>
</template>
