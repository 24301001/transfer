<template>
  <div class="dashboard-page">
    <!-- 顶部概览卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon" style="background: #eff6ff;">
              <el-icon :size="24" color="#1a56db"><WarningFilled /></el-icon>
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
            <div class="stat-icon" style="background: #fef2f2;">
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
            <div class="stat-icon" style="background: #fffbeb;">
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
            <div class="stat-icon" style="background: #f0fdf4;">
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

    <!-- 地图 + 事故列表 -->
    <el-row :gutter="16" class="main-section">
      <el-col :span="14">
        <div class="page-card">
          <div class="card-header">
            <h3>事故分布地图</h3>
            <el-tag size="small" type="info">{{ accidentStore.accidentList.length }} 起事故</el-tag>
          </div>
          <MapCard
            :height="'400px'"
            title="指挥大屏地图"
            hint="事故点位分布示意（红线为风险等级）"
            :markers="mapMarkers"
          />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="page-card">
          <div class="card-header">
            <h3>高风险事故提醒</h3>
            <el-badge :value="stats.highRisk" :hidden="stats.highRisk === 0" class="badge">
              <el-button size="small" @click="filterHighRisk">查看全部</el-button>
            </el-badge>
          </div>
          <div class="risk-list" v-if="highRiskAccidents.length">
            <div
              v-for="item in highRiskAccidents.slice(0, 5)"
              :key="item.id"
              class="risk-item"
              @click="goToDetail(item.id)"
            >
              <RiskBadge :level="item.riskLevel" size="small" />
              <div class="risk-info">
                <span class="risk-title">{{ item.type }} - {{ item.location?.name }}</span>
                <span class="risk-time">{{ item.reportTime }}</span>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
          <el-empty v-else description="暂无高风险事故" :image-size="80" />
        </div>
      </el-col>
    </el-row>

    <!-- 事故列表 -->
    <div class="page-card" style="margin-top: 16px;">
      <div class="card-header">
        <h3>事故列表</h3>
        <div class="filter-bar">
          <el-select v-model="filterRisk" placeholder="风险等级" clearable size="small" style="width:120px" @change="fetchAccidents">
            <el-option label="低" value="低" />
            <el-option label="中" value="中" />
            <el-option label="高" value="高" />
            <el-option label="严重" value="严重" />
          </el-select>
          <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width:120px" @change="fetchAccidents">
            <el-option label="待处理" value="待处理" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已处理" value="已处理" />
          </el-select>
          <el-button size="small" icon="Refresh" @click="fetchAccidents">刷新</el-button>
        </div>
      </div>
      <el-table :data="accidentStore.accidentList" stripe @row-click="goToDetail" style="cursor:pointer;">
        <el-table-column prop="caseNo" label="编号" width="140" />
        <el-table-column prop="type" label="事故类型" width="110" />
        <el-table-column label="风险等级" width="90">
          <template #default="{ row }">
            <RiskBadge :level="row.riskLevel" size="small" v-if="row.riskLevel !== '-'" />
            <span v-else class="analyzing">分析中...</span>
          </template>
        </el-table-column>
        <el-table-column prop="location?.name" label="地点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reportTime" label="上报时间" width="150" />
        <el-table-column prop="congestionDuration" label="拥堵预测" width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.congestionDuration !== '分析中...'"
              type="warning"
              size="small"
              plain
              @click.stop="showCongestionDetail(row)"
            >
              <el-icon><Clock /></el-icon>
              {{ row.congestionDuration }}
            </el-button>
            <span v-else class="analyzing">分析中...</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 高风险事故弹窗 -->
    <el-dialog v-model="highRiskDialog" title="高风险事故详情" width="600px">
      <template v-if="currentHighRisk">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事故编号">{{ currentHighRisk.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ currentHighRisk.type }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <RiskBadge :level="currentHighRisk.riskLevel" />
          </el-descriptions-item>
          <el-descriptions-item label="风险评分">{{ currentHighRisk.riskScore }}</el-descriptions-item>
          <el-descriptions-item label="地点" :span="2">{{ currentHighRisk.location?.name }}</el-descriptions-item>
          <el-descriptions-item label="预计拥堵">{{ currentHighRisk.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="恢复时间">{{ currentHighRisk.recoveryTime }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:12px;">
          <el-alert :title="currentHighRisk.supportAdvice" type="warning" :closable="false" show-icon />
        </div>
      </template>
      <template #footer>
        <el-button @click="highRiskDialog = false">关闭</el-button>
        <el-button type="primary" @click="goToDetail(currentHighRisk?.id)">查看详情</el-button>
      </template>
    </el-dialog>

    <!-- 拥堵预测详情弹窗 -->
    <el-dialog v-model="congestionDialog" title="拥堵预测详情" width="550px" top="25vh">
      <template v-if="congestionItem">
        <div class="congestion-header">
          <el-tag size="large" effect="dark" type="warning">
            <el-icon style="vertical-align:-2px;margin-right:4px;"><Clock /></el-icon>
            预计拥堵 {{ congestionItem.congestionDuration }}
          </el-tag>
        </div>
        <el-descriptions :column="2" border style="margin-top:16px;">
          <el-descriptions-item label="事故编号">{{ congestionItem.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="事故类型">{{ congestionItem.type }}</el-descriptions-item>
          <el-descriptions-item label="拥堵时长">{{ congestionItem.congestionDuration }}</el-descriptions-item>
          <el-descriptions-item label="道路恢复">{{ congestionItem.recoveryTime }}</el-descriptions-item>
          <el-descriptions-item label="影响车道">{{ congestionItem.affectedLanes }}</el-descriptions-item>
          <el-descriptions-item label="当前车流">{{ congestionItem.trafficFlow }}</el-descriptions-item>
          <el-descriptions-item label="天气">{{ congestionItem.weather }}</el-descriptions-item>
          <el-descriptions-item label="道路等级">{{ congestionItem.roadLevel }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <RiskBadge :level="congestionItem.riskLevel" size="small" />
          </el-descriptions-item>
          <el-descriptions-item label="地点" :span="2">{{ congestionItem.location?.name }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top:12px;">
          <h4 style="font-size:14px;font-weight:600;margin-bottom:8px;">处置建议</h4>
          <el-alert
            :title="congestionItem.disposalAdvice"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </template>
      <template #footer>
        <el-button @click="congestionDialog = false">关闭</el-button>
        <el-button type="primary" @click="goToDetail(congestionItem?.id)">查看详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccidentStore } from '@/stores/accident'
import { getAccidentList } from '@/services/modules/accident'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'
import {
  WarningFilled, CircleCloseFilled, CircleCheckFilled,
  Clock, ArrowRight, Refresh,
} from '@element-plus/icons-vue'

const router = useRouter()
const accidentStore = useAccidentStore()

const filterRisk = ref('')
const filterStatus = ref('')
const highRiskDialog = ref(false)
const currentHighRisk = ref(null)
const congestionDialog = ref(false)
const congestionItem = ref(null)

// 统计数据
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

// 高风险事故
const highRiskAccidents = computed(() =>
  accidentStore.accidentList.filter((a) => a.riskLevel === '高' || a.riskLevel === '严重')
)

// 地图标记
const mapMarkers = computed(() =>
  accidentStore.accidentList.slice(0, 20).map((a, idx) => ({
    x: 15 + (idx * 3.5) % 70,
    y: 15 + (idx * 5.7) % 70,
    label: a.type,
    count: a.riskLevel === '严重' ? '!' : '',
    type: a.riskLevel === '严重' ? 'danger' : a.riskLevel === '高' ? 'warning' : 'primary',
    color: a.riskLevel === '严重' ? '#ef4444' : a.riskLevel === '高' ? '#f97316' : '#3b82f6',
  }))
)

function statusType(status) {
  const map = { 待处理: 'danger', 处理中: 'warning', 已处理: 'success', 已结案: 'info' }
  return map[status] || 'info'
}

function filterHighRisk() {
  if (highRiskAccidents.value.length) {
    currentHighRisk.value = highRiskAccidents.value[0]
    highRiskDialog.value = true
  }
}

function showCongestionDetail(row) {
  congestionItem.value = row
  congestionDialog.value = true
}

function goToDetail(id) {
  router.push(`/command/accident/${id}`)
}

async function fetchAccidents() {
  accidentStore.loading = true
  try {
    const res = await getAccidentList({
      riskLevel: filterRisk.value || undefined,
      status: filterStatus.value || undefined,
    })
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

.stat-cards {
  margin-bottom: 16px;

  .stat-card {
    --el-card-padding: 16px;
  }

  .stat-inner {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .stat-icon {
    width: 52px;
    height: 52px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .stat-info {
    display: flex;
    flex-direction: column;
  }

  .stat-num {
    font-size: 28px;
    font-weight: 700;
    color: $text-primary;
    line-height: 1.2;
  }

  .stat-label {
    font-size: 13px;
    color: $text-secondary;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  h3 {
    font-size: 16px;
    font-weight: 600;
  }
}

.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.risk-list {
  .risk-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 8px;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: #f8fafc;
    }

    .risk-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;

      .risk-title {
        font-size: 13px;
        color: $text-primary;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .risk-time {
        font-size: 11px;
        color: $text-light;
        margin-top: 2px;
      }
    }
  }
}

.analyzing {
  color: $text-light;
  font-style: italic;
  font-size: 12px;
}

.congestion-header {
  text-align: center;
  padding: 8px 0 16px;

  .el-tag {
    font-size: 18px;
    padding: 8px 24px;
  }
}

:deep(.el-table) .el-button--small {
  font-size: 12px;
  padding: 4px 10px;
}

.main-section {
  margin-bottom: 0;
}
</style>
