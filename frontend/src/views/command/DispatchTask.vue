<template>
  <div class="dispatch-page">
    <div class="page-header">
      <div>
        <h2>车辆 ETA 调度</h2>
        <p>
          按事故位置计算可用车辆到达时间，由指挥人员手动选择救护车或清障车
        </p>
      </div>

      <el-button
        :loading="pageLoading"
        @click="refreshAll"
      >
        <el-icon>
          <Refresh />
        </el-icon>
        刷新数据
      </el-button>
    </div>

    <div class="dispatch-grid">
      <section class="page-card dispatch-form-card">
        <h3 class="section-title">
          调度条件
        </h3>

        <el-form label-position="top">
          <el-form-item
            label="关联事故"
            required
          >
            <el-select
              v-model="form.incidentId"
              placeholder="请选择事故"
              filterable
              style="width: 100%"
              @change="handleConditionChange"
            >
              <el-option
                v-for="incident in incidentOptions"
                :key="incident.id"
                :value="incident.id"
                :label="incidentOptionLabel(incident)"
              >
                <div class="incident-option">
                  <span class="incident-option__main">
                    {{ incident.caseNo }}
                    ·
                    {{ incident.type }}
                  </span>

                  <RiskBadge
                    :level="incident.riskLevel"
                    size="small"
                  />
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item
            label="车辆类型"
            required
          >
            <el-radio-group
              v-model="form.vehicleType"
              @change="handleConditionChange"
            >
              <el-radio-button value="AMBULANCE">
                救护车
              </el-radio-button>

              <el-radio-button value="CLEARANCE_TRUCK">
                清障车
              </el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="任务接收人">
            <el-select
              v-model="form.receiverUserId"
              placeholder="可选：选择清障/救援人员"
              clearable
              filterable
              style="width: 100%"
            >
              <el-option
                v-for="user in responders"
                :key="user.id"
                :value="user.id"
                :label="`${user.fullName}（${user.username}）`"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="调度说明">
            <el-input
              v-model="form.advice"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="例如：优先抢救伤员、注意现场拥堵、从东侧入口进入"
            />
          </el-form-item>

          <el-button
            type="primary"
            :loading="etaLoading"
            :disabled="!canQueryEta"
            @click="loadEtas"
          >
            <el-icon>
              <Timer />
            </el-icon>
            查询车辆到达时间
          </el-button>
        </el-form>
      </section>

      <section class="page-card incident-card">
        <h3 class="section-title">
          事故摘要
        </h3>

        <el-empty
          v-if="!selectedIncident"
          description="请先选择关联事故"
          :image-size="90"
        />

        <template v-else>
          <div class="incident-title-row">
            <div>
              <div class="case-no">
                {{ selectedIncident.caseNo }}
              </div>

              <h3>
                {{ selectedIncident.type }}
              </h3>
            </div>

            <RiskBadge
              :level="selectedIncident.riskLevel"
              size="large"
            />
          </div>

          <el-descriptions
            :column="1"
            border
          >
            <el-descriptions-item label="事故位置">
              {{ selectedIncident.locationName }}
            </el-descriptions-item>

            <el-descriptions-item label="当前状态">
              {{ selectedIncident.status }}
            </el-descriptions-item>

            <el-descriptions-item label="预计拥堵">
              {{
                durationText(
                  selectedIncident.congestionMinutes
                )
              }}
            </el-descriptions-item>

            <el-descriptions-item label="预计恢复">
              {{
                durationText(
                  selectedIncident.recoveryMinutes
                )
              }}
            </el-descriptions-item>

            <el-descriptions-item label="支援判断">
              <el-tag
                :type="
                  selectedIncident.supportRequired
                    ? 'danger'
                    : 'success'
                "
                effect="plain"
              >
                {{
                  selectedIncident.supportRequired
                    ? '需要支援'
                    : '暂不需要支援'
                }}
              </el-tag>

              <div
                v-if="selectedIncident.supportReason"
                class="support-reason"
              >
                {{ selectedIncident.supportReason }}
              </div>
            </el-descriptions-item>
          </el-descriptions>

          <el-button
            text
            type="primary"
            class="detail-link"
            @click="
              openIncidentDetail(
                selectedIncident.id
              )
            "
          >
            查看事故完整详情

            <el-icon>
              <ArrowRight />
            </el-icon>
          </el-button>
        </template>
      </section>
    </div>

    <section class="page-card eta-card">
      <div class="card-header">
        <div>
          <h3>
            可调度车辆与 ETA
          </h3>

          <p>
            列表按预计到达时间升序排列；“最快”仅作参考，最终由指挥人员决定
          </p>
        </div>

        <el-tag
          v-if="etaList.length"
          type="info"
          effect="plain"
        >
          共 {{ etaList.length }} 辆可用车辆
        </el-tag>
      </div>

      <el-table
        v-loading="etaLoading"
        :data="etaList"
        stripe
        highlight-current-row
        empty-text="选择事故和车辆类型后查询 ETA"
        @current-change="handleVehicleSelect"
      >
        <el-table-column
          label="选择"
          width="68"
          align="center"
        >
          <template #default="{ row }">
            <el-radio
              v-model="form.vehicleId"
              :value="row.vehicleId"
              aria-label="选择车辆"
            >
              <span />
            </el-radio>
          </template>
        </el-table-column>

        <el-table-column
          label="车辆"
          min-width="180"
        >
          <template #default="{ row }">
            <div class="vehicle-cell">
              <div class="vehicle-name">
                {{
                  row.vehicleName ||
                  row.vehicleTypeLabel
                }}

                <el-tag
                  v-if="row.fastest"
                  size="small"
                  type="success"
                  effect="dark"
                >
                  最快
                </el-tag>
              </div>

              <span>
                {{ row.vehicleNo }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          prop="statusLabel"
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              type="success"
              size="small"
              effect="plain"
            >
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          label="距离"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            {{
              numberText(
                row.distanceKm,
                'km'
              )
            }}
          </template>
        </el-table-column>

        <el-table-column
          label="参考速度"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            {{
              numberText(
                row.speedKmh,
                'km/h'
              )
            }}
          </template>
        </el-table-column>

        <el-table-column
          label="预计到达"
          width="130"
          align="center"
        >
          <template #default="{ row }">
            <span class="eta-value">
              {{
                row.estimatedArrivalMinutes ??
                '-'
              }}
            </span>

            <span
              v-if="
                row.estimatedArrivalMinutes != null
              "
            >
              分钟
            </span>
          </template>
        </el-table-column>

        <el-table-column
          prop="message"
          label="说明"
          min-width="210"
        />
      </el-table>

      <div class="dispatch-actions">
        <div class="selected-vehicle-tip">
          <template v-if="selectedVehicle">
            已选择：

            <strong>
              {{ selectedVehicle.vehicleNo }}
            </strong>

            ，预计
            {{
              selectedVehicle.estimatedArrivalMinutes
            }}
            分钟到达
          </template>

          <template v-else>
            请在表格中选择一辆车
          </template>
        </div>

        <el-button
          type="danger"
          size="large"
          :loading="dispatching"
          :disabled="!canDispatch"
          @click="handleDispatch"
        >
          <el-icon>
            <Promotion />
          </el-icon>

          确认调度所选车辆
        </el-button>
      </div>
    </section>

    <section class="page-card task-card">
      <div class="card-header">
        <div>
          <h3>
            最近调度任务
          </h3>

          <p>
            包含普通调度任务和通过 ETA 选车创建的车辆任务
          </p>
        </div>

        <el-select
          v-model="taskFilterStatus"
          placeholder="任务状态"
          clearable
          size="small"
          style="width: 140px"
          @change="loadTasks"
        >
          <el-option
            label="待接收"
            value="待接收"
          />

          <el-option
            label="已出发"
            value="已出发"
          />

          <el-option
            label="已到达"
            value="已到达"
          />

          <el-option
            label="处理中"
            value="处理中"
          />

          <el-option
            label="已完成"
            value="已完成"
          />
        </el-select>
      </div>

      <el-table
        v-loading="taskLoading"
        :data="taskList"
        stripe
      >
        <el-table-column
          prop="taskNo"
          label="任务编号"
          min-width="170"
        />

        <el-table-column
          prop="accidentId"
          label="事故 ID"
          width="90"
        />

        <el-table-column
          prop="vehicleType"
          label="任务/车辆类型"
          width="120"
        />

        <el-table-column
          label="执行车辆"
          min-width="160"
        >
          <template #default="{ row }">
            <template v-if="row.vehiclePlate">
              <div>
                {{ row.vehicleName || '-' }}
              </div>

              <span class="secondary-text">
                {{ row.vehiclePlate }}
              </span>
            </template>

            <span v-else>
              -
            </span>
          </template>
        </el-table-column>

        <el-table-column
          label="ETA"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            {{
              row.estimatedArrivalMinutes == null
                ? '-'
                : `${row.estimatedArrivalMinutes} 分钟`
            }}
          </template>
        </el-table-column>

        <el-table-column
          prop="status"
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="taskStatusType(row.status)"
              size="small"
              effect="plain"
            >
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column
          prop="createTime"
          label="创建时间"
          width="170"
        />

        <el-table-column
          label="操作"
          width="110"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="
                openIncidentDetail(
                  row.accidentId
                )
              "
            >
              事故详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import { useRouter } from 'vue-router'

import {
  ElMessage,
  ElMessageBox,
} from 'element-plus'

import {
  ArrowRight,
  Promotion,
  Refresh,
  Timer,
} from '@element-plus/icons-vue'

import RiskBadge from '@/components/RiskBadge.vue'

import { useUserStore } from '@/stores/user'

import {
  dispatchSelectedVehicle,
  getCommandIncidents,
  getDispatchList,
  getResponders,
  getVehicleEtas,
} from '@/services/modules/dispatch'

const router = useRouter()
const userStore = useUserStore()

const pageLoading = ref(false)
const etaLoading = ref(false)
const taskLoading = ref(false)
const dispatching = ref(false)

const incidents = ref([])
const responders = ref([])
const etaList = ref([])
const taskList = ref([])

const taskFilterStatus = ref('')

const form = ref({
  incidentId: null,
  vehicleType: 'AMBULANCE',
  vehicleId: null,
  receiverUserId: null,
  advice: '',
})

const incidentOptions = computed(() =>
  incidents.value.filter(
    (item) =>
      ![
        'CLEARED',
        'CLOSED',
      ].includes(item.statusCode)
  )
)

const selectedIncident = computed(() =>
  incidents.value.find(
    (item) =>
      item.id === form.value.incidentId
  ) || null
)

const selectedVehicle = computed(() =>
  etaList.value.find(
    (item) =>
      item.vehicleId === form.value.vehicleId
  ) || null
)

const canQueryEta = computed(() =>
  Boolean(
    form.value.incidentId &&
    form.value.vehicleType
  )
)

const canDispatch = computed(() =>
  Boolean(
    canQueryEta.value &&
    form.value.vehicleId &&
    userStore.userInfo?.id
  )
)

function incidentOptionLabel(incident) {
  return (
    `[${incident.caseNo}] ` +
    `${incident.type} - ` +
    `${incident.locationName}`
  )
}

function durationText(value) {
  return value == null
    ? '暂无预测'
    : `${value} 分钟`
}

function numberText(
  value,
  unit
) {
  if (
    value == null ||
    Number.isNaN(Number(value))
  ) {
    return '-'
  }

  return `${Number(value).toFixed(1)} ${unit}`
}

function taskStatusType(status) {
  const map = {
    待接收: 'info',
    已出发: 'warning',
    已到达: 'success',
    处理中: 'primary',
    已完成: 'success',
    已取消: 'danger',
  }

  return map[status] || 'info'
}

function handleConditionChange() {
  etaList.value = []
  form.value.vehicleId = null
}

function handleVehicleSelect(row) {
  if (row?.vehicleId) {
    form.value.vehicleId =
      row.vehicleId
  }
}

function openIncidentDetail(incidentId) {
  if (incidentId) {
    router.push(
      `/command/accident/${incidentId}`
    )
  }
}

async function loadIncidents() {
  const res = await getCommandIncidents({
    page: 1,
    pageSize: 100,
  })

  incidents.value =
    res.data.list
}

async function loadResponders() {
  const res = await getResponders(
    'RESCUE_WORKER'
  )

  responders.value =
    res.data
}

async function loadTasks() {
  taskLoading.value = true

  try {
    const res = await getDispatchList({
      page: 1,
      pageSize: 100,

      status:
        taskFilterStatus.value ||
        undefined,
    })

    taskList.value =
      res.data.list

  } finally {
    taskLoading.value = false
  }
}

async function loadEtas() {
  if (!canQueryEta.value) {
    ElMessage.warning(
      '请先选择事故和车辆类型'
    )

    return
  }

  etaLoading.value = true
  form.value.vehicleId = null

  try {
    const res = await getVehicleEtas(
      form.value.incidentId,
      form.value.vehicleType
    )

    etaList.value =
      res.data

    if (!etaList.value.length) {
      ElMessage.warning(
        '当前没有具备坐标且状态为可调度的车辆'
      )

      return
    }

    /*
     * 默认勾选最快车辆，但最终仍需指挥人员确认。
     */
    const fastest =
      etaList.value.find(
        (item) => item.fastest
      )

    if (fastest) {
      form.value.vehicleId =
        fastest.vehicleId
    }

  } finally {
    etaLoading.value = false
  }
}

async function handleDispatch() {
  if (
    !canDispatch.value ||
    !selectedVehicle.value
  ) {
    ElMessage.warning(
      '请选择要调度的车辆'
    )

    return
  }

  const incident =
    selectedIncident.value

  const vehicle =
    selectedVehicle.value

  try {
    await ElMessageBox.confirm(
      `确认调度 ${vehicle.vehicleNo} ` +
        `前往事故 ${incident.caseNo}？` +
        `预计 ${vehicle.estimatedArrivalMinutes} 分钟到达。`,

      '确认车辆调度',

      {
        confirmButtonText: '确认调度',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch {
    return
  }

  dispatching.value = true

  try {
    await dispatchSelectedVehicle(
      form.value.incidentId,

      {
        vehicleType:
          form.value.vehicleType,

        vehicleId:
          form.value.vehicleId,

        receiverUserId:
          form.value.receiverUserId,

        assignedByUserId:
          userStore.userInfo.id,

        advice:
          form.value.advice,
      }
    )

    ElMessage.success(
      '车辆调度成功'
    )

    form.value.vehicleId = null
    form.value.advice = ''
    etaList.value = []

    await Promise.all([
      loadIncidents(),
      loadTasks(),
    ])

  } finally {
    dispatching.value = false
  }
}

async function refreshAll() {
  pageLoading.value = true

  try {
    await Promise.all([
      loadIncidents(),
      loadResponders(),
      loadTasks(),
    ])

    if (canQueryEta.value) {
      await loadEtas()
    }

  } finally {
    pageLoading.value = false
  }
}

onMounted(refreshAll)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.dispatch-page {
  padding-bottom: 28px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;

  h2 {
    margin: 0 0 6px;
    font-size: 24px;
    color: $text-primary;
  }

  p {
    margin: 0;
    color: $text-secondary;
  }
}

.dispatch-grid {
  display: grid;
  grid-template-columns:
    minmax(0, 1fr)
    minmax(360px, 0.9fr);
  gap: 18px;
  margin-bottom: 18px;
}

.page-card {
  margin-bottom: 18px;
}

.dispatch-grid .page-card {
  margin-bottom: 0;
}

.section-title {
  margin: 0 0 18px;
  padding-left: 12px;
  border-left: 3px solid $accent;
  font-size: 16px;
  font-weight: 600;
  color: $text-primary;
}

.incident-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.incident-option__main {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.incident-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  h3 {
    margin: 5px 0 0;
    color: $text-primary;
  }
}

.case-no,
.secondary-text {
  color: $text-secondary;
  font-size: 13px;
}

.support-reason {
  margin-top: 8px;
  color: $text-secondary;
  line-height: 1.6;
}

.detail-link {
  margin-top: 12px;
  padding-left: 0;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h3 {
    margin: 0 0 5px;
    font-size: 17px;
    color: $text-primary;
  }

  p {
    margin: 0;
    color: $text-secondary;
    font-size: 13px;
  }
}

.vehicle-cell {
  .vehicle-name {
    display: flex;
    align-items: center;
    gap: 8px;
    color: $text-primary;
    font-weight: 600;
  }

  > span {
    display: block;
    margin-top: 3px;
    color: $text-secondary;
    font-size: 13px;
  }
}

.eta-value {
  font-size: 20px;
  font-weight: 700;
  color: $accent;
}

.dispatch-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid $border;
}

.selected-vehicle-tip {
  color: $text-secondary;

  strong {
    color: $text-primary;
  }
}

@media (max-width: 960px) {
  .dispatch-grid {
    grid-template-columns: 1fr;
  }

  .page-header,
  .dispatch-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
