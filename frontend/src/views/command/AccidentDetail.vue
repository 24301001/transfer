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

      <!-- 图片 + 基本信息 -->
      <el-row :gutter="16" style="margin-top:16px;">
        <el-col :span="10">
          <div class="page-card">
            <h3 class="section-title">事故照片</h3>
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
            <el-empty v-else description="暂无照片" :image-size="60" />
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
              <el-descriptions-item label="影响车道">{{ accident.affectedLanes }}</el-descriptions-item>
              <el-descriptions-item label="天气">{{ accident.weather }}</el-descriptions-item>
              <el-descriptions-item label="当前车流">{{ accident.trafficFlow }}</el-descriptions-item>
              <el-descriptions-item label="识别可信度">{{ accident.confidence }}</el-descriptions-item>
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
            <div v-if="!accident.supportAdvice" style="text-align:center;color:#9ca3af;padding:16px;">
              暂无支援建议
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- AI 解释 -->
      <div class="page-card" style="margin-top:16px;" v-if="accident.aiExplanation">
        <h3 class="section-title">AI 分析结果说明</h3>
        <div class="ai-box">
          <el-icon :size="20" color="#1a56db"><ChatLineSquare /></el-icon>
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
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.detail-page {
  max-width: 1100px;
  margin: 0 auto;
}

.back-bar {
  margin-bottom: 12px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  h2 {
    font-size: 22px;
    font-weight: 700;
    margin-bottom: 8px;
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
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
  padding-left: 10px;
  border-left: 3px solid $primary;
}

.image-gallery {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;

  .gallery-img {
    width: 100%;
    height: 120px;
    border-radius: 6px;
    cursor: pointer;
  }
}

.predict-value {
  font-size: 16px;
  font-weight: 600;
  color: $primary;
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
  padding: 16px;
  background: #f0f5ff;
  border-radius: 8px;

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
