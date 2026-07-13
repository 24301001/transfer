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
        <el-col :span="16">
          <div class="page-card">
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
              地图仍然显示事故地点。

              点击地图时：
              1. 获取清障人员当前位置
              2. 将当前位置发送给后端
              3. 使用后端返回的 navigationUrl 打开导航
            -->
            <MapCard
              :height="'200px'"
              :title="task.location?.name"
              :hint="
                navigationLoading
                  ? '正在获取当前位置…'
                  : '点击自动定位并导航'
              "
              :markers="mapMarkers"
              :center="mapCenter"
              :zoom="15"
              style="margin-top: 12px"
              @click="handleNavigate"
            />

            <div class="navigation-tip">
              <el-icon>
                <Position />
              </el-icon>

              <span>
                点击地图后将自动获取当前位置，并导航至事故地点。
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
  getRealCurrentPosition,
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
 * 自动定位并打开导航。
 *
 * 原来的事故地点和导航逻辑全部保留，
 * 这里只是在请求导航链接之前增加：
 *
 * 1. 获取清障人员当前 GPS 坐标；
 * 2. 将当前位置传给后端；
 * 3. 后端生成当前位置到事故地点的 navigationUrl；
 * 4. 打开导航页面。
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

  navigationLoading.value = true

  /*
   * 点击时立即创建新窗口。
   *
   * 定位和接口请求都是异步操作。如果等待它们完成以后
   * 再调用 window.open，部分手机浏览器会拦截导航窗口。
   */
  const navigationWindow =
    window.open(
      'about:blank',
      '_blank'
    )

  const loadingMessage =
    ElMessage.info({
      message:
        '正在获取当前位置并生成导航…',
      duration: 0,
    })

  try {
    /*
     * 项目现有函数。
     *
     * navigator.geolocation 返回的是
     * WGS84 GPS 坐标，无需转换成 BD09 后再传后端。
     */
    const currentPosition =
      await getRealCurrentPosition()

    const res =
      await getClearanceRescueDetail(
        taskId,
        {
          lng: currentPosition.lng,
          lat: currentPosition.lat,
          coordinateType: 'WGS84',
        }
      )

    const navigationUrl =
      res.data?.navigationUrl

    if (!navigationUrl) {
      throw new Error(
        '后端未返回导航链接'
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
      '[TaskDetail] 自动定位导航失败：',
      error
    )

    ElMessage.error(
      error?.message ||
      '自动定位导航失败，请检查定位权限'
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
