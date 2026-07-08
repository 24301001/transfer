<template>
  <div class="dispatch-page">
    <div class="page-header">
      <h2>调度处理</h2>
      <p>创建和查看调度任务，派警、清障车或救援人员前往现场</p>
    </div>

    <!-- 创建调度任务 -->
    <div class="page-card" v-if="showCreateForm">
      <h3 class="section-title">创建调度任务</h3>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="关联事故" prop="accidentId">
          <el-select v-model="form.accidentId" placeholder="选择需要调度的事故" filterable style="width:100%">
            <el-option
              v-for="a in pendingAccidents"
              :key="a.id"
              :label="`[${a.caseNo}] ${a.type} - ${a.location?.name}`"
              :value="a.id"
            >
              <span>{{ a.caseNo }} - {{ a.type }}</span>
              <RiskBadge :level="a.riskLevel" size="small" style="margin-left:8px" />
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="调度人员" prop="assignedTo">
          <el-select v-model="form.assignedTo" placeholder="选择清障/救援人员" filterable style="width:100%">
            <el-option
              v-for="u in rescueUsers"
              :key="u.id"
              :label="u.nickname"
              :value="u.nickname"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="车辆类型" prop="vehicleType">
          <el-select v-model="form.vehicleType" placeholder="选择车辆" style="width:100%">
            <el-option label="清障车" value="清障车" />
            <el-option label="救护车" value="救护车" />
            <el-option label="警车" value="警车" />
            <el-option label="工程车" value="工程车" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="备注信息，如注意事项、特殊要求等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            创建任务
          </el-button>
          <el-button @click="showCreateForm = false">取消</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 切换按钮（不在创建状态时显示） -->
    <div v-if="!showCreateForm" style="margin-bottom:16px;">
      <el-button type="primary" @click="showCreateForm = true">
        <el-icon><Plus /></el-icon>
        新建调度任务
      </el-button>
    </div>

    <!-- 任务列表 -->
    <div class="page-card">
      <div class="card-header">
        <h3>调度任务列表</h3>
        <div class="filter-bar">
          <el-select v-model="filterStatus" placeholder="状态" clearable size="small" style="width:130px" @change="fetchTasks">
            <el-option label="待接收" value="待接收" />
            <el-option label="已出发" value="已出发" />
            <el-option label="已到达" value="已到达" />
            <el-option label="处理中" value="处理中" />
            <el-option label="已完成" value="已完成" />
          </el-select>
          <el-button size="small" icon="Refresh" @click="fetchTasks">刷新</el-button>
        </div>
      </div>

      <el-table :data="taskList" stripe>
        <el-table-column prop="id" label="编号" width="60" />
        <el-table-column prop="caseNo" label="事故编号" width="120" />
        <el-table-column prop="accidentType" label="事故类型" width="100" />
        <el-table-column label="风险等级" width="80">
          <template #default="{ row }">
            <RiskBadge :level="row.riskLevel" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="assignedTo" label="指派人员" width="90" />
        <el-table-column prop="vehicleType" label="车辆" width="80" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="150" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="router.push(`/rescue/task/${row.id}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAccidentStore } from '@/stores/accident'
import { useDispatchStore } from '@/stores/dispatch'
import { getDispatchList, createDispatch } from '@/services/modules/dispatch'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import RiskBadge from '@/components/RiskBadge.vue'

const router = useRouter()
const accidentStore = useAccidentStore()
const dispatchStore = useDispatchStore()

const formRef = ref(null)
const showCreateForm = ref(false)
const creating = ref(false)
const filterStatus = ref('')
const taskList = ref([])

const form = ref({
  accidentId: '',
  assignedTo: '',
  vehicleType: '',
  notes: '',
})

const rules = {
  accidentId: [{ required: true, message: '请选择关联事故', trigger: 'change' }],
  assignedTo: [{ required: true, message: '请选择调度人员', trigger: 'change' }],
  vehicleType: [{ required: true, message: '请选择车辆类型', trigger: 'change' }],
}

// 待调度的事故（未关联调度任务或状态未完成的）
const pendingAccidents = ref([])
const rescueUsers = ref([
  { id: 1, nickname: '王队长' },
  { id: 2, nickname: '陈师傅' },
  { id: 3, nickname: '刘师傅' },
  { id: 4, nickname: '张师傅' },
])

function taskStatusType(status) {
  const map = { 待接收: 'info', 已出发: 'warning', 已到达: 'success', 处理中: 'primary', 已完成: 'success' }
  return map[status] || 'info'
}

async function fetchTasks() {
  try {
    const res = await getDispatchList({ status: filterStatus.value || undefined })
    if (res.code === 200) {
      taskList.value = res.data.list
      dispatchStore.setTasks(res.data.list)
    }
  } catch {}
}

async function handleCreate() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    const res = await createDispatch({
      accidentId: form.value.accidentId,
      assignedTo: form.value.assignedTo,
      vehicleType: form.value.vehicleType,
      notes: form.value.notes,
      assigner: '李指挥',
      accidentType: accidentStore.getAccidentById(form.value.accidentId)?.type || '',
      location: accidentStore.getAccidentById(form.value.accidentId)?.location || {},
      riskLevel: accidentStore.getAccidentById(form.value.accidentId)?.riskLevel || '',
    })
    if (res.code === 200) {
      ElMessage.success('调度任务创建成功')
      showCreateForm.value = false
      form.value = { accidentId: '', assignedTo: '', vehicleType: '', notes: '' }
      fetchTasks()
    }
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  fetchTasks()
  // 获取待调度事故
  pendingAccidents.value = accidentStore.accidentList.filter((a) =>
    a.status !== '已处理' && a.status !== '已结案'
  )
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.section-title {
  font-family: $font-sans;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-left: 12px;
  border-left: 3px solid $accent;
  color: $text-primary;
}

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
  }
}

.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
