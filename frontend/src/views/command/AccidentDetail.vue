<template>
  <div class="detail-page" v-loading="loading">
    <!-- 返回按钮 -->
    <div class="back-bar">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <div v-if="accident" class="detail-content">
      <!-- 顶部信息 -->
      <div class="page-card">
        <div class="detail-header">
          <div>
            <h2>{{ accident.type }}</h2>
            <div class="header-meta">
              <span class="meta-item">编号：{{ accident.caseNo }}</span>
              <span class="meta-item">上报人：{{ accident.reporter }}</span>
              <span class="meta-item">时间：{{ accident.reportTime }}</span>
            </div>
          </div>
          <div class="header-tags">
            <RiskBadge :level="accident.riskLevel" size="large" />
            <el-tag :type="statusType" effect="plain" size="large">{{ accident.status }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 现场媒体 + 基本信息 -->
      <el-row :gutter="16" style="margin-top:16px;">
        <el-col :span="10">
          <div class="page-card">
            <h3 class="section-title">现场媒体</h3>
            <div class="media-mode-title" v-if="accident.media?.some(item => item.hasAnnotatedMedia)">
              已显示带框检测结果
            </div>
            <div class="image-gallery" v-if="accident.images?.length">
              <el-image
                v-for="(img, idx) in accident.images"
                :key="idx"
                :src="img.url"
                :preview-src-list="accident.images.map(i => i.url)"
                fit="cover"
                class="gallery-img"
              />
            </div>
            <div class="video-gallery" v-if="accident.videos?.length">
              <video
                v-for="video in accident.videos"
                :key="video.id"
                :src="video.url"
                controls
                class="gallery-video"
              ></video>
            </div>
            <el-alert
              v-if="accident.media?.length && !accident.media.some(item => item.hasAnnotatedMedia)"
              title="暂无带框检测结果，当前仅可查看原始媒体"
              type="warning"
              :closable="false"
              show-icon
              class="media-alert"
            />
            <div v-if="accident.media?.some(item => item.aiDetectedTypes?.length)" class="media-labels">
              <el-tag
                v-for="label in accident.sceneLabels"
                :key="label"
                size="small"
                effect="plain"
              >
                {{ label }}
              </el-tag>
            </div>
            <el-empty v-if="!accident.media?.length" description="暂无现场媒体" :image-size="60" />
          </div>
        </el-col>
        <el-col :span="14">
          <div class="page-card">
            <h3 class="section-title">基本信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="事故地点" :span="2">
                <el-icon><LocationFilled /></el-icon>
                {{ accident.location?.name }}
              </el-descriptions-item>
              <el-descriptions-item label="区域">{{ accident.location?.area }}</el-descriptions-item>
              <el-descriptions-item label="道路等级">{{ accident.roadLevel }}</el-descriptions-item>
              <el-descriptions-item label="路面状况">{{ accident.roadStatus }}</el-descriptions-item>
              <el-descriptions-item label="影响车道">{{ accident.affectedLanes }}</el-descriptions-item>
              <el-descriptions-item label="天气">{{ accident.weather }}</el-descriptions-item>
              <el-descriptions-item label="当前车流">{{ accident.trafficFlow }}</el-descriptions-item>
              <el-descriptions-item label="现场人流">{{ accident.peopleFlow }}</el-descriptions-item>
              <el-descriptions-item label="识别可信度">{{ accident.confidence }}</el-descriptions-item>
              <el-descriptions-item label="是否有人受伤">
                <el-tag :type="accident.injuryReported ? 'danger' : 'success'" size="small">
                  {{ accident.injuryReported ? '是' : '否' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="受伤人数">{{ accident.injuredCount }}人</el-descriptions-item>
              <el-descriptions-item v-if="accident.injuryEstimate" label="伤情描述" :span="2">
                {{ accident.injuryEstimate }}
              </el-descriptions-item>
              <el-descriptions-item v-if="accident.sceneLabels?.length" label="场景识别" :span="2">
                <div class="description-tags">
                  <el-tag
                    v-for="label in accident.sceneLabels"
                    :key="label"
                    size="small"
                    effect="plain"
                  >
                    {{ label }}
                  </el-tag>
                </div>
              </el-descriptions-item>
            </el-descriptions>

            <!-- 百度地图 -->
            <MapCard
              :height="'180px'"
              :title="accident.location?.name"
              hint="事故位置"
              :markers="mapMarkers"
              :center="mapCenter"
              :zoom="15"
              style="margin-top:12px;"
            />
          </div>
        </el-col>
      </el-row>

      <!-- 预测与建议 -->
      <el-row :gutter="16" style="margin-top:16px;">
        <el-col :span="12">
          <div class="page-card">
            <h3 class="section-title">后果预测</h3>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="拥堵持续时间">
                <span class="predict-value">{{ accident.congestionDuration }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="道路恢复时间">
                <span class="predict-value">{{ accident.recoveryTime }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="是否需要支援">
                <el-tag v-if="accident.needSupport?.length" type="danger" size="small">
                  {{ accident.needSupport.join('、') }}
                </el-tag>
                <span v-else class="no-support">暂无需支援</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="page-card">
            <h3 class="section-title">处置建议</h3>
            <el-alert
              :title="accident.disposalAdvice"
              type="warning"
              :closable="false"
              show-icon
              class="advice-alert"
            />
            <el-divider />
            <h4 style="font-size:14px;font-weight:600;margin-bottom:8px;">支援建议</h4>
            <el-alert
              v-if="accident.supportAdvice"
              :title="accident.supportAdvice"
              type="info"
              :closable="false"
              show-icon
            />
            <div v-if="!accident.supportAdvice" style="text-align:center;color:#94a3b8;padding:16px;">
              暂无支援建议
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 算法2风险评估 -->
      <div class="page-card" style="margin-top:16px;">
        <div class="section-header">
          <h3 class="section-title">算法2风险评估</h3>
          <el-tag v-if="accident.modelVersion" size="small" effect="plain">
            {{ accident.modelVersion }}
          </el-tag>
        </div>
        <div class="algorithm-summary">
          <div class="algorithm-metric">
            <span class="metric-label">融合风险等级</span>
            <RiskBadge :level="accident.riskLevel" size="large" />
          </div>
          <div class="algorithm-metric">
            <span class="metric-label">风险评分</span>
            <span class="metric-value">{{ formatRiskScore(accident.riskScore) }}</span>
          </div>
          <div class="algorithm-metric">
            <span class="metric-label">模型置信度</span>
            <span class="metric-value">{{ accident.confidence }}</span>
          </div>
          <div class="algorithm-metric">
            <span class="metric-label">追踪编号</span>
            <code class="trace-code">{{ accident.dataModuleTraceId || '-' }}</code>
          </div>
        </div>

        <el-descriptions :column="2" border class="algorithm-detail">
          <el-descriptions-item label="占用车道">{{ accident.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="车流强度">{{ accident.trafficFlow }}</el-descriptions-item>
          <el-descriptions-item label="人流强度">{{ accident.peopleFlow }}</el-descriptions-item>
          <el-descriptions-item label="天气">{{ accident.weather }}</el-descriptions-item>
          <el-descriptions-item label="道路等级">{{ accident.roadLevel }}</el-descriptions-item>
          <el-descriptions-item label="路面状况">{{ accident.roadStatus }}</el-descriptions-item>
          <el-descriptions-item v-if="accident.riskFactors" label="主要风险因子" :span="2">
            <div class="factor-tags">
              <el-tag
                v-for="factor in splitRiskFactors(accident.riskFactors)"
                :key="factor"
                size="small"
                type="warning"
                effect="plain"
              >
                {{ factor }}
              </el-tag>
            </div>
          </el-descriptions-item>
          <el-descriptions-item v-if="accident.evidenceSummary" label="证据摘要" :span="2">
            {{ accident.evidenceSummary }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- AI 解释 -->
      <div class="page-card" style="margin-top:16px;" v-if="accident.aiExplanation">
        <h3 class="section-title">AI 分析结果说明</h3>
        <div class="ai-box">
          <el-icon :size="20" color="#3b82f6"><ChatLineSquare /></el-icon>
          <p>{{ accident.aiExplanation }}</p>
        </div>
      </div>

      <!-- 处置记录 -->
      <div class="page-card" style="margin-top:16px;">
        <h3 class="section-title">处置记录</h3>
        <StatusTimeline :records="accident.processRecords || []" />
      </div>

      <!-- 操作按钮 -->
      <div class="detail-actions" v-if="userStore.role === 'COMMAND'">
        <el-button type="primary" size="large" icon="Van" @click="createDispatch">
          创建调度任务
        </el-button>
        <el-button size="large" icon="Edit">更新状态</el-button>
      </div>
    </div>

    <!-- 空白占位 -->
    <div v-else-if="!loading" class="empty-state">
      <el-empty description="未找到事故信息" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getAccidentDetail } from '@/services/modules/accident'
import { wgs84ToBd09 } from '@/utils/location'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import StatusTimeline from '@/components/StatusTimeline.vue'
import { ArrowLeft, LocationFilled, ChatLineSquare } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const accident = ref(null)
const loading = ref(true)

const statusType = computed(() => {
  const map = { 待处理: 'danger', 处理中: 'warning', 已处理: 'success', 已结案: 'info' }
  return map[accident.value?.status] || 'info'
})

/** 地图标记（BD09 坐标） */
const mapMarkers = computed(() => {
  if (!accident.value?.location?.lng || !accident.value?.location?.lat) return null
  const bd09 = wgs84ToBd09(accident.value.location.lng, accident.value.location.lat)
  return [{
    lng: bd09.lng,
    lat: bd09.lat,
    label: accident.value.location?.name || '',
  }]
})

/** 地图中心点（BD09） */
const mapCenter = computed(() => {
  if (!accident.value?.location?.lng || !accident.value?.location?.lat) return null
  const bd09 = wgs84ToBd09(accident.value.location.lng, accident.value.location.lat)
  return { lng: bd09.lng, lat: bd09.lat }
})

async function fetchDetail() {
  loading.value = true
  try {
    const id = route.params.id
    const res = await getAccidentDetail(id)
    if (res.code === 200) {
      accident.value = res.data
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchDetail)

function goBack() {
  if (userStore.role === 'RESCUE') {
    router.push('/rescue/tasks')
  } else {
    router.push('/command/dashboard')
  }
}

function createDispatch() {
  if (accident.value) {
    router.push(`/command/dispatch?accidentId=${accident.value.id}`)
  }
}

function formatRiskScore(score) {
  if (score === null || score === undefined || score === '') return '-'
  const value = Number(score)
  if (Number.isNaN(value)) return score
  return value.toFixed(1)
}

function splitRiskFactors(value) {
  if (!value) return []
  return String(value)
    .split(/[、,，;；]/)
    .map((item) => item.trim())
    .filter(Boolean)
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.detail-page {
  max-width: 1100px;
  margin: 0 auto;
}

.back-bar {
  margin-bottom: 12px;

  .el-button {
    &:hover { color: $accent; }
  }
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  h2 {
    font-family: $font-sans;
    font-size: 22px;
    font-weight: 700;
    color: $text-primary;
    margin-bottom: 8px;
    letter-spacing: -0.01em;
  }

  .header-meta {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;

    .meta-item {
      font-size: 13px;
      color: $text-secondary;
    }
  }

  .header-tags {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }
}

.section-title {
  font-family: $font-sans;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
  padding-left: 12px;
  border-left: 3px solid $accent;
  color: $text-primary;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  .section-title {
    margin-bottom: 0;
  }
}

.algorithm-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.algorithm-metric {
  min-height: 72px;
  padding: 12px;
  border: 1px solid $border;
  border-radius: 8px;
  background: rgba($border-light, 0.45);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.metric-label {
  font-size: 12px;
  color: $text-light;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: $text-primary;
  font-variant-numeric: tabular-nums;
}

.trace-code {
  width: fit-content;
  max-width: 100%;
  padding: 2px 6px;
  border-radius: 4px;
  background: $border-light;
  color: $text-secondary;
  font-size: 12px;
  font-family: $font-mono;
  overflow-wrap: anywhere;
}

.algorithm-detail {
  margin-top: 6px;
}

.factor-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.image-gallery {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;

  .gallery-img {
    width: 100%;
    height: 120px;
    border-radius: 8px;
    cursor: pointer;
    transition: opacity 0.2s;

    &:hover { opacity: 0.85; }
  }
}

.video-gallery {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.gallery-video {
  width: 100%;
  max-height: 220px;
  border-radius: 8px;
  background: #000;
}

.media-labels,
.description-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.media-labels {
  margin-top: 12px;
}

.media-mode-title {
  display: inline-flex;
  margin-bottom: 10px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba($success, 0.08);
  color: $success;
  font-size: 12px;
  font-weight: 600;
}

.media-alert {
  margin-top: 10px;
}

.predict-value {
  font-size: 16px;
  font-weight: 600;
  color: $accent;
  font-variant-numeric: tabular-nums;
}

.no-support {
  color: $text-light;
  font-size: 13px;
}

.advice-alert {
  white-space: pre-line;
}

.ai-box {
  display: flex;
  gap: 12px;
  padding: 18px;
  background: linear-gradient(135deg, rgba($accent, 0.05), rgba($accent-secondary, 0.02));
  border-radius: 10px;
  border: 1px solid rgba($accent, 0.08);

  .el-icon { color: $accent; flex-shrink: 0; margin-top: 2px; }

  p {
    flex: 1;
    line-height: 1.7;
    color: $text-primary;
    font-size: 14px;
  }
}

.detail-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.empty-state {
  padding: 80px 0;
}
</style>
