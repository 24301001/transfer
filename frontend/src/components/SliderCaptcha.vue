<template>
  <div class="slider-captcha" :class="{ 'is-verified': verified, 'is-failed': failed }">
    <!-- 拼图区域 -->
    <div class="captcha-puzzle" ref="puzzleRef" :style="puzzleStyle">
      <!-- 底部背景图 -->
      <div class="bg-layer" v-if="challenge">
        <img :src="challenge.backgroundImageBase64" :width="challenge.imageWidth" :height="challenge.imageHeight" alt="" />
      </div>
      <!-- 缺口标记 -->
      <div class="gap-mask" v-if="challenge" :style="gapStyle"></div>
      <!-- 拼图滑块 -->
      <div
        class="puzzle-piece"
        v-if="challenge"
        :style="pieceStyle"
        ref="pieceRef"
      >
        <img
          :src="challenge.puzzleImageBase64"
          :width="challenge.pieceWidth"
          :height="challenge.pieceWidth"
          alt=""
          draggable="false"
        />
      </div>
      <!-- 验证状态图标 -->
      <div class="status-overlay" v-if="verified">
        <div class="status-icon success">✓</div>
      </div>
      <div class="status-overlay" v-if="failed">
        <div class="status-icon fail">✗</div>
      </div>
    </div>

    <!-- 滑块轨道 -->
    <div class="slider-track" ref="trackRef" @mousedown.prevent="startDrag" @touchstart.prevent="startDrag">
      <div class="track-bg"></div>
      <div class="track-fill" :style="{ width: trackFillWidth }"></div>
      <div
        class="slider-btn"
        :class="{ dragging: isDragging }"
        :style="{ left: sliderLeft + 'px' }"
        ref="btnRef"
      >
        <span class="btn-icon" v-if="!verified && !failed">{{ isDragging ? '►' : '→' }}</span>
        <span class="btn-icon" v-else-if="verified">✓</span>
        <span class="btn-icon" v-else>✗</span>
      </div>
      <span class="track-hint" v-if="!isDragging && !verified && !failed">
        {{ loading ? '加载中...' : '拖动滑块完成拼图' }}
      </span>
      <span class="track-hint" v-if="verified">验证通过</span>
      <span class="track-hint" v-if="failed">验证失败，点击重试</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getSliderCaptchaChallenge, verifySliderCaptcha } from '@/services/modules/user'

const emit = defineEmits(['verified', 'error'])

// ====== 状态 ======
const challenge = ref(null)
const loading = ref(false)
const verified = ref(false)
const failed = ref(false)

const isDragging = ref(false)
const sliderLeft = ref(0)
const trackWidth = ref(280)

const puzzleRef = ref(null)
const pieceRef = ref(null)
const trackRef = ref(null)
const btnRef = ref(null)

// ====== 样式计算 ======
const PIECE_BORDER = 6

const puzzleStyle = computed(() => {
  if (!challenge.value) return {}
  return {
    width: challenge.value.imageWidth + 'px',
    height: challenge.value.imageHeight + 'px',
  }
})

const gapStyle = computed(() => {
  if (!challenge.value) return {}
  // 缺口在背景图上的位置：puzzleY 是垂直位置，水平位置在 targetX
  // 我们用滑块当前距离来表示缺口的水平位置
  // 注意：缺口位置就是 targetX，但 targetX 储存在服务端，前端不知道
  // 我们在背景上不画缺口标记（防作弊），只保留 puzzleY 的信息
  return {
    top: challenge.value.puzzleY + 'px',
    height: challenge.value.pieceWidth + 'px',
  }
})

// 拼图碎片的最终正确位置（未知，但拖动到正确位置后端会验证）
const pieceStyle = computed(() => {
  if (!challenge.value) return {}
  return {
    left: sliderLeft.value + 'px',
    top: challenge.value.puzzleY + 'px',
    width: challenge.value.pieceWidth + 'px',
    height: challenge.value.pieceWidth + 'px',
  }
})

const trackFillWidth = computed(() => {
  const max = maxSlideX.value
  if (max <= 0) return '0px'
  return (sliderLeft.value / max) * 100 + '%'
})

const maxSlideX = computed(() => {
  if (!challenge.value) return 0
  return challenge.value.imageWidth - challenge.value.pieceWidth - PIECE_BORDER
})

// ====== 加载挑战 ======
async function loadChallenge() {
  loading.value = true
  failed.value = false
  verified.value = false
  sliderLeft.value = 0
  try {
    const data = await getSliderCaptchaChallenge()
    challenge.value = data
    // 更新轨道宽度匹配图片宽度
    trackWidth.value = data.imageWidth
  } catch {
    emit('error', '获取滑块验证码失败')
  } finally {
    loading.value = false
  }
}

// ====== 拖拽逻辑 ======
let startX = 0
let startLeft = 0

function getClientX(e) {
  return e.touches ? e.touches[0].clientX : e.clientX
}

function startDrag(e) {
  if (verified.value || loading.value) return
  isDragging.value = true
  failed.value = false
  startX = getClientX(e)
  startLeft = sliderLeft.value

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('touchmove', onDrag, { passive: true })
  document.addEventListener('touchend', endDrag)
}

function onDrag(e) {
  if (!isDragging.value) return
  const clientX = getClientX(e)
  const delta = clientX - startX
  const max = maxSlideX.value
  sliderLeft.value = Math.max(0, Math.min(max, startLeft + delta))
}

async function endDrag() {
  if (!isDragging.value) return
  isDragging.value = false

  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)

  // 验证滑块位置
  await doVerify()
}

async function doVerify() {
  if (!challenge.value) return
  loading.value = true
  try {
    const result = await verifySliderCaptcha({
      captchaId: challenge.value.captchaId,
      sliderX: sliderLeft.value,
    })
    verified.value = true
    emit('verified', result.sliderToken)
  } catch {
    failed.value = true
    // 延迟后自动重置
    setTimeout(() => {
      sliderLeft.value = 0
      failed.value = false
      loadChallenge()
    }, 1200)
  } finally {
    loading.value = false
  }
}

// 点击滑块轨道重置（失败状态）
onMounted(() => {
  loadChallenge()
})

// 暴露重置方法
function reset() {
  loadChallenge()
}

defineExpose({ reset, verified: computed(() => verified.value) })

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)
})
</script>

<style scoped>
.slider-captcha {
  user-select: none;
  -webkit-user-select: none;
}

.captcha-puzzle {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background: #f0f2f5;
  margin-bottom: 0;
}

.bg-layer {
  display: block;
}

.bg-layer img {
  display: block;
  max-width: 100%;
  height: auto;
}

.gap-mask {
  position: absolute;
  left: 0;
  width: 100%;
  pointer-events: none;
}

.puzzle-piece {
  position: absolute;
  cursor: grab;
  z-index: 2;
  transition: none;
}

.puzzle-piece img {
  display: block;
  pointer-events: none;
  max-width: none;
}

.slider-captcha.is-dragging .puzzle-piece {
  cursor: grabbing;
}

.status-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
  z-index: 5;
}

.status-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  animation: pop 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.status-icon.success {
  background: #22c55e;
  color: #fff;
}

.status-icon.fail {
  background: #ef4444;
  color: #fff;
}

@keyframes pop {
  from { transform: scale(0); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

/* 滑块轨道 */
.slider-track {
  position: relative;
  height: 42px;
  background: #f0f2f5;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.track-bg {
  position: absolute;
  inset: 0;
}

.track-fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.08), rgba(59, 130, 246, 0.18));
  border-radius: 8px;
  transition: width 0.1s;
}

.slider-btn {
  position: absolute;
  top: -1px;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  z-index: 3;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
  transition: box-shadow 0.2s, transform 0.2s;
  margin-left: -1px;
}

.slider-btn:hover {
  box-shadow: 0 3px 12px rgba(59, 130, 246, 0.4);
}

.slider-btn.dragging {
  cursor: grabbing;
  box-shadow: 0 3px 14px rgba(59, 130, 246, 0.5);
  transform: scale(1.04);
}

.btn-icon {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.track-hint {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #94a3b8;
  pointer-events: none;
  letter-spacing: 0.02em;
}

/* 验证成功/失败状态 */
.slider-captcha.is-verified .slider-btn {
  background: linear-gradient(135deg, #22c55e, #16a34a);
  box-shadow: 0 2px 6px rgba(34, 197, 94, 0.3);
}

.slider-captcha.is-verified .track-fill {
  background: linear-gradient(90deg, rgba(34, 197, 94, 0.1), rgba(34, 197, 94, 0.2));
}

.slider-captcha.is-failed .slider-btn {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  box-shadow: 0 2px 6px rgba(239, 68, 68, 0.3);
}
</style>
