<template>
  <div class="health-page">
    <!-- 顶部：标题 + 状态 + 操作 -->
    <div class="health-header">
      <div class="header-left">
        <h2 class="page-title">🩺 系统健康</h2>
        <div class="status-badge" :class="'is-' + (data?.status || '').toLowerCase()">
          <span class="status-dot"></span>
          <span>{{ statusLabel }}</span>
        </div>
        <span class="uptime" v-if="data?.uptime">已运行 {{ data.uptime }}</span>
      </div>
      <div class="header-right">
        <span class="check-time" v-if="data?.checkedAt">
          检测时间：{{ formatTime(data.checkedAt) }}
        </span>
        <el-button size="small" :loading="loading" @click="fetchHealth" class="refresh-btn">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 状态消息 -->
    <div class="status-message" v-if="data?.statusMessage">
      <el-icon :size="16" v-if="data.status === 'UP'"><CircleCheck /></el-icon>
      <el-icon :size="16" v-else-if="data.status === 'DEGRADED'"><WarningFilled /></el-icon>
      <el-icon :size="16" v-else><CircleCloseFilled /></el-icon>
      {{ data.statusMessage }}
    </div>

    <!-- 告警 -->
    <el-alert
      v-if="data?.warnings?.length"
      title="系统告警"
      type="warning"
      :description="data.warnings.join('；')"
      show-icon
      :closable="false"
      class="warn-alert"
    />

    <!-- 资源使用率 -->
    <div class="card-grid resource-grid" v-if="data?.resources">
      <div class="stat-card">
        <div class="stat-label">堆内存使用率</div>
        <div class="progress-wrap">
          <el-progress
            :percentage="Math.round(data.resources.heapUsagePercent)"
            :color="heapColor"
            :stroke-width="10"
            :show-text="false"
          />
        </div>
        <div class="stat-value">{{ formatBytes(data.resources.heapUsedBytes) }} / {{ formatBytes(data.resources.heapMaxBytes) }}</div>
        <div class="stat-detail">
          已提交 {{ formatBytes(data.resources.heapCommittedBytes) }} ·
          非堆 {{ formatBytes(data.resources.nonHeapUsedBytes) }}
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-label">磁盘使用率</div>
        <div class="progress-wrap">
          <el-progress
            :percentage="Math.round(data.resources.diskUsagePercent)"
            :color="diskColor"
            :stroke-width="10"
            :show-text="false"
          />
        </div>
        <div class="stat-value">{{ formatBytes(data.resources.diskTotalBytes - data.resources.diskUsableBytes) }} / {{ formatBytes(data.resources.diskTotalBytes) }}</div>
        <div class="stat-detail">可用 {{ formatBytes(data.resources.diskUsableBytes) }}</div>
      </div>

      <div class="stat-card">
        <div class="stat-label">CPU 使用率</div>
        <div class="cpu-bars">
          <div class="cpu-row">
            <span class="cpu-key">进程</span>
            <el-progress
              :percentage="normalizeCpuPct(data.resources.processCpuUsagePercent)"
              :stroke-width="8"
              :show-text="false"
              class="cpu-bar"
            />
            <span class="cpu-val">{{ formatCpu(data.resources.processCpuUsagePercent) }}</span>
          </div>
          <div class="cpu-row">
            <span class="cpu-key">系统</span>
            <el-progress
              :percentage="normalizeCpuPct(data.resources.systemCpuUsagePercent)"
              :stroke-width="8"
              :show-text="false"
              color="#8b5cf6"
              class="cpu-bar"
            />
            <span class="cpu-val">{{ formatCpu(data.resources.systemCpuUsagePercent) }}</span>
          </div>
        </div>
        <div class="stat-detail">vCPU × {{ data.server?.availableProcessors ?? '?' }} · 负载 {{ data.server?.systemLoadAverage ?? '-' }}</div>
      </div>
    </div>

    <!-- 应用与服务器信息 -->
    <div class="page-card" v-if="data?.application">
      <h4 class="section-title"><el-icon><InfoFilled /></el-icon> 应用与服务器信息</h4>
      <div class="info-grid">
        <div class="info-item">
          <span class="info-key">应用名</span>
          <span class="info-val">{{ data.application.name }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">版本</span>
          <span class="info-val"><code>{{ data.application.version }}</code></span>
        </div>
        <div class="info-item">
          <span class="info-key">Profile</span>
          <span class="info-val">
            <el-tag v-for="p in data.application.activeProfiles" :key="p" size="small" class="profile-tag">{{ p }}</el-tag>
          </span>
        </div>
        <div class="info-item">
          <span class="info-key">端口</span>
          <span class="info-val">{{ data.server?.port ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">Java 版本</span>
          <span class="info-val">{{ data.application.javaVersion }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">Spring Boot</span>
          <span class="info-val">{{ data.application.springBootVersion }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">时区</span>
          <span class="info-val">{{ data.application.timezone }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">主机名</span>
          <span class="info-val"><code>{{ data.server?.hostName ?? '-' }}</code></span>
        </div>
        <div class="info-item">
          <span class="info-key">操作系统</span>
          <span class="info-val">{{ data.server?.operatingSystem ?? '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-key">架构</span>
          <span class="info-val">{{ data.server?.architecture ?? '-' }}</span>
        </div>
      </div>
    </div>

    <!-- 组件依赖 -->
    <div class="page-card" v-if="componentList.length">
      <h4 class="section-title"><el-icon><Connection /></el-icon> 组件依赖</h4>
      <el-table :data="componentList" stripe class="comp-table">
        <el-table-column label="组件" prop="displayName" width="150" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag
              :type="compTagType(row.status)"
              size="small"
              class="comp-tag"
            >
              <span class="comp-dot" :class="'dot-' + row.status.toLowerCase()"></span>
              {{ compLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="响应时间" width="110">
          <template #default="{ row }">
            <span class="comp-ms" v-if="row.responseTimeMs != null">{{ row.responseTimeMs }}ms</span>
            <span class="comp-ms na" v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="消息" min-width="200">
          <template #default="{ row }">
            <span class="comp-msg">{{ row.message }}</span>
            <div class="comp-details" v-if="row.detailText">
              <span class="detail-toggle" @click="row._expanded = !row._expanded">
                {{ row._expanded ? '收起' : '详情' }}
              </span>
              <pre v-if="row._expanded" class="detail-pre">{{ row.detailText }}</pre>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 业务数据概览 -->
    <div class="page-card" v-if="data?.businessMetrics">
      <h4 class="section-title"><el-icon><DataAnalysis /></el-icon> 业务数据概览</h4>
      <div class="biz-grid">
        <!-- 用户 -->
        <div class="biz-group">
          <div class="biz-title">用户</div>
          <div class="biz-ring">{{ data.businessMetrics.totalUsers }}</div>
          <div class="biz-subs">
            <span class="biz-up">启用 {{ data.businessMetrics.enabledUsers }}</span>
            <span class="biz-divider">·</span>
            <span class="biz-down">停用 {{ data.businessMetrics.disabledUsers }}</span>
          </div>
        </div>
        <!-- 事故 -->
        <div class="biz-group">
          <div class="biz-title">事故</div>
          <div class="biz-ring accent">{{ data.businessMetrics.totalIncidents }}</div>
          <div class="biz-subs">
            <span class="biz-active">进行中 {{ data.businessMetrics.activeIncidents }}</span>
            <span class="biz-divider">·</span>
            <span class="biz-done">已关闭 {{ data.businessMetrics.closedIncidents }}</span>
          </div>
        </div>
        <!-- 调度 -->
        <div class="biz-group">
          <div class="biz-title">调度任务</div>
          <div class="biz-ring purple">{{ data.businessMetrics.totalDispatchTasks }}</div>
          <div class="biz-subs">
            <span class="biz-active">活跃 {{ data.businessMetrics.activeDispatchTasks }}</span>
            <span class="biz-divider">·</span>
            <span class="biz-done">完成 {{ data.businessMetrics.completedDispatchTasks }}</span>
          </div>
        </div>
        <!-- 车辆 -->
        <div class="biz-group">
          <div class="biz-title">清障车辆</div>
          <div class="biz-ring cyan">{{ data.businessMetrics.totalEmergencyVehicles }}</div>
          <div class="biz-subs">
            <span class="biz-up">可用 {{ data.businessMetrics.availableEmergencyVehicles }}</span>
            <span class="biz-divider">·</span>
            <span class="biz-down">停用 {{ data.businessMetrics.outOfServiceEmergencyVehicles }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 提示 -->
    <div class="page-card hint-card">
      <el-alert
        title="提示：仅 admin 角色可见，用于排查后端部署状态。健康检查接口不会记录访问日志。"
        type="info"
        :closable="false"
        show-icon
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSystemHealth } from '@/services/modules/system'
import {
  Refresh, CircleCheck, WarningFilled, CircleCloseFilled,
  InfoFilled, Connection, DataAnalysis,
} from '@element-plus/icons-vue'

const loading = ref(false)
const data = ref(null)
const error = ref(false)

const statusLabel = computed(() => {
  if (!data.value) return '检测中...'
  const map = { UP: '运行正常', DEGRADED: '服务降级', DOWN: '服务异常' }
  return map[data.value.status] || data.value.status
})

const componentList = computed(() => {
  const comps = data.value?.components
  if (!comps) return []
  const displayNames = {
    database: '数据库', redis: 'Redis',
    predictionModule: '事故预测', yoloService: 'YOLO 识别',
    siliconFlowAi: '硅基流动 AI', baiduMap: '百度地图', mail: '邮件服务',
  }
  return Object.entries(comps).map(([key, c]) => ({
    key,
    displayName: displayNames[key] || key,
    status: c.status,
    configured: c.configured,
    responseTimeMs: c.responseTimeMs,
    message: c.message,
    detailText: c.details && Object.keys(c.details).length
      ? JSON.stringify(c.details, null, 2)
      : null,
    _expanded: false,
  }))
})

function compTagType(status) {
  if (status === 'UP') return 'success'
  if (status === 'DEGRADED') return 'warning'
  if (status === 'NOT_CONFIGURED') return 'info'
  return 'danger'
}

function compLabel(row) {
  if (row.status === 'UP') return '已连接'
  if (row.status === 'DEGRADED') return '降级'
  if (row.status === 'NOT_CONFIGURED') return '未配置'
  return '异常'
}

function heapColor(pct) {
  if (pct >= 85) return 'linear-gradient(to right, #ef4444, #dc2626)'
  if (pct >= 65) return 'linear-gradient(to right, #f59e0b, #d97706)'
  return 'linear-gradient(to right, #10b981, #059669)'
}

function diskColor(pct) {
  if (pct >= 90) return 'linear-gradient(to right, #ef4444, #dc2626)'
  if (pct >= 75) return 'linear-gradient(to right, #f59e0b, #d97706)'
  return 'linear-gradient(to right, #3b82f6, #6366f1)'
}

function normalizeCpuPct(val) {
  if (val == null || val < 0) return 0
  return Math.min(100, Math.round(val))
}

function formatCpu(val) {
  if (val == null || val < 0) return '—'
  return val.toFixed(1) + '%'
}

function formatBytes(bytes) {
  if (bytes == null || bytes < 0) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(bytes >= 1024 ** 3 ? 1 : 0) + ' ' + units[i]
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function fetchHealth() {
  loading.value = true
  error.value = false
  try {
    const res = await getSystemHealth()
    if (res.code === 200) {
      data.value = res.data
    }
  } catch (e) {
    error.value = true
    data.value = {
      status: 'DOWN',
      statusMessage: '无法连接到后端服务',
      checkedAt: new Date().toISOString(),
      uptime: '',
      uptimeSeconds: 0,
      application: null,
      server: null,
      resources: null,
      components: {},
      businessMetrics: null,
      warnings: ['后端服务不可达，请检查服务状态和网络连接'],
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchHealth)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.health-page {
  animation: fade-in 0.35s ease both;
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

// ===== 顶部 =====
.health-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 20px 24px;
  box-shadow: $shadow-sm;
  border: 1px solid $border;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 14px;
  }
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 14px;
  border-radius: $radius-full;
  font-size: 13px;
  font-weight: 600;

  &.is-up {
    background: rgba($success, 0.08);
    color: $success;
    border: 1px solid rgba($success, 0.15);
  }
  &.is-degraded {
    background: rgba($warning, 0.08);
    color: $warning;
    border: 1px solid rgba($warning, 0.15);
  }
  &.is-down {
    background: rgba($danger, 0.08);
    color: $danger;
    border: 1px solid rgba($danger, 0.15);
  }

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: currentColor;
    animation: pulse-dot 2s ease-in-out infinite;
  }
}

@keyframes pulse-dot {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.35); }
}

.uptime {
  font-size: 12px;
  color: $text-secondary;
  font-family: $font-mono;
}

.check-time {
  font-size: 12px;
  color: $text-light;
  font-family: $font-mono;
}

.refresh-btn {
  flex-shrink: 0;
}

// ===== 状态消息 =====
.status-message {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: $text-secondary;
  padding: 10px 24px;
  background: $bg-white;
  border-radius: $radius-md;
  margin-bottom: 16px;
  border: 1px solid $border;
  box-shadow: $shadow-sm;
}

// ===== 告警 =====
.warn-alert {
  margin-bottom: 16px;
}

// ===== 资源卡片 =====
.resource-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 18px 20px;
  border: 1px solid $border;
  box-shadow: $shadow-sm;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.stat-label {
  font-size: 12px;
  font-weight: 600;
  color: $text-secondary;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 10px;
}

.progress-wrap {
  margin-bottom: 8px;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
  font-family: $font-mono;
}

.stat-detail {
  font-size: 11px;
  color: $text-light;
  margin-top: 4px;
  font-family: $font-mono;
}

.cpu-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 6px;
}

.cpu-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .cpu-key {
    font-size: 11px;
    color: $text-secondary;
    width: 28px;
    text-align: right;
    flex-shrink: 0;
  }

  .cpu-bar {
    flex: 1;
  }

  .cpu-val {
    font-size: 13px;
    font-weight: 600;
    color: $text-primary;
    font-family: $font-mono;
    width: 50px;
    text-align: right;
    flex-shrink: 0;
  }
}

// ===== 通用卡片 =====
.page-card {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 20px 24px;
  border: 1px solid $border;
  box-shadow: $shadow-sm;
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 6px;
}

// ===== 信息网格 =====
.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px 20px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid $border-light;

  .info-key {
    font-size: 12px;
    color: $text-secondary;
    min-width: 80px;
    flex-shrink: 0;
  }

  .info-val {
    font-size: 13px;
    color: $text-primary;
    font-family: $font-mono;
    word-break: break-all;

    code {
      background: rgba($primary, 0.06);
      padding: 1px 6px;
      border-radius: 4px;
    }
  }
}

.profile-tag {
  margin-right: 4px;
}

// ===== 组件表格 =====
.comp-table {
  :deep(.el-table__cell) { padding: 8px 12px; }
}

.comp-tag {
  border: none !important;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.comp-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;

  &.dot-up { background: $success; }
  &.dot-degraded { background: $warning; }
  &.dot-not_configured { background: $text-light; }
  &.dot-down { background: $danger; }
}

.comp-ms {
  font-family: $font-mono;
  font-size: 12px;
  color: $text-primary;

  &.na { color: $text-light; }
}

.comp-msg {
  font-size: 12px;
  color: $text-primary;
}

.detail-toggle {
  font-size: 11px;
  color: $primary;
  cursor: pointer;
  margin-left: 4px;
  user-select: none;

  &:hover { color: $primary-dark; }
}

.detail-pre {
  margin: 6px 0 0;
  padding: 8px 10px;
  background: #f8fafc;
  border-radius: 6px;
  font-size: 11px;
  font-family: $font-mono;
  line-height: 1.5;
  overflow-x: auto;
  white-space: pre-wrap;
  color: $text-secondary;
  border: 1px solid $border-light;
}

// ===== 业务数据 =====
.biz-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.biz-group {
  text-align: center;
  padding: 16px 12px;
  border-radius: $radius-md;
  background: linear-gradient(135deg, rgba($primary, 0.02), rgba($primary, 0.05));
  border: 1px solid $border-light;
}

.biz-title {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: $text-light;
  margin-bottom: 10px;
}

.biz-ring {
  font-size: 30px;
  font-weight: 700;
  font-family: $font-mono;
  color: $success;
  line-height: 1;

  &.accent { color: $primary; }
  &.purple { color: #8b5cf6; }
  &.cyan { color: #06b6d4; }
}

.biz-subs {
  margin-top: 8px;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.biz-divider {
  color: $border;
}

.biz-up { color: $success; }
.biz-down { color: $text-light; }
.biz-active { color: $warning; }
.biz-done { color: $primary; }

// ===== 提示 =====
.hint-card {
  margin-top: 0;
}

// ===== 响应式 =====
@media (max-width: 960px) {
  .resource-grid {
    grid-template-columns: 1fr;
  }

  .biz-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .health-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .biz-grid {
    grid-template-columns: 1fr;
  }
}
</style>
