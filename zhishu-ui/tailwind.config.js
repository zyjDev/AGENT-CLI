import animate from 'tailwindcss-animate'

/**
 * 设计令牌与原型 prototype/ 完全对齐：
 * 主色 靛蓝 #4F46E5 → 紫罗兰 #7C3AED，页面底 #F7F8FC，正文 #1F2430
 */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"PingFang SC"', '"HarmonyOS Sans SC"', '"Microsoft YaHei"', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', '"JetBrains Mono"', 'Consolas', 'monospace'],
      },
      colors: {
        brand: {
          DEFAULT: '#4F46E5',
          400: '#818CF8',
          500: '#6366F1',
          600: '#4F46E5',
          700: '#4338CA',
          violet: '#7C3AED',
        },
        ink: {
          900: '#1F2430',
          600: '#5A6474',
          400: '#8C94A3',
          300: '#B4BAC7',
        },
        page: '#F7F8FC',
        line: '#E6E9F2',
        ok: '#16A34A',
        warn: '#F59E0B',
        err: '#E5484D',
        info: '#2563EB',
      },
      keyframes: {
        floatBlob: {
          '0%, 100%': { transform: 'translate3d(0,0,0) scale(1)' },
          '50%': { transform: 'translate3d(18px,-22px,0) scale(1.08)' },
        },
        fadeUp: {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        pulseDot: {
          '0%, 100%': { opacity: '.25', transform: 'scale(.85)' },
          '50%': { opacity: '1', transform: 'scale(1)' },
        },
      },
      animation: {
        blob: 'floatBlob 14s ease-in-out infinite',
        'fade-up': 'fadeUp .32s ease both',
        'pulse-dot': 'pulseDot 1.1s ease-in-out infinite',
      },
    },
  },
  plugins: [animate],
}
