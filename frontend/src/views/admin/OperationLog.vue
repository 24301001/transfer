<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>操作日志</h2>
      <p>系统管理员对用户账号的操作记录</p>
    </div>

    <div class="page-card">
      <div class="filter-bar">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="width:240px"
        />
        <el-select v-model="filterAction" placeholder="操作类型" clearable size="small" style="width:140px">
          <el-option label="编辑用户" value="UPDATE_USER" />
          <el-option label="启/禁用用户" value="UPDATE_USER_STATUS" />
          <el-option label="删除用户" value="DELETE_USER" />
        </el-select>
        <el-input v-model="searchKey" placeholder="搜索用户/操作人" size="small" style="width:180px" clearable />
        <el-button size="small" icon="Refresh" @click="fetchLogs">刷新</el-button>
      </div>

      <el-table :data="logs" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="operatorName" label="操作人" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small" effect="plain">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标用户" width="120">
          <template #default="{ row }">
            {{ row.targetUser || row.objectId || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP 地址" width="130" />
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="20"
          :total="total"
          layout="prev, pager, next"
          @current-change="fetchLogs"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { getSystemLogs } from '@/services/modules/system'
import { Refresh } from '@element-plus/icons-vue'

const logs = ref([])
const page = ref(1)
const total = ref(0)
const dateRange = ref([])
const filterAction = ref('')
const searchKey = ref('')

function actionLabel(action) {
  const map = {
    UPDATE_USER: '编辑用户',
    UPDATE_USER_STATUS: '启/禁用用户',
    DELETE_USER: '删除用户',
  }
  return map[action] || action
}

function actionTagType(action) {
  const map = {
    UPDATE_USER: '',
    UPDATE_USER_STATUS: 'warning',
    DELETE_USER: 'danger',
  }
  return map[action] || 'info'
}

async function fetchLogs() {
  try {
    const params = { page: page.value, pageSize: 20 }

    if (filterAction.value) {
      params.operationType = filterAction.value
    }
    if (searchKey.value) {
      params.keyword = searchKey.value
    }
    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0].toISOString()
      params.endTime = dateRange.value[1].toISOString()
    }

    const res = await getSystemLogs(params)
    if (res.code === 200) {
      logs.value = res.data.list || []
      total.value = res.data.total
    }
  } catch {}
}

// 搜索或筛选条件变化时回到第一页重新查询
watch([filterAction, searchKey], () => {
  page.value = 1
  fetchLogs()
})

onMounted(fetchLogs)
</script>

<style lang="scss" scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
