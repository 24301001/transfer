<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>系统健康</h2>
      <p>查看后端服务运行状态和组件依赖</p>
    </div>

    <div class="page-card">
      <div class="status-bar">
        <div class="status-tag" :class="healthClass">
          <span class="status-dot"></span>
          {{ healthLabel }}
        </div>
        <span class="check-time" v-if="healthTime">检测时间：{{ healthTime }}</span>
        <el-button size="small" :loading="loading" @click="fetchHealth" class="refresh-btn">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <div class="page-card">
      <h4 class="section-title">组件依赖</h4>
      <el-table :data="dependencyList" stripe>
        <el-table-column label="组件" prop="name" width="200" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.value && !row.value.includes('placeholder') ? 'success' : 'warning'" size="small">
              {{ row.value && !row.value.includes('placeholder') ? '已连接' : '未配置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="详情" prop="value" />
      </el-table>
    </div>

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
import { Refresh } from '@element-plus/icons-vue'

const loading = ref(false)
const healthStatus = ref('')
const healthTime = ref('')
const dependencies = ref({})

const healthClass = computed(() => ({
  'is-up': healthStatus.value === 'UP',
  'is-down': healthStatus.value && healthStatus.value !== 'UP',
}))

const healthLabel = computed(() => {
  if (!healthStatus.value) return '检测中...'
  return healthStatus.value === 'UP' ? '服务正常' : '服务异常'
})

const dependencyList = computed(() =>
  Object.entries(dependencies.value).map(([key, value]) => ({
    name: key,
    value: value || '未知',
  }))
)

async function fetchHealth() {
  loading.value = true
  try {
    const res = await getSystemHealth()
    if (res.code === 200) {
      healthStatus.value = res.data.status
      healthTime.value = res.data.time
      dependencies.value = res.data.dependencies
    }
  } catch {
    healthStatus.value = 'DOWN'
    healthTime.value = new Date().toLocaleString('zh-CN')
    dependencies.value = { error: '无法连接到后端服务' }
  } finally {
    loading.value = false
  }
}

onMounted(fetchHealth)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;

  .status-tag {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 20px;
    border-radius: 20px;
    font-size: 14px;
    font-weight: 600;

    &.is-up {
      background: linear-gradient(135deg, rgba($success, 0.10), rgba($success, 0.04));
      color: $success;
      border: 1px solid rgba($success, 0.15);
    }

    &.is-down {
      background: linear-gradient(135deg, rgba($danger, 0.10), rgba($danger, 0.04));
      color: $danger;
      border: 1px solid rgba($danger, 0.15);
    }

    .status-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: currentColor;
      animation: pulse-dot 2s ease-in-out infinite;

      @keyframes pulse-dot {
        0%, 100% { opacity: 0.5; transform: scale(1); }
        50% { opacity: 1; transform: scale(1.3); }
      }
    }
  }

  .check-time {
    font-size: 12px;
    color: $text-light;
    font-family: $font-mono;
  }

  .refresh-btn {
    margin-left: auto;
  }
}

.section-title {
  font-family: $font-sans;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 14px;
  color: $text-primary;
}

.hint-card {
  margin-top: 16px;
}
</style>
