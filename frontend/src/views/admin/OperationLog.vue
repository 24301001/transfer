<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>操作日志</h2>
      <p>查看系统所有操作记录和审计追踪</p>
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
        <el-select v-model="filterModule" placeholder="模块" clearable size="small" style="width:120px">
          <el-option label="认证模块" value="认证模块" />
          <el-option label="事故模块" value="事故模块" />
          <el-option label="调度模块" value="调度模块" />
          <el-option label="系统管理" value="系统管理" />
        </el-select>
        <el-button size="small" icon="Refresh" @click="fetchLogs">刷新</el-button>
      </div>

      <el-table :data="logs" stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="time" label="时间" width="160" />
        <el-table-column prop="user" label="操作人" width="80" />
        <el-table-column prop="module" label="模块" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="120" />
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
import { ref, onMounted } from 'vue'
import { getSystemLogs } from '@/services/modules/system'
import { Refresh } from '@element-plus/icons-vue'

const logs = ref([])
const page = ref(1)
const total = ref(0)
const dateRange = ref([])
const filterModule = ref('')

async function fetchLogs() {
  try {
    const res = await getSystemLogs({ page: page.value, pageSize: 20 })
    if (res.code === 200) {
      logs.value = res.data.list
      total.value = res.data.total
    }
  } catch {}
}

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
