<template>
  <div class="dashboard-page">
    <!-- 顶部概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" style="background: rgba(59,130,246,0.10);">
              <el-icon :size="24" color="#3b82f6"><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <span class="stat-num">{{ stats.today }}</span>
              <span class="stat-label">今日事故</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" style="background: rgba(239,68,68,0.10);">
              <el-icon :size="24" color="#ef4444"><CircleCloseFilled /></el-icon>
            </div>
            <div class="stat-info">
              <span class="stat-num">{{ stats.highRisk }}</span>
              <span class="stat-label">高风险事故</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" style="background: rgba(245,158,11,0.10);">
              <el-icon :size="24" color="#f59e0b"><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <span class="stat-num">{{ stats.pending }}</span>
              <span class="stat-label">待处理</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" style="background: rgba(16,185,129,0.10);">
              <el-icon :size="24" color="#10b981"><CircleCheckFilled /></el-icon>
            </div>
            <div class="stat-info">
              <span class="stat-num">{{ stats.resolved }}</span>
              <span class="stat-label">已处理</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 地图（占满宽度） -->
    <el-row :gutter="16" class="main-section">
      <el-col :span="24" style="display:flex; flex-direction:column;">
        <div class="page-card map-card">
          <div class="card-header">
            <h3>事故分布地图</h3>
            <el-tag size="small" type="info">{{ accidentStore.accidentList.length }} 起事故</el-tag>
          </div>
          <MapCard
            height="100%"
            title="指挥大屏地图"
            :hint="mapMarkers.length + ' 起事故'"
            :markers="mapMarkers"
          />
        </div>
      </el-col>
    </el-row>

    <!-- 事故详情侧边栏 -->
    <el-drawer
      v-model="drawerVisible"
      title="事故详情"
      :size="420"
      direction="rtl"
    >
      <template v-if="selectedAccident">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="事故编号">{{ selectedAccident.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ selectedAccident.type }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <RiskBadge :level="selectedAccident.riskLevel" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ selectedAccident.riskScore }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(selectedAccident.status)" size="small" effect="plain">
              {{ selectedAccident.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="地点">{{ selectedAccident.location?.name }}</el-descriptions-item>
          <el-descriptions-item label="上报时间">{{ selectedAccident.reportTime }}</el-descriptions-item>
          <el-descriptions-item label="预计拥堵">{{ selectedAccident.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ selectedAccident.recoveryTime }}</el-descriptions-item>
          <el-descriptions-item label="影响车道">{{ selectedAccident.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="天气">{{ selectedAccident.weather }}</el-descriptions-item>
          <el-descriptions-item label="道路等级">{{ selectedAccident.roadLevel }}</el-descriptions-item>
          <el-descriptions-item label="处置建议">
            <el-alert
              v-if="selectedAccident.disposalAdvice"
              :title="selectedAccident.disposalAdvice"
              type="info"
              :closable="false"
              show-icon
            />
            <span v-else class="text-muted">暂无建议</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 事故描述（列表数据已有） -->
        <div v-if="selectedAccident.description" class="drawer-section">
          <h4 class="drawer-section-title">事故描述</h4>
          <p class="drawer-section-text">{{ selectedAccident.description }}</p>
        </div>

        <!-- 处置反馈（从详情接口获取） -->
        <div v-if="drawerDetail?.dispatchFeedback" class="drawer-section">
          <h4 class="drawer-section-title">
            <el-tag size="small" type="success" effect="plain" style="margin-right:6px;">反馈</el-tag>
            处置反馈
          </h4>
          <p class="drawer-section-text feedback-text">{{ drawerDetail.dispatchFeedback }}</p>
        </div>
        <div v-else-if="drawerDetailLoading" class="drawer-section">
          <p class="text-muted" style="text-align:center;padding:8px;">加载处置反馈...</p>
        </div>
      </template>
      <template #footer>
        <div style="display:flex; gap:12px;">
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button type="primary" @click="goToDetail(selectedAccident?.id)">查看详情</el-button>
          <el-button type="success" @click="openDispatchDialog">
            分配清障人员
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 分配清障人员对话框 -->
    <el-dialog
      v-model="dispatchDialogVisible"
      title="分配清障任务"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="dispatchForm"
        :rules="dispatchFormRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="关联事故">
          <el-input :model-value="selectedAccident?.caseNo || ''" disabled />
        </el-form-item>
        <el-form-item label="调度人员" prop="assignedTo">
          <el-select v-model="dispatchForm.assignedTo" placeholder="选择清障/救援人员" filterable style="width:100%">
            <el-option
              v-for="u in rescueUsers"
              :key="u.id"
              :label="u.nickname"
              :value="u.nickname"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="车辆类型" prop="vehicleType">
          <el-select v-model="dispatchForm.vehicleType" placeholder="选择车辆" style="width:100%">
            <el-option label="清障车" value="清障车" />
            <el-option label="救护车" value="救护车" />
            <el-option label="警车" value="警车" />
            <el-option label="工程车" value="工程车" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="dispatchForm.notes" type="textarea" :rows="3" placeholder="备注信息，如注意事项、特殊要求等" />
        </el-form-item>
        <el-form-item label="分配人">
          <el-input value="李指挥" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitDispatch">
          确认分配
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccidentStore } from '@/stores/accident'
import { getAccidentList, getAccidentDetail } from '@/services/modules/accident'
import { createDispatch } from '@/services/modules/dispatch'
import { wgs84ToBd09 } from '@/utils/location'
import { ElMessage } from 'element-plus'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import {
  WarningFilled, CircleCloseFilled, CircleCheckFilled,
  Clock,
} from '@element-plus/icons-vue'

const router = useRouter()
const accidentStore = useAccidentStore()

// ====== 统计数据 ======
const stats = computed(() => {
  const list = accidentStore.accidentList
  const today = list.filter((a) => {
    const d = new Date(a.reportTime)
    const now = new Date()
    return d.toDateString() === now.toDateString()
  }).length
  const highRisk = list.filter((a) => a.riskLevel === '高' || a.riskLevel === '严重').length
  const pending = list.filter((a) => a.status === '待处理' || a.status === '处理中').length
  const resolved = list.filter((a) => a.status === '已处理' || a.status === '已结案').length
  return { today, highRisk, pending, resolved }
})

// ====== 地图标记 ======
const mapMarkers = computed(() =>
  accidentStore.accidentList
    .filter((a) => a.location?.lng && a.location?.lat)
    .slice(0, 50)
    .map((a) => {
      const bd09 = wgs84ToBd09(a.location.lng, a.location.lat)
      return {
        lng: bd09.lng,
        lat: bd09.lat,
        label: a.type,
        onClick: () => openDrawer(a.id),
      }
    })
)

// ====== Marker 点击 → 抽屉 ======
const selectedAccidentId = ref(null)
const drawerVisible = ref(false)
const drawerDetail = ref(null)
const drawerDetailLoading = ref(false)

const selectedAccident = computed(() => {
  if (!selectedAccidentId.value) return null
  return accidentStore.accidentList.find((a) => a.id === selectedAccidentId.value) || null
})

async function openDrawer(accidentId) {
  selectedAccidentId.value = accidentId
  drawerVisible.value = true
  // 获取完整详情（含调度反馈）
  drawerDetailLoading.value = true
  drawerDetail.value = null
  try {
    const res = await getAccidentDetail(accidentId)
    if (res.code === 200) {
      drawerDetail.value = res.data
    }
  } catch {
    // 静默失败，drawer 仍显示列表数据
  } finally {
    drawerDetailLoading.value = false
  }
}

// ====== 分配清障对话框 ======
const dispatchDialogVisible = ref(false)
const creating = ref(false)
const formRef = ref(null)
const dispatchForm = ref({
  assignedTo: '',
  vehicleType: '',
  notes: '',
})
const rescueUsers = [
  { id: 1, nickname: '王队长' },
  { id: 2, nickname: '陈师傅' },
  { id: 3, nickname: '刘师傅' },
  { id: 4, nickname: '张师傅' },
]
const dispatchFormRules = {
  assignedTo: [{ required: true, message: '请选择调度人员', trigger: 'change' }],
  vehicleType: [{ required: true, message: '请选择车辆类型', trigger: 'change' }],
}

function openDispatchDialog() {
  dispatchDialogVisible.value = true
}

async function submitDispatch() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (!selectedAccident.value) return

  creating.value = true
  try {
    const res = await createDispatch({
      accidentId: selectedAccident.value.id,
      assignedTo: dispatchForm.value.assignedTo,
      vehicleType: dispatchForm.value.vehicleType,
      notes: dispatchForm.value.notes,
      assigner: '李指挥',
    })
    if (res.code === 200) {
      ElMessage.success('调度任务创建成功')
      dispatchDialogVisible.value = false
      drawerVisible.value = false
      dispatchForm.value = { assignedTo: '', vehicleType: '', notes: '' }
    }
  } catch (err) {
    ElMessage.error('创建调度任务失败: ' + (err.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

// ====== 通用 ======
function statusType(status) {
  const map = { 待处理: 'danger', 处理中: 'warning', 已处理: 'success', 已结案: 'info' }
  return map[status] || 'info'
}

function goToDetail(id) {
  router.push(`/command/accident/${id}`)
}

async function fetchAccidents() {
  accidentStore.loading = true
  try {
    const res = await getAccidentList()
    if (res.code === 200) {
      accidentStore.setAccidents(res.data.list)
    }
  } finally {
    accidentStore.loading = false
  }
}

// 轮询刷新（5秒检测新数据）
let pollTimer = null
onMounted(() => {
  fetchAccidents()
  pollTimer = setInterval(fetchAccidents, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.dashboard-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - #{$header-height} - 48px);
  overflow: hidden;
}

// ===== 统计卡片 =====
.stat-cards {
  flex-shrink: 0;
  margin-bottom: 16px;

  .stat-card {
    --el-card-padding: 0;
  }

  .el-card {
    border-radius: 14px;
    overflow: hidden;
  }

  .stat-inner {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 18px;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;

    // 渐变底色覆盖原始内联 style
    &[style*="background: rgba(59,130,246,0.10)"] {
      background: linear-gradient(135deg, rgba($accent, 0.12), rgba($accent-secondary, 0.06)) !important;
    }
    &[style*="background: rgba(239,68,68,0.10)"] {
      background: linear-gradient(135deg, rgba(239,68,68,0.12), rgba(248,113,113,0.06)) !important;
    }
    &[style*="background: rgba(245,158,11,0.10)"] {
      background: linear-gradient(135deg, rgba(245,158,11,0.12), rgba(251,191,36,0.06)) !important;
    }
    &[style*="background: rgba(16,185,129,0.10)"] {
      background: linear-gradient(135deg, rgba(16,185,129,0.12), rgba(52,211,153,0.06)) !important;
    }
  }

  .stat-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .stat-num {
    font-size: 26px;
    font-weight: 700;
    color: $text-primary;
    line-height: 1.15;
    letter-spacing: -0.02em;
    font-variant-numeric: tabular-nums;
  }

  .stat-label {
    font-size: 12px;
    color: $text-secondary;
    font-weight: 500;
  }
}

// ===== 主区域（地图） =====
.main-section {
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
}

.map-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  .card-header {
    flex-shrink: 0;
    margin-bottom: 12px;
    padding: 0;

    h3 {
      font-size: 16px;
      font-weight: 600;
    }
  }

  :deep(.baidu-map-card) {
    flex: 1;
    min-height: 0;
    border-radius: 10px;
  }
}

// ===== 通用卡片头部 =====
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h3 {
    font-family: $font-sans;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    letter-spacing: -0.01em;
  }
}

.text-muted {
  color: $text-light;
  font-size: 13px;
}

// ===== drawer 额外内容 =====
.drawer-section {
  margin-top: 16px;
  padding: 14px;
  background: var(--el-bg-color-page);
  border-radius: 10px;
}

.drawer-section-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
}

.drawer-section-text {
  font-size: 13px;
  color: $text-secondary;
  line-height: 1.7;
  white-space: pre-wrap;
  margin: 0;
}

.feedback-text {
  color: $text-primary;
}
</style>
