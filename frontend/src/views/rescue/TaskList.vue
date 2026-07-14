<template>
  <div class="task-list-page">
    <div class="page-header">
      <h2>清障任务</h2>
      <p>查看被分配的清障救援任务，更新处置状态</p>
    </div>

    <el-row :gutter="12" class="stat-cards">
      <el-col
        v-for="card in statCards"
        :key="card.key"
        :xs="12"
        :sm="12"
        :md="6"
      >
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <span class="stat-num">{{ card.value }}</span>
            <span class="stat-label">{{ card.label }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="task-grid" v-loading="loading">
      <div
        v-for="task in taskList"
        :key="task.id"
        class="task-card"
        @click="goToDetail(task.id)"
      >
        <div class="task-header">
          <RiskBadge :level="task.riskLevel" size="small" />
          <el-tag :type="taskStatusType(task.status)" size="small" effect="plain">
            {{ task.status }}
          </el-tag>
        </div>
        <div class="task-body">
          <h4>{{ task.accidentType }}</h4>
          <p class="task-location">
            <el-icon><LocationFilled /></el-icon>
            {{ task.location?.name || '未知地点' }}
          </p>
          <div class="task-meta">
            <span>指派：{{ task.assignedTo }}</span>
            <span>车辆：{{ task.vehicleType }}（{{ task.vehiclePlate }}）</span>
          </div>
        </div>
        <div class="task-footer">
          <span class="task-time">{{ task.createTime }}</span>
          <el-tag v-if="task.feedback" size="small" type="success">{{ task.feedback }}</el-tag>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && taskList.length === 0" description="暂无任务" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDispatchStore } from '@/stores/dispatch'
import { getDispatchList } from '@/services/modules/dispatch'
import RiskBadge from '@/components/RiskBadge.vue'
import { LocationFilled } from '@element-plus/icons-vue'

const router = useRouter()
const dispatchStore = useDispatchStore()

const taskList = ref([])
const loading = ref(true)

const totalCount = computed(() => taskList.value.length)
const pendingCount = computed(() =>
  taskList.value.filter((t) => t.status === '待接收' || t.status === '已出发').length
)
const inProgressCount = computed(() =>
  taskList.value.filter((t) => t.status === '已到达' || t.status === '处理中').length
)
const completedCount = computed(() =>
  taskList.value.filter((t) => t.status === '已完成').length
)

const statCards = computed(() => [
  { key: 'pending', value: pendingCount.value, label: '待处理任务' },
  { key: 'progress', value: inProgressCount.value, label: '处理中' },
  { key: 'done', value: completedCount.value, label: '已完成' },
  { key: 'total', value: totalCount.value, label: '总任务数' },
])

function taskStatusType(status) {
  const map = {
    '待接收': 'info',
    '已出发': 'warning',
    '已到达': 'success',
    '处理中': 'primary',
    '已完成': 'success',
  }
  return map[status] || 'info'
}

function goToDetail(id) {
  router.push(`/rescue/task/${id}`)
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = await getDispatchList()
    if (res.code === 200) {
      taskList.value = res.data.list
      dispatchStore.setTasks(res.data.list)
    }
  } finally {
    loading.value = false
  }
}

let pollTimer = null
onMounted(() => {
  fetchTasks()
  pollTimer = setInterval(fetchTasks, 5000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.task-list-page {
  width: 100%;
  min-width: 0;
}

.stat-cards {
  margin-bottom: 16px;

  .el-col {
    display: flex;
    margin-bottom: 12px;
  }

  .stat-card {
    --el-card-padding: 0;
    width: 100%;
    height: 100%;

    :deep(.el-card__body) {
      height: 100%;
      padding: 0;
    }
  }

  .el-card {
    border-radius: 12px;
    overflow: hidden;
  }

  .stat-inner {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: center;
    gap: 6px;
    min-height: 88px;
    padding: 16px 18px;
    box-sizing: border-box;
  }

  .stat-num {
    font-family: $font-mono;
    font-size: 28px;
    font-weight: 700;
    color: $accent;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.02em;
  }

  .stat-label {
    font-size: 12px;
    color: $text-secondary;
    font-weight: 500;
    line-height: 1.3;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  gap: 16px;
}

.task-card {
  background: $bg-white;
  border-radius: 14px;
  padding: 20px;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: all 0.25s ease-out;
  border: 1px solid $border;
  min-width: 0;

  &:hover {
    box-shadow: $shadow-lg;
    border-color: rgba($accent, 0.25);
    transform: translateY(-3px);
  }

  .task-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .task-body {
    h4 {
      font-size: 16px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 8px;
      word-break: break-word;
    }

    .task-location {
      font-size: 13px;
      color: $text-secondary;
      display: flex;
      align-items: flex-start;
      gap: 4px;
      margin-bottom: 8px;
      word-break: break-all;

      .el-icon {
        color: $accent;
        font-size: 14px;
        margin-top: 2px;
        flex-shrink: 0;
      }
    }

    .task-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 12px;
      font-size: 12px;
      color: $text-light;
    }
  }

  .task-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px solid $border-light;

    .task-time {
      font-size: 12px;
      color: $text-light;
      font-variant-numeric: tabular-nums;
    }
  }
}

@media (max-width: 768px) {
  .stat-cards {
    .stat-inner {
      min-height: 78px;
      padding: 14px;
      align-items: center;
      text-align: center;
    }

    .stat-num { font-size: 24px; }

    .stat-label {
      font-size: 11px;
      white-space: normal;
      text-align: center;
    }
  }

  .task-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .task-card {
    padding: 16px;
    border-radius: 12px;

    &:hover { transform: none; }

    .task-body h4 { font-size: 15px; }
  }
}

@media (max-width: 380px) {
  .stat-cards .stat-label { font-size: 10px; }
}
</style>
