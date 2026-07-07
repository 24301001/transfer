<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>事故历史</h2>
      <p>查看和检索所有历史事故记录</p>
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
        <el-select v-model="filterType" placeholder="事故类型" clearable size="small" style="width:130px">
          <el-option label="追尾事故" value="追尾事故" />
          <el-option label="车辆碰撞" value="车辆碰撞" />
          <el-option label="道路封闭" value="道路封闭" />
          <el-option label="施工占道" value="施工占道" />
        </el-select>
        <el-input v-model="searchKey" placeholder="搜索地点/编号" size="small" style="width:180px" clearable />
        <el-button size="small" type="primary" @click="handleExport">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>

      <el-table :data="accidentStore.accidentList" stripe @row-click="goToDetail" style="cursor:pointer;">
        <el-table-column prop="caseNo" label="编号" width="130" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column label="风险等级" width="80">
          <template #default="{ row }">
            <RiskBadge :level="row.riskLevel" size="small" v-if="row.riskLevel !== '-'" />
            <span v-else style="color:#9ca3af;">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="location?.name" label="地点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="location?.area" label="区域" width="90" />
        <el-table-column prop="reporter" label="上报人" width="80" />
        <el-table-column prop="reportTime" label="上报时间" width="150" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="accidentStore.accidentList.length === 0" description="暂无数据" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccidentStore } from '@/stores/accident'
import { getAccidentList } from '@/services/modules/accident'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import RiskBadge from '@/components/RiskBadge.vue'

const router = useRouter()
const accidentStore = useAccidentStore()

const dateRange = ref([])
const filterType = ref('')
const searchKey = ref('')

function statusType(status) {
  const map = { 待处理: 'danger', 处理中: 'warning', 已处理: 'success', 已结案: 'info' }
  return map[status] || 'info'
}

function goToDetail(row) {
  router.push(`/command/accident/${row.id}`)
}

function handleExport() {
  ElMessage.success('数据导出中...')
}

onMounted(async () => {
  if (accidentStore.accidentList.length === 0) {
    const res = await getAccidentList()
    if (res.code === 200) {
      accidentStore.setAccidents(res.data.list)
    }
  }
})
</script>

<style lang="scss" scoped>
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
