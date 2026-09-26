<script setup lang="ts">
/**
 * 品牌标识：六边形轮廓 + 内部三节点连线，象征「编排 + 调度中枢」。
 * 图标按尺寸自适应：小尺寸下把内部三角放大一档，避免节点糊成一团。
 */
withDefaults(
  defineProps<{
    /** 图标外框边长（px） */
    size?: number
    /** 外框圆角（px） */
    radius?: number
    /** 是否只渲染图形、不带渐变底 */
    plain?: boolean
  }>(),
  { size: 32, radius: 9, plain: false },
)
</script>

<template>
  <div
    class="flex shrink-0 items-center justify-center"
    :class="plain ? '' : 'brand-grad'"
    :style="{ width: `${size}px`, height: `${size}px`, borderRadius: `${radius}px` }"
  >
    <svg viewBox="0 0 24 24" fill="none" :style="{ width: `${size * 0.72}px`, height: `${size * 0.72}px` }">
      <polygon
        points="12,2.6 20.1,7.3 20.1,16.7 12,21.4 3.9,16.7 3.9,7.3"
        :stroke="plain ? 'currentColor' : '#ffffff'"
        stroke-width="1.6"
        stroke-linejoin="round"
      />
      <path
        d="M12 7.6 7.9 15.2M12 7.6l4.1 7.6M7.9 15.2h8.2"
        :stroke="plain ? 'currentColor' : '#ffffff'"
        stroke-width="1.5"
        stroke-linecap="round"
      />
      <circle cx="12" cy="7.6" r="1.5" :fill="plain ? 'currentColor' : '#ffffff'" />
      <circle cx="7.9" cy="15.2" r="1.5" :fill="plain ? 'currentColor' : '#ffffff'" />
      <circle cx="16.1" cy="15.2" r="1.5" :fill="plain ? 'currentColor' : '#ffffff'" />
    </svg>
  </div>
</template>
