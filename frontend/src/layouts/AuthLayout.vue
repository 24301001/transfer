<template>
  <div class="auth-layout" ref="layoutRef" @mousemove="onMouseMove" @mouseleave="onMouseLeave">
    <!-- 呼吸背景层 -->
    <div class="bg-layer">
      <div class="bg-base"></div>
      <div class="bg-breath"></div>
      <div class="bg-glow" :style="glowStyle"></div>
    </div>

    <!-- 粒子画布（团簇粒子引擎） -->
    <canvas ref="canvasRef" class="particle-canvas"></canvas>

    <!-- 微几何体 -->
    <div class="geo-micron">
      <div
        v-for="(g, i) in microGeometry"
        :key="i"
        class="geo-dot"
        :style="{
          width: g.s + 'px', height: g.s + 'px',
          left: g.x + '%', top: g.y + '%',
          opacity: g.o,
          animationDelay: (i * 0.3) + 's',
          animationDuration: (4 + g.s * 0.05) + 's',
        }"
      ></div>
    </div>

    <!-- 主内容 -->
    <div class="auth-container">
      <div class="auth-brand">
        <!-- 品牌图标 -->
        <div class="brand-icon" ref="iconRef">
          <div class="icon-ring"></div>
          <svg width="34" height="34" viewBox="0 0 36 36" fill="none">
            <circle cx="18" cy="18" r="2" fill="#3b82f6" opacity="0.9"/>
            <circle cx="9" cy="10" r="1.4" fill="#6366f1" opacity="0.6"/>
            <circle cx="27" cy="10" r="1.4" fill="#6366f1" opacity="0.6"/>
            <circle cx="9" cy="26" r="1.4" fill="#6366f1" opacity="0.6"/>
            <circle cx="27" cy="26" r="1.4" fill="#6366f1" opacity="0.6"/>
            <line x1="10.5" y1="11.5" x2="16" y2="16.5" stroke="#93c5fd" stroke-width="0.6" opacity="0.5"/>
            <line x1="25.5" y1="11.5" x2="20" y2="16.5" stroke="#93c5fd" stroke-width="0.6" opacity="0.5"/>
            <line x1="10.5" y1="24.5" x2="16" y2="19.5" stroke="#93c5fd" stroke-width="0.6" opacity="0.5"/>
            <line x1="25.5" y1="24.5" x2="20" y2="19.5" stroke="#93c5fd" stroke-width="0.6" opacity="0.5"/>
          </svg>
        </div>

        <!-- 标题 -->
        <h1 class="brand-title" ref="titleRef">
          交通事故智能识别与调度系统<br />
         
        </h1>
        <p class="brand-sub" ref="subRef">Traffic Accident Risk Assessment & Prediction Platform</p>

        <!-- 特性指标 -->
        <div class="brand-indicators" ref="indicatorsRef">
          <div class="indicator" v-for="(item, i) in indicators" :key="i">
            <span class="ind-dot" :style="{ background: item.color }"></span>
            <span class="ind-text">{{ item.label }}</span>
          </div>
        </div>

        
      </div>

      <!-- 右侧登录卡片 -->
      <div class="auth-card-wrap" ref="cardWrapRef">
        <div class="card-glow" ref="cardGlowRef"></div>
        <div class="auth-card" ref="cardRef">
          <div class="card-rim"></div>
          <div class="card-accent-bar"></div>
          <router-view />
        </div>
        <div class="card-shim"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'

// =============================================
// 1. 鼠标追踪
// =============================================
const mouse = reactive({ x: -9999, y: -9999, vx: 0, vy: 0 })
let prevMouse = { x: -9999, y: -9999 }

const layoutRef = ref(null)

function onMouseMove(e) {
  const rect = layoutRef.value.getBoundingClientRect()
  prevMouse = { x: mouse.x, y: mouse.y }
  mouse.x = ((e.clientX - rect.left) / rect.width) * 100
  mouse.y = ((e.clientY - rect.top) / rect.height) * 100
  mouse.vx = mouse.x - prevMouse.x
  mouse.vy = mouse.y - prevMouse.y
}

function onMouseLeave() {
  mouse.x = -9999
  mouse.y = -9999
  mouse.vx = 0
  mouse.vy = 0
}

// =============================================
// 2. 呼吸光晕跟随
// =============================================
const glowStyle = ref({
  background: 'radial-gradient(ellipse at 50% 50%, transparent 0%, transparent 60%)',
})

function updateGlow() {
  if (mouse.x < 0) return
  const cx = mouse.x
  const cy = mouse.y
  glowStyle.value = {
    background: `
      radial-gradient(ellipse at ${cx}% ${cy}%, rgba(59,130,246,0.08) 0%, transparent 50%),
      radial-gradient(ellipse at ${Math.min(100, cx + 15)}% ${Math.max(0, cy - 10)}%, rgba(99,102,241,0.04) 0%, transparent 45%)
    `,
  }
}

// =============================================
// 3. Canvas 团簇粒子物理引擎（Google Antigravity 风格）
// =============================================
const canvasRef = ref(null)
let ctx = null
let particles = []
let animId = null
let canvasW = 0
let canvasH = 0

const PARTICLE_COUNT = 120
const CLUSTER_ATTRACT = 0.0005
const CLUSTER_LINK_DIST = 180
const CLUSTER_LINK_OPACITY = 0.12
const INFLUENCE_RADIUS = 220
const REPULSE_RADIUS = 35
const DAMPING = 0.93
const SPRING = 0.0004
const MAX_SPEED = 2.0
const SEED_COUNT = 5

class Particle {
  constructor(isSeed = false) {
    this.isSeed = isSeed
    this.reset()
  }

  reset() {
    this.x = Math.random() * canvasW
    this.y = Math.random() * canvasH
    this.ox = this.x
    this.oy = this.y
    this.vx = (Math.random() - 0.5) * 0.3
    this.vy = (Math.random() - 0.5) * 0.3

    if (this.isSeed) {
      this.s = 3 + Math.random() * 2
      this.baseA = 0.12 + Math.random() * 0.08
      this.mass = 3.0 + Math.random() * 1.0
      this.type = 0
    } else {
      this.s = 1 + Math.random() * 2
      this.baseA = 0.04 + Math.random() * 0.06
      this.mass = 0.3 + Math.random() * 0.7
      this.type = Math.random() < 0.5 ? 0 : (Math.random() < 0.6 ? 1 : 2)
    }

    this.alpha = this.baseA
    this.drift = (Math.random() - 0.5) * 0.03
    this.phase = Math.random() * Math.PI * 2
  }

  update(mx, my, allParticles) {
    this.vx += this.drift * 0.04
    this.vy += this.drift * 0.04

    const breath = Math.sin(Date.now() * 0.0006 + this.phase) * 0.002
    this.vx += breath * (this.isSeed ? 0.5 : 1)
    this.vy += breath * (this.isSeed ? 0.3 : 0.6)

    for (const other of allParticles) {
      if (other === this) continue
      const dx = this.x - other.x
      const dy = this.y - other.y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist > 0 && dist < 250) {
        const force = CLUSTER_ATTRACT * other.mass * (1 - dist / 250)
        this.vx -= dx * force
        this.vy -= dy * force
      }
    }

    if (mx > -9999) {
      const dx = this.x - mx
      const dy = this.y - my
      const dist = Math.sqrt(dx * dx + dy * dy)

      if (dist < INFLUENCE_RADIUS && dist > 0) {
        const gravStrength = (1 - dist / INFLUENCE_RADIUS) * 0.03
        this.vx -= dx * gravStrength
        this.vy -= dy * gravStrength

        if (dist < REPULSE_RADIUS) {
          const repStrength = (1 - dist / REPULSE_RADIUS) * 0.1
          this.vx += dx * repStrength
          this.vy += dy * repStrength
        }

        if (mouse.vx && mouse.vy) {
          this.vx += mouse.vx * 0.004
          this.vy += mouse.vy * 0.004
        }
      }
    }

    this.vx += (this.ox - this.x) * SPRING * (this.isSeed ? 0.3 : 1)
    this.vy += (this.oy - this.y) * SPRING * (this.isSeed ? 0.3 : 1)

    this.vx *= DAMPING
    this.vy *= DAMPING

    const speed = Math.sqrt(this.vx * this.vx + this.vy * this.vy)
    if (speed > MAX_SPEED) {
      this.vx = (this.vx / speed) * MAX_SPEED
      this.vy = (this.vy / speed) * MAX_SPEED
    }

    this.x += this.vx
    this.y += this.vy

    const margin = 30
    if (this.x < -margin) { this.x = canvasW + margin; this.ox = this.x }
    if (this.x > canvasW + margin) { this.x = -margin; this.ox = this.x }
    if (this.y < -margin) { this.y = canvasH + margin; this.oy = this.y }
    if (this.y > canvasH + margin) { this.y = -margin; this.oy = this.y }

    const d = Math.sqrt((this.x - this.ox) ** 2 + (this.y - this.oy) ** 2)
    this.alpha = Math.max(0.005, Math.min(this.baseA, this.baseA - d * 0.0008))
  }

  draw() {
    if (!ctx) return
    ctx.save()
    ctx.globalAlpha = this.alpha
    ctx.translate(this.x, this.y)

    if (this.type === 0) {
      ctx.fillStyle = this.isSeed ? 'rgba(99, 102, 241, 0.8)' : 'rgba(59, 130, 246, 0.7)'
      ctx.beginPath()
      ctx.arc(0, 0, this.s * 0.5, 0, Math.PI * 2)
      ctx.fill()
    } else if (this.type === 1) {
      ctx.strokeStyle = 'rgba(59, 130, 246, 0.35)'
      ctx.lineWidth = 0.6
      ctx.beginPath()
      ctx.arc(0, 0, this.s * 0.7, 0, Math.PI * 2)
      ctx.stroke()
    } else {
      ctx.strokeStyle = 'rgba(99, 102, 241, 0.3)'
      ctx.lineWidth = 0.5
      const h = this.s * 0.5
      ctx.beginPath()
      ctx.moveTo(-h, 0); ctx.lineTo(h, 0)
      ctx.moveTo(0, -h); ctx.lineTo(0, h)
      ctx.stroke()
    }

    ctx.restore()
  }
}

function drawClusterLinks(allParticles) {
  if (!ctx) return
  ctx.save()
  for (let i = 0; i < allParticles.length; i++) {
    for (let j = i + 1; j < allParticles.length; j++) {
      const a = allParticles[i]
      const b = allParticles[j]
      const dx = a.x - b.x
      const dy = a.y - b.y
      const dist = Math.sqrt(dx * dx + dy * dy)

      if (dist < CLUSTER_LINK_DIST) {
        const opacity = CLUSTER_LINK_OPACITY * (1 - dist / CLUSTER_LINK_DIST)
        if (opacity < 0.001) continue
        ctx.globalAlpha = opacity * Math.min(a.alpha / a.baseA, b.alpha / b.baseA)
        ctx.strokeStyle = 'rgba(59, 130, 246, 0.5)'
        ctx.lineWidth = 0.4
        ctx.beginPath()
        ctx.moveTo(a.x, a.y)
        ctx.lineTo(b.x, b.y)
        ctx.stroke()
      }
    }
  }
  ctx.restore()
}

function initCanvas() {
  if (!canvasRef.value || !layoutRef.value) return
  ctx = canvasRef.value.getContext('2d')
  resizeCanvas()
  const seeds = Array.from({ length: SEED_COUNT }, () => new Particle(true))
  const normals = Array.from({ length: PARTICLE_COUNT - SEED_COUNT }, () => new Particle(false))
  particles = [...seeds, ...normals]
  animateParticles()
}

function resizeCanvas() {
  if (!canvasRef.value || !layoutRef.value) return
  const rect = layoutRef.value.getBoundingClientRect()
  canvasW = canvasRef.value.width = rect.width * window.devicePixelRatio
  canvasH = canvasRef.value.height = rect.height * window.devicePixelRatio
  canvasRef.value.style.width = rect.width + 'px'
  canvasRef.value.style.height = rect.height + 'px'
  if (ctx) ctx.scale(window.devicePixelRatio, window.devicePixelRatio)
  canvasW = rect.width
  canvasH = rect.height
  if (particles.length) {
    particles.forEach(p => { p.ox = Math.random() * canvasW; p.oy = Math.random() * canvasH; p.x = p.ox; p.y = p.oy })
  }
}

function animateParticles() {
  if (!ctx) return
  ctx.clearRect(0, 0, canvasW, canvasH)

  const mx = mouse.x < 0 ? -9999 : (mouse.x / 100) * canvasW
  const my = mouse.y < 0 ? -9999 : (mouse.y / 100) * canvasH

  drawClusterLinks(particles)

  for (const p of particles) {
    p.update(mx, my, particles)
    p.draw()
  }

  updateGlow()
  animId = requestAnimationFrame(animateParticles)
}

// =============================================
// 4. DOM 磁力交互
// =============================================
const iconRef = ref(null)
const titleRef = ref(null)
const subRef = ref(null)
const indicatorsRef = ref(null)
const cardWrapRef = ref(null)
const cardGlowRef = ref(null)
const cardRef = ref(null)

function applyMagnetic() {
  if (mouse.x < 0) {
    document.querySelectorAll('.magnetic-target').forEach(el => {
      if (!el._origTransform && !el._isReset) return
      el._isReset = true
      el.style.transition = 'transform 0.8s cubic-bezier(0.23, 1, 0.32, 1)'
      el.style.transform = 'translate(0, 0)'
    })
    return
  }

  const mX = mouse.x
  const mY = mouse.y

  if (iconRef.value) {
    const rect = iconRef.value.getBoundingClientRect()
    const cx = ((rect.left + rect.width / 2) / window.innerWidth) * 100
    const cy = ((rect.top + rect.height / 2) / window.innerHeight) * 100
    iconRef.value.style.transform = `translate(${(mX - cx) * 0.06}px, ${(mY - cy) * 0.06}px)`
    iconRef.value._isReset = false
  }

  if (titleRef.value) {
    const rect = titleRef.value.getBoundingClientRect()
    const cx = ((rect.left + rect.width / 2) / window.innerWidth) * 100
    const cy = ((rect.top + rect.height / 2) / window.innerHeight) * 100
    titleRef.value.style.transform = `translate(${(mX - cx) * 0.04}px, ${(mY - cy) * 0.04}px)`
    titleRef.value._isReset = false
  }

  if (subRef.value) {
    const rect = subRef.value.getBoundingClientRect()
    const cx = ((rect.left + rect.width / 2) / window.innerWidth) * 100
    const cy = ((rect.top + rect.height / 2) / window.innerHeight) * 100
    subRef.value.style.transform = `translate(${(mX - cx) * 0.03}px, ${(mY - cy) * 0.03}px)`
    subRef.value._isReset = false
  }

  if (indicatorsRef.value) {
    const items = indicatorsRef.value.querySelectorAll('.indicator')
    items.forEach(el => {
      const rect = el.getBoundingClientRect()
      const cx = ((rect.left + rect.width / 2) / window.innerWidth) * 100
      const cy = ((rect.top + rect.height / 2) / window.innerHeight) * 100
      const dx = cx - mX
      const dy = cy - mY
      const dist = Math.sqrt(dx * dx + dy * dy)

      if (dist < 12 && dist > 0) {
        const angle = Math.atan2(dy, dx)
        const push = (12 - dist) * 0.15
        el.style.transform = `translate(${Math.cos(angle) * push}px, ${Math.sin(angle) * push}px)`
        el.style.transition = 'transform 0.2s ease-out'
      } else {
        el.style.transform = ''
        el.style.transition = 'transform 0.6s cubic-bezier(0.23, 1, 0.32, 1)'
      }
      el._isReset = false
    })
  }

  if (cardGlowRef.value && cardWrapRef.value) {
    const rect = cardWrapRef.value.getBoundingClientRect()
    const cw = (rect.left / window.innerWidth) * 100
    const ch = (rect.top / window.innerHeight) * 100
    const dx = mX - (cw + 20)
    const dy = mY - (ch + 15)
    const dist = Math.sqrt(dx * dx + dy * dy)

    if (dist < 25) {
      const angle = Math.atan2(dy, dx) * (180 / Math.PI)
      cardGlowRef.value.style.background = `
        linear-gradient(${angle}deg,
          rgba(59,130,246,0.15) 0%,
          rgba(99,102,241,0.06) 40%,
          transparent 70%
        )
      `
    }
  }
}

// =============================================
// 5. 微几何体数据
// =============================================
const microGeometry = Array.from({ length: 18 }, () => ({
  x: Math.random() * 100,
  y: Math.random() * 100,
  s: 2 + Math.random() * 4,
  o: 0.03 + Math.random() * 0.06,
}))

// =============================================
// 6. 特色指标
// =============================================
const indicators = [
  { label: 'AI 智能事故预估', color: '#3b82f6' },
  { label: '实时路况融合分析', color: '#6366f1' },
  { label: '多角色协同调度', color: '#06b6d4' },
]

// =============================================
// 7. 生命周期
// =============================================
let magneticRaf = null

function magneticLoop() {
  applyMagnetic()
  magneticRaf = requestAnimationFrame(magneticLoop)
}

onMounted(async () => {
  await nextTick()
  initCanvas()
  magneticLoop()
  window.addEventListener('resize', resizeCanvas)
})

onBeforeUnmount(() => {
  if (animId) cancelAnimationFrame(animId)
  if (magneticRaf) cancelAnimationFrame(magneticRaf)
  window.removeEventListener('resize', resizeCanvas)
  ctx = null
  particles = []
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

// =============================================
// Base – 蓝白交互基调
// =============================================
.auth-layout {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f0f5ff;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

// =============================================
// 呼吸背景层
// =============================================
.bg-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-base {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    160deg,
    #f0f5ff 0%,
    #eef2ff 40%,
    #ecfeff 100%
  );
}

.bg-breath {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 45% 0%, rgba(59, 130, 246, 0.05) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(99, 102, 241, 0.04) 0%, transparent 40%),
    radial-gradient(ellipse at 20% 70%, rgba(6, 182, 212, 0.03) 0%, transparent 35%),
    radial-gradient(ellipse at 55% 100%, rgba(59, 130, 246, 0.03) 0%, transparent 30%);
  animation: breath 2.8s ease-in-out infinite;
  transform-origin: center;
}

@keyframes breath {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 0.85; transform: scale(1.03); }
}

.bg-glow {
  position: absolute;
  inset: 0;
  transition: background 0.6s ease-out;
  z-index: 1;
}

// =============================================
// 粒子画布
// =============================================
.particle-canvas {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
}

// =============================================
// 微几何体
// =============================================
.geo-micron {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.geo-dot {
  position: absolute;
  border-radius: 50%;
  background: rgba(59, 130, 246, 0.25);
  animation: geo-float 8s ease-in-out infinite;
  transform: translate(-50%, -50%);
}

@keyframes geo-float {
  0%, 100% {
    transform: translate(-50%, -50%) translateY(0);
    opacity: 0.02;
  }
  33% {
    transform: translate(-50%, -50%) translateY(-8px);
    opacity: 0.06;
  }
  66% {
    transform: translate(-50%, -50%) translateY(4px);
    opacity: 0.03;
  }
}

// =============================================
// 主容器
// =============================================
.auth-container {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 80px;
  max-width: 1040px;
  width: 90%;
  animation: container-in 1s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes container-in {
  from { opacity: 0; transform: translateY(24px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

// =============================================
// 品牌区（左）– 蓝白
// =============================================
.auth-brand {
  flex: 1;
  max-width: 420px;
  color: #1e293b;
  user-select: none;
  padding-right: 10px;
}

// ---- 图标 ----
.brand-icon {
  position: relative;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 28px;
  transition: transform 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  will-change: transform;
}

.icon-ring {
  position: absolute;
  inset: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(59,130,246,0.08), rgba(99,102,241,0.04));
  border: 1px solid rgba(59, 130, 246, 0.12);
  transition: all 0.4s ease;
}

.brand-icon:hover .icon-ring {
  border-color: rgba(59, 130, 246, 0.25);
  box-shadow: 0 0 20px rgba(59, 130, 246, 0.08);
}

// ---- 标题 ----
.brand-title {
  font-family: 'Instrument Serif', Georgia, 'Times New Roman', serif;
  font-size: 36px;
  font-weight: 400;
  line-height: 1.3;
  margin-bottom: 12px;
  letter-spacing: -0.01em;
  color: #1e293b;
  transition: transform 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  will-change: transform;

  .title-thin {
    font-weight: 300;
    color: #64748b;
  }
}

.brand-sub {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.06em;
  font-weight: 400;
  margin-bottom: 36px;
  transition: transform 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  will-change: transform;
}

// ---- Indicators ----
.brand-indicators {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #64748b;
  transition: color 0.4s ease, transform 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  will-change: transform;
  cursor: default;

  &:hover {
    color: #1e293b;
  }
}

.ind-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: transform 0.3s, box-shadow 0.3s;
}

.indicator:hover .ind-dot {
  transform: scale(1.6);
  box-shadow: 0 0 8px currentColor;
}

.ind-text {
  font-weight: 450;
  letter-spacing: 0.01em;
}

// ---- 品牌底部装饰 ----
.brand-divider {
  width: 40px;
  height: 1px;
  background: linear-gradient(90deg, rgba(59,130,246,0.3), rgba(99,102,241,0.1), transparent);
  margin-top: 40px;
  margin-bottom: 10px;
}

.brand-footer-text {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.12em;
  font-weight: 400;
}

// =============================================
// 卡片（右）
// =============================================
.auth-card-wrap {
  position: relative;
  width: 400px;
  flex-shrink: 0;
}

.card-glow {
  position: absolute;
  inset: -1px;
  border-radius: 20px;
  transition: background 0.6s ease-out;
  opacity: 0.5;
  pointer-events: none;
  animation: glow-breathe 3s ease-in-out infinite;
}

@keyframes glow-breathe {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.55; }
}

.auth-card {
  position: relative;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(24px) saturate(1.4);
  -webkit-backdrop-filter: blur(24px) saturate(1.4);
  border: 1px solid rgba(59, 130, 246, 0.08);
  border-radius: 20px;
  padding: 36px 32px 32px;
  overflow: hidden;
  box-shadow:
    0 4px 24px rgba(59, 130, 246, 0.06),
    0 1px 2px rgba(0, 0, 0, 0.03);
  transition: transform 0.3s cubic-bezier(0.23, 1, 0.32, 1),
              box-shadow 0.3s ease;

  &:hover {
    box-shadow:
      0 8px 36px rgba(59, 130, 246, 0.10),
      0 1px 4px rgba(0, 0, 0, 0.04);
  }
}

.card-rim {
  position: absolute;
  top: 0;
  left: 12%;
  right: 12%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(59, 130, 246, 0.08),
    transparent
  );
}

.card-accent-bar {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 2.5px;
  border-radius: 0 0 3px 3px;
  background: linear-gradient(90deg, #3b82f6, #6366f1);
  opacity: 0.6;
}

.card-shim {
  position: absolute;
  bottom: -20px;
  left: 10%;
  right: 10%;
  height: 40px;
  background: radial-gradient(ellipse at center, rgba(59,130,246,0.04) 0%, transparent 70%);
  pointer-events: none;
  z-index: -1;
}

// =============================================
// 卡片内颜色适配（针对 router-view 传入的 Login/Register）
// =============================================
.auth-card {
  // Login 页
  :deep(.lh-label) {
    color: rgba(59, 130, 246, 0.4) !important;
  }
  :deep(.lh-heading) {
    color: #1e293b !important;
  }

  :deep(.lf-footer) {
    span { color: #94a3b8; }
    a { color: rgba(59, 130, 246, 0.8); }
  }

  :deep(.quick-divider) {
    &::before { background: rgba(59, 130, 246, 0.08) !important; }
    span {
      background: rgba(255, 255, 255, 0.82) !important;
      color: #94a3b8 !important;
    }
  }

  :deep(.quick-chip) {
    background: rgba(59, 130, 246, 0.04) !important;
    border-color: rgba(59, 130, 246, 0.08) !important;
    color: #64748b !important;

    &:hover {
      background: rgba(59, 130, 246, 0.08) !important;
      border-color: rgba(59, 130, 246, 0.15) !important;
      color: #3b82f6 !important;
    }
  }

  // Register 页
  :deep(.form-title) {
    .title-en { color: rgba(59, 130, 246, 0.4) !important; }
    .title-cn { color: #1e293b !important; }
  }

  :deep(.form-footer) {
    span { color: #94a3b8 !important; }
  }

  // 通用输入框
  :deep(.el-input__wrapper) {
    background: rgba(255, 255, 255, 0.6) !important;
    box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.08) inset !important;

    &:hover {
      box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2) inset !important;
    }

    &.is-focus {
      box-shadow: 0 0 0 1.5px rgba(59, 130, 246, 0.35) inset !important;
      background: rgba(255, 255, 255, 0.85) !important;
    }
  }

  :deep(.el-input__inner) {
    color: #1e293b;
    &::placeholder { color: #94a3b8; }
  }

  :deep(.el-input__prefix) {
    color: #94a3b8;
    .el-icon { color: inherit; }
  }

  :deep(.el-input__wrapper.is-focus) .el-input__prefix {
    color: #3b82f6 !important;
  }

  :deep(.el-select .el-input__wrapper) {
    background: rgba(255, 255, 255, 0.6) !important;
  }

  :deep(.el-select-dropdown) {
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(16px);
    border: 1px solid rgba(59, 130, 246, 0.08);

    .el-select-dropdown__item {
      color: #64748b;
      &:hover { background: rgba(59, 130, 246, 0.06); color: #1e293b; }
      &.selected { color: #3b82f6; background: rgba(59, 130, 246, 0.04); }
    }
  }
}

// =============================================
// 响应式
// =============================================
@media (max-width: 1024px) {
  .auth-container {
    gap: 48px;
  }
}

@media (max-width: 820px) {
  .auth-container {
    flex-direction: column;
    gap: 32px;
    max-width: 440px;
  }

  .auth-brand {
    text-align: center;
    max-width: 100%;
    padding-right: 0;

    .brand-icon { margin: 0 auto 20px; }
    .brand-title { font-size: 28px; }
    .brand-sub { display: none; }
    .brand-indicators { display: none; }
    .brand-divider { display: none; }
    .brand-footer-text { display: none; }
  }

  .auth-card-wrap {
    width: 100%;
  }

  .auth-card {
    padding: 28px 20px;
  }
}

@media (max-width: 480px) {
  .auth-container {
    width: 95%;
  }

  .auth-card {
    padding: 24px 16px;
    border-radius: 16px;
  }
}

// =============================================
// 无障碍
// =============================================
@media (prefers-reduced-motion: reduce) {
  .bg-breath,
  .geo-dot,
  .auth-container,
  .card-glow {
    animation: none !important;
  }
  .particle-canvas { display: none; }
  .auth-card,
  .brand-title,
  .brand-sub,
  .brand-icon,
  .indicator {
    transition-duration: 0.01ms !important;
  }
}
</style>
