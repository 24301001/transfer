<template>
  <div
    class="task-detail-page"
    v-loading="loading"
  >
    <div class="back-bar">
      <el-button
        text
        @click="router.push('/rescue/tasks')"
      >
        <el-icon>
          <ArrowLeft />
        </el-icon>

        返回任务列表
      </el-button>
    </div>

    <div
      v-if="task"
      class="detail-content"
    >
      <el-row :gutter="16">
        <!-- 基本信息 -->
        <el-col :span="16" class="left-col">
          <div class="page-card left-card">
            <div class="detail-header">
              <div>
                <h2>
                  调度任务 #{{ task.id }}
                </h2>

                <div class="header-meta">
                  <span>
                    事故编号：{{ task.caseNo }}
                  </span>

                  <span>
                    创建时间：{{ task.createTime }}
                  </span>
                </div>
              </div>

              <div class="header-tags">
                <RiskBadge
                  :level="task.riskLevel"
                  size="large"
                />

                <el-tag
                  :type="statusType"
                  effect="dark"
                  size="large"
                >
                  {{ task.status }}
                </el-tag>
              </div>
            </div>

            <el-divider />

            <el-descriptions
              :column="2"
              border
            >
              <el-descriptions-item
                label="事故类型"
                :span="2"
              >
                {{ task.accidentType }}
              </el-descriptions-item>

              <el-descriptions-item
                label="事故地点"
                :span="2"
              >
                <el-icon>
                  <LocationFilled />
                </el-icon>

                {{ task.location?.name || '未知' }}
              </el-descriptions-item>

              <el-descriptions-item label="指派人员">
                {{ task.assignedTo }}
              </el-descriptions-item>

              <el-descriptions-item label="指派车辆">
                {{ task.vehicleType }}
                （{{ task.vehiclePlate }}）
              </el-descriptions-item>

              <el-descriptions-item
                label="备注说明"
                :span="2"
              >
                {{ task.notes || '无' }}
              </el-descriptions-item>
            </el-descriptions>

            <!--
              地图显示事故地点。

              点击地图时不再读取浏览器定位，
              而是由后端根据当前任务绑定的车辆 ID，
              查询数据库中的车辆经纬度作为导航起点，
              再生成“救援车辆位置 → 事故地点”的导航路线。
            -->
            <div class="map-wrapper">
              <MapCard
                height="100%"
                :title="task.location?.name"
                :hint="
                  navigationLoading
                    ? '正在读取调度车辆位置…'
                    : '点击从调度车辆位置开始导航'
                "
                :markers="mapMarkers"
                :center="mapCenter"
                :zoom="15"
                @click="handleNavigate"
              />
            </div>

            <div class="navigation-tip">
              <el-icon>
                <Position />
              </el-icon>

              <span>
                点击地图后，将使用该任务所选救援车辆在数据库中的经纬度作为起点，导航至事故地点。
              </span>
            </div>

            <!-- 关联事故详情 -->
            <div
              v-if="task.accident"
              style="margin-top: 16px"
            >
              <h3 class="section-title">
                事故详情
              </h3>

              <el-descriptions
                :column="2"
                border
                size="small"
              >
                <el-descriptions-item
                  label="事故描述"
                  :span="2"
                >
                  {{ task.accident.description }}
                </el-descriptions-item>

                <el-descriptions-item label="影响车道">
                  {{ task.accident.affectedLanes }}
                </el-descriptions-item>

                <el-descriptions-item label="道路等级">
                  {{ task.accident.roadLevel }}
                </el-descriptions-item>

                <el-descriptions-item label="天气">
                  {{ task.accident.weather }}
                </el-descriptions-item>

                <el-descriptions-item label="车流量">
                  {{ task.accident.trafficFlow }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-col>

        <!-- 右侧：现场风险、处置建议、状态更新 -->
        <el-col :span="8">
          <!-- 现场风险 -->
          <div
            class="page-card"
            style="margin-bottom: 16px"
          >
            <h3 class="section-title">
              现场风险
            </h3>

            <div class="risk-display">
              <RiskBadge
                :level="task.riskLevel"
                size="large"
              />

              <div class="risk-desc">
                <p v-if="task.riskLevel === '低'">
                  常规处置，注意基本安全
                </p>

                <p v-if="task.riskLevel === '中'">
                  需注意来往车辆，放置警示标志
                </p>

                <p v-if="task.riskLevel === '高'">
                  高风险！注意漏油、起火等二次事故
                </p>

                <p v-if="task.riskLevel === '严重'">
                  严重危险！等待专业救援，切勿盲目靠近
                </p>
              </div>
            </div>
          </div>

          <!-- 清障建议 -->
          <div
            class="page-card"
            style="margin-bottom: 16px"
          >
            <h3 class="section-title">
              清障建议
            </h3>

            <el-alert
              v-if="task.accident?.disposalAdvice"
              :title="task.accident.disposalAdvice"
              type="warning"
              :closable="false"
              show-icon
            />

            <div
              v-else
              class="no-data"
            >
              暂无清障建议
            </div>
          </div>

          <!-- 状态更新 -->
          <div class="page-card">
            <h3 class="section-title">
              处置状态
            </h3>

            <div class="status-list">
              <el-steps
                direction="vertical"
                :active="stepIndex"
                space="60px"
              >
                <el-step
                  title="待接收"
                  description="等待接收任务"
                />

                <el-step
                  title="已出发"
                  description="前往事故现场"
                />

                <el-step
                  title="已到达"
                  description="到达事故位置"
                />

                <el-step
                  title="处理中"
                  description="正在进行清障作业"
                />

                <el-step
                  title="已完成"
                  description="处置完毕，恢复通行"
                />
              </el-steps>
            </div>

            <el-divider />

            <!-- 当前状态操作 -->
            <div class="status-actions">
              <template v-if="task.status === '待接收'">
                <el-button
                  type="primary"
                  size="large"
                  style="width: 100%"
                  @click="updateStatus('已出发')"
                >
                  <el-icon>
                    <Van />
                  </el-icon>

                  接收并出发
                </el-button>
              </template>

              <template v-else-if="task.status === '已出发'">
                <el-button
                  type="primary"
                  size="large"
                  style="width: 100%"
                  @click="updateStatus('已到达')"
                >
                  <el-icon>
                    <LocationFilled />
                  </el-icon>

                  确认到达
                </el-button>
              </template>

              <template v-else-if="task.status === '已到达'">
                <el-button
                  type="primary"
                  size="large"
                  style="width: 100%"
                  @click="updateStatus('处理中')"
                >
                  <el-icon>
                    <Tools />
                  </el-icon>

                  开始处理
                </el-button>
              </template>

              <template v-else-if="task.status === '处理中'">
                <el-button
                  type="success"
                  size="large"
                  style="width: 100%"
                  @click="showFeedbackDialog = true"
                >
                  <el-icon>
                    <CircleCheck />
                  </el-icon>

                  完成处置
                </el-button>
              </template>

              <template v-else-if="task.status === '已完成'">
                <el-alert
                  title="该任务已完成"
                  type="success"
                  :closable="false"
                  show-icon
                />
              </template>
            </div>

            <!-- 反馈信息 -->
            <div
              v-if="task.feedback"
              class="feedback-box"
            >
              <el-tag
                type="success"
                effect="plain"
              >
                处置反馈
              </el-tag>

              <p>
                {{ task.feedback }}
              </p>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 完成反馈弹窗 -->
    <el-dialog
      v-model="showFeedbackDialog"
      title="完成处置"
      width="450px"
    >
      <el-form :model="feedbackForm">
        <el-form-item label="处理反馈">
          <el-input
            v-model="feedbackForm.feedback"
            type="textarea"
            :rows="3"
            placeholder="请描述处置情况，如清理完成、恢复通车等"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button
          @click="showFeedbackDialog = false"
        >
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="updating"
          @click="confirmComplete"
        >
          确认完成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import {
  useRoute,
  useRouter,
} from 'vue-router'

import { ElMessage } from 'element-plus'

import {
  ArrowLeft,
  CircleCheck,
  LocationFilled,
  Position,
  Tools,
  Van,
} from '@element-plus/icons-vue'

import { useDispatchStore } from '@/stores/dispatch'

import {
  getClearanceRescueDetail,
  getDispatchDetail,
  updateDispatchStatus,
} from '@/services/modules/dispatch'

import {
  wgs84ToBd09,
} from '@/utils/location'

import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'

const route = useRoute()
const router = useRouter()
const dispatchStore = useDispatchStore()

const task = ref(null)
const loading = ref(true)
const updating = ref(false)
const navigationLoading = ref(false)
const showFeedbackDialog = ref(false)

const feedbackForm = ref({
  feedback: '',
})

const statusType = computed(() => {
  const map = {
    待接收: 'info',
    已出发: 'warning',
    已到达: 'success',
    处理中: 'primary',
    已完成: 'success',
  }

  return map[task.value?.status] || 'info'
})

const stepIndex = computed(() => {
  const map = {
    待接收: 0,
    已出发: 1,
    已到达: 2,
    处理中: 3,
    已完成: 4,
  }

  return map[task.value?.status] ?? 0
})

/**
 * 事故地点标记。
 *
 * 后端事故坐标为 WGS84，
 * MapCard 使用百度地图 BD09 坐标，
 * 因此这里只转换事故地点的展示坐标。
 */
const mapMarkers = computed(() => {
  const longitude = Number(
    task.value?.location?.lng
  )

  const latitude = Number(
    task.value?.location?.lat
  )

  if (
    !Number.isFinite(longitude) ||
    !Number.isFinite(latitude)
  ) {
    return null
  }

  const bd09 = wgs84ToBd09(
    longitude,
    latitude
  )

  return [
    {
      lng: bd09.lng,
      lat: bd09.lat,
      label:
        task.value?.location?.name ||
        '',
    },
  ]
})

/**
 * 事故地点地图中心。
 */
const mapCenter = computed(() => {
  const longitude = Number(
    task.value?.location?.lng
  )

  const latitude = Number(
    task.value?.location?.lat
  )

  if (
    !Number.isFinite(longitude) ||
    !Number.isFinite(latitude)
  ) {
    return null
  }

  const bd09 = wgs84ToBd09(
    longitude,
    latitude
  )

  return {
    lng: bd09.lng,
    lat: bd09.lat,
  }
})

async function fetchDetail() {
  loading.value = true

  try {
    const id = route.params.id
    const res =
      await getDispatchDetail(id)

    if (res.code === 200) {
      task.value = res.data
    }
  } catch (error) {
    console.error(
      '[TaskDetail] 获取任务详情失败：',
      error
    )

    ElMessage.error(
      error?.message ||
      '获取任务详情失败'
    )
  } finally {
    loading.value = false
  }
}

/**
 * 使用任务绑定车辆的数据库坐标打开导航。
 *
 * 前端不再调用浏览器定位 API，也不再向后端传入
 * 浏览器当前位置。后端会根据 taskId 找到该任务绑定的
 * emergencyVehicleId，并使用车辆表/任务快照中的经纬度
 * 作为起点，事故经纬度作为终点生成 navigationUrl。
 */
async function handleNavigate() {
  if (navigationLoading.value) {
    return
  }

  const taskId = route.params.id

  if (!taskId) {
    ElMessage.error(
      '缺少调度任务 ID，无法导航'
    )
    return
  }

  if (!task.value?.vehicleId) {
    ElMessage.warning(
      '当前任务尚未绑定救援车辆，无法生成导航路线'
    )
    return
  }

  navigationLoading.value = true

  /*
   * 点击时立即创建新窗口。
   *
   * 接口请求是异步操作。如果等待请求结束后再调用
   * window.open，部分手机浏览器会把它当作弹窗拦截。
   */
  const navigationWindow =
    window.open(
      'about:blank',
      '_blank'
    )

  const loadingMessage =
    ElMessage.info({
      message:
        '正在读取调度车辆位置并生成导航…',
      duration: 0,
    })

  try {
    /*
     * 这里只传 taskId。
     *
     * 不传 longitude、latitude，也不读取浏览器定位。
     * 后端根据任务绑定的车辆，从数据库读取车辆坐标，
     * 生成“车辆位置 → 事故地点”的 navigationUrl。
     */
    const res =
      await getClearanceRescueDetail(
        taskId
      )

    const navigationUrl =
      res.data?.navigationUrl

    if (!navigationUrl) {
      throw new Error(
        '无法生成导航路线，请确认已选择车辆，且车辆数据库经纬度完整'
      )
    }

    if (navigationWindow) {
      navigationWindow.location.replace(
        navigationUrl
      )
    } else {
      /*
       * 如果浏览器彻底拦截新窗口，
       * 则直接在当前页面打开导航。
       */
      window.location.assign(
        navigationUrl
      )
    }
  } catch (error) {
    if (
      navigationWindow &&
      !navigationWindow.closed
    ) {
      navigationWindow.close()
    }

    console.error(
      '[TaskDetail] 车辆位置导航失败：',
      error
    )

    ElMessage.error(
      error?.message ||
      '车辆位置导航失败，请检查车辆经纬度数据'
    )
  } finally {
    loadingMessage.close()
    navigationLoading.value = false
  }
}

async function updateStatus(
  newStatus
) {
  updating.value = true

  try {
    const res =
      await updateDispatchStatus({
        id: task.value.id,
        status: newStatus,
      })

    if (res.code === 200) {
      ElMessage.success(
        `状态已更新为：${newStatus}`
      )

      task.value.status = newStatus

      task.value.updateTime =
        new Date().toLocaleString(
          'zh-CN'
        )

      dispatchStore.updateTaskStatus(
        task.value.id,
        newStatus
      )
    }
  } finally {
    updating.value = false
  }
}

async function confirmComplete() {
  await updateStatus('已完成')

  if (feedbackForm.value.feedback) {
    await updateDispatchStatus({
      id: task.value.id,
      status: '已完成',
      feedback:
        feedbackForm.value.feedback,
    })

    task.value.feedback =
      feedbackForm.value.feedback

    dispatchStore.updateTaskStatus(
      task.value.id,
      '已完成',
      feedbackForm.value.feedback
    )
  }

  showFeedbackDialog.value = false
  feedbackForm.value.feedback = ''
}

onMounted(fetchDetail)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.task-detail-page {
  max-width: 1100px;
  margin: 0 auto;
}

.detail-row {
  align-items: stretch;
}

.left-col {
  display: flex;
  flex-direction: column;
}

.left-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.map-wrapper {
  flex: 1;
  min-height: 350px;
  margin-top: 12px;
  border-radius: 10px;
  overflow: hidden;
}

.back-bar {
  margin-bottom: 12px;

  .el-button {
    &:hover {
      color: $accent;
    }
  }
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  h2 {
    margin-bottom: 6px;
    color: $text-primary;
    font-family: $font-sans;
    font-size: 20px;
    font-weight: 700;
  }

  .header-meta {
    display: flex;
    gap: 16px;
    color: $text-secondary;
    font-size: 13px;
  }

  .header-tags {
    display: flex;
    flex-shrink: 0;
    gap: 8px;
  }
}

.navigation-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: $text-secondary;
  font-size: 12px;
  line-height: 1.6;

  .el-icon {
    flex-shrink: 0;
    color: $accent;
  }
}

.section-title {
  margin-bottom: 14px;
  padding-left: 12px;
  border-left: 3px solid $accent;
  color: $text-primary;
  font-family: $font-sans;
  font-size: 15px;
  font-weight: 600;
}

.risk-display {
  text-align: center;

  .risk-desc {
    margin-top: 10px;
    color: $text-secondary;
    font-size: 13px;
    line-height: 1.6;
  }
}

.no-data {
  padding: 16px;
  color: $text-light;
  font-size: 13px;
  text-align: center;
}

.status-list {
  padding: 8px 0;
}

.status-actions {
  margin: 4px 0;

  .el-button--large {
    height: 48px;
    border-radius: 12px;
    font-weight: 600;
  }
}

.feedback-box {
  margin-top: 12px;
  padding: 14px;
  border: 1px solid rgba($success, 0.12);
  border-radius: 10px;
  background: linear-gradient(
    135deg,
    rgba($success, 0.06),
    rgba($success, 0.02)
  );

  p {
    margin-top: 6px;
    color: $text-primary;
    font-size: 13px;
    line-height: 1.6;
  }
}
</style>
