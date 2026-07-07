<template>
  <div class="admin-page" v-loading="loading">
    <div class="page-header">
      <h2>基础数据</h2>
      <p>系统运行统计数据与事故分析</p>
    </div>

    <!-- 概览 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-num">{{ data?.totalAccidents || 0 }}</span>
            <span class="stat-label">事故总数</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-num">{{ data?.totalDispatches || 0 }}</span>
            <span class="stat-label">调度次数</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-num">{{ data?.successRate || '0%' }}</span>
            <span class="stat-label">处置成功率</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-num">{{ data?.avgProcessTime || '0' }}</span>
            <span class="stat-label">平均处理时间</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px;">
      <!-- 事故类型分布 -->
      <el-col :span="12">
        <div class="page-card">
          <h3 class="section-title">事故类型分布</h3>
          <div class="type-chart" v-if="data?.accidentByType">
            <div v-for="(val, key) in data.accidentByType" :key="key" class="type-row">
              <span class="type-name">{{ key }}</span>
              <el-progress
                :percentage="Math.round(val.count / data.totalAccidents * 100)"
                :stroke-width="16"
                :status="val.trend === 'up' ? 'exception' : val.trend === 'down' ? 'success' : undefined"
              >
                <span class="type-count">{{ val.count }}起</span>
              </el-progress>
              <el-tag v-if="val.trend === 'up'" size="small" type="danger">↑ 上升</el-tag>
              <el-tag v-else-if="val.trend === 'down'" size="small" type="success">↓ 下降</el-tag>
              <el-tag v-else size="small" type="info">→ 持平</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 风险等级分布 -->
      <el-col :span="12">
        <div class="page-card">
          <h3 class="section-title">风险等级分布</h3>
          <div class="risk-chart" v-if="data?.riskDistribution">
            <div v-for="(count, level) in data.riskDistribution" :key="level" class="risk-row">
              <span class="risk-name">
                <RiskBadge :level="level" size="small" :show-icon="false" />
              </span>
              <el-progress
                :percentage="Math.round(count / data.totalAccidents * 100)"
                :stroke-width="20"
                :color="riskColor(level)"
              />
              <span class="risk-count">{{ count }}起</span>
            </div>
          </div>
        </div>

        <!-- 月度趋势 -->
        <div class="page-card" style="margin-top:16px;">
          <h3 class="section-title">月度事故趋势</h3>
          <div class="month-chart" v-if="data?.monthlyStats">
            <div v-for="item in data.monthlyStats" :key="item.month" class="month-row">
              <span class="month-label">{{ item.month }}</span>
              <el-progress
                :percentage="item.accidents"
                :stroke-width="24"
                color="#1a56db"
              >
                <span>{{ item.accidents }}起</span>
              </el-progress>
              <span class="month-response">响应 {{ item.avgResponseTime }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSystemData } from '@/services/modules/system'
import RiskBadge from '@/components/RiskBadge.vue'

const loading = ref(true)
const data = ref(null)

function riskColor(level) {
  const map = { 低: '#10b981', 中: '#f59e0b', 高: '#f97316', 严重: '#ef4444' }
  return map[level] || '#6b7280'
}

onMounted(async () => {
  try {
    const res = await getSystemData()
    if (res.code === 200) {
      data.value = res.data
    }
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.stat-cards {
  .stat-item {
    text-align: center;
    padding: 8px 0;

    .stat-num {
      display: block;
      font-size: 28px;
      font-weight: 700;
      color: $primary;
    }

    .stat-label {
      display: block;
      font-size: 13px;
      color: $text-secondary;
      margin-top: 4px;
    }
  }
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-left: 10px;
  border-left: 3px solid $primary;
}

.type-row, .risk-row, .month-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.type-name, .risk-name {
  width: 80px;
  font-size: 13px;
  flex-shrink: 0;
}

.type-count, .risk-count {
  width: 50px;
  font-size: 13px;
  font-weight: 500;
  text-align: right;
  flex-shrink: 0;
}

.month-label {
  width: 70px;
  font-size: 12px;
  flex-shrink: 0;
}

.month-response {
  width: 90px;
  font-size: 12px;
  color: $text-light;
  text-align: right;
  flex-shrink: 0;
}

.el-progress {
  flex: 1;
}
</style>
