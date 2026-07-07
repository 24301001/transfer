<template>
  <div class="task-list-page">
    <div class="page-header">
      <h2>清障任务</h2>
      <p>查看被分配的清障救援任务，更新处置状态</p>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <span class="stat-num">{{ pendingCount }}</span>
            <span class="stat-label">待处理任务</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <span class="stat-num">{{ inProgressCount }}</span>
            <span class="stat-label">处理中</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <span class="stat-num">{{ completedCount }}</span>
            <span class="stat-label">已完成</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <span class="stat-num">{{ totalCount }}</span>
            <span class="stat-label">总任务数</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务卡片列表 -->
    <div class="task-grid" v-loading="loading">
      <div v-for="task in taskList" :key="task.id" class="task-card" @click="goToDetail(task.id)">
        <div class="task-header">
          <RiskBadge :level="task.riskLevel" size="small" />
          <el-tag :type="taskStatusType(task.status)" size="small" effect="plain">{{ task.status }}</el-tag>
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
const pendingCount = computed(() => taskList.value.filter((t) => t.status === '待接收' || t.status === '已出发').length)
const inProgressCount = computed(() => taskList.value.filter((t) => t.status === '已到达' || t.status === '处理中').length)
const completedCount = computed(() => taskList.value.filter((t) => t.status === '已完成').length)

function taskStatusType(status) {
  const map = { 待接收: 'info', 已出发: 'warning', 已到达: 'success', 处理中: 'primary', 已完成: 'success' }
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

.stat-cards {
  margin-bottom: 16px;

  .stat-card {
    --el-card-padding: 14px;
  }

  .stat-inner {
    display: flex;
    align-items: center;
    gap: 12px;

    .stat-num {
      font-size: 28px;
      font-weight: 700;
      color: $primary;
      line-height: 1;
    }

    .stat-label {
      font-size: 13px;
      color: $text-secondary;
    }
  }
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.task-card {
  background: $bg-white;
  border-radius: $radius-lg;
  padding: 18px;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid $border;

  &:hover {
    box-shadow: $shadow-md;
    border-color: $primary-lighter;
    transform: translateY(-2px);
  }

  .task-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .task-body {
    h4 {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 8px;
    }

    .task-location {
      font-size: 13px;
      color: $text-secondary;
      display: flex;
      align-items: center;
      gap: 4px;
      margin-bottom: 8px;
    }

    .task-meta {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      font-size: 12px;
      color: $text-light;
    }
  }

  .task-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid $border-light;

    .task-time {
      font-size: 12px;
      color: $text-light;
    }
  }
}
</style>
