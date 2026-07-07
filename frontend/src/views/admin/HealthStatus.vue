<template>
  <div class="admin-page" v-loading="loading">
    <div class="page-header">
      <h2>系统健康状态</h2>
      <p>监控系统各服务运行状态和关键指标</p>
    </div>

    <!-- 系统概览 -->
    <el-row :gutter="16" class="stat-cards" v-if="health">
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.totalAccidents }}</div>
            <div class="mini-label">事故总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.todayAccidents }}</div>
            <div class="mini-label">今日事故</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.pendingTasks }}</div>
            <div class="mini-label">待处理任务</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.activeUsers }}</div>
            <div class="mini-label">在线用户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.cpuUsage }}</div>
            <div class="mini-label">CPU</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" :body-style="{ padding: '16px' }">
          <div class="mini-stat">
            <div class="mini-num">{{ health.stats.memoryUsage }}</div>
            <div class="mini-label">内存</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务列表 -->
    <div class="page-card" style="margin-top:16px;">
      <div class="card-header">
        <h3>服务状态</h3>
        <el-tag v-if="health" :type="health.overall === 'healthy' ? 'success' : 'danger'" size="small" effect="dark">
          {{ health.overall === 'healthy' ? '● 全部正常' : '● 存在异常' }}
        </el-tag>
      </div>

      <el-table :data="health?.services || []" stripe>
        <el-table-column prop="name" label="服务名称" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'healthy' ? 'success' : 'danger'" size="small">
              <el-icon style="margin-right:3px;">
                <CircleCheckFilled v-if="row.status === 'healthy'" />
                <WarningFilled v-else />
              </el-icon>
              {{ row.status === 'healthy' ? '正常' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uptime" label="运行时长" width="160" />
        <el-table-column prop="responseTime" label="响应时间" width="120" />
        <el-table-column label="操作" width="100">
          <template #default>
            <el-button size="small" text>查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 健康提示 -->
    <el-alert
      v-if="hasDegraded"
      title="部分服务存在异常，请及时检查处理"
      type="warning"
      show-icon
      style="margin-top:16px;"
      :closable="false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSystemHealth } from '@/services/modules/system'
import { CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'

const loading = ref(true)
const health = ref(null)

const hasDegraded = computed(() =>
  health.value?.services?.some((s) => s.status !== 'healthy')
)

onMounted(async () => {
  try {
    const res = await getSystemHealth()
    if (res.code === 200) {
      health.value = res.data
    }
  } finally {
    loading.value = false
  }
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.stat-cards {
  .mini-stat {
    text-align: center;

    .mini-num {
      font-size: 22px;
      font-weight: 700;
      color: $primary;
    }

    .mini-label {
      font-size: 12px;
      color: $text-secondary;
      margin-top: 4px;
    }
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
</style>
