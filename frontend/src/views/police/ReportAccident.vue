<template>
  <div class="report-page">
    <div class="page-header">
      <h2>事故上报</h2>
      <p>请填写事故现场信息并提交，系统将自动进行事故识别与分析</p>
    </div>

    <div class="page-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" size="large">
        <!-- 事故照片 -->
        <el-form-item label="事故照片" prop="images">
          <PhotoUploader v-model="form.images" />
        </el-form-item>

        <!-- 现场视频 -->
        <el-form-item label="现场视频">
          <div class="video-recorder">
            <div v-if="!selectedVideo && !recording" class="video-placeholder">
              <div class="video-choice">
                <div class="video-choice-item">
                  <el-icon :size="40"><VideoCamera /></el-icon>
                  <p>拍摄现场视频</p>
                  <el-button type="primary" @click="startRecording">
                    <el-icon><VideoCamera /></el-icon>
                    开始录制
                  </el-button>
                </div>
                <div class="video-choice-divider"></div>
                <div class="video-choice-item">
                  <el-icon :size="40"><Upload /></el-icon>
                  <p>上传已有视频</p>
                  <el-upload
                    :auto-upload="false"
                    :show-file-list="false"
                    accept="video/mp4,video/webm,video/quicktime,video/*"
                    :on-change="handleVideoUpload"
                  >
                    <el-button>
                      <el-icon><Upload /></el-icon>
                      选择视频
                    </el-button>
                  </el-upload>
                </div>
              </div>
            </div>

            <div v-if="recording" class="video-recording">
              <div class="recording-indicator">
                <span class="rec-dot"></span>
                <span class="rec-timer">{{ formatTime(recordElapsed) }} / 20秒</span>
              </div>
              <el-progress
                :percentage="(recordElapsed / 20) * 100"
                :stroke-width="6"
                :color="recordElapsed >= 15 ? '#ef4444' : '#3b82f6'"
              />
              <el-button
                type="danger"
                @click="stopRecording"
                :disabled="recordElapsed < 1"
                style="margin-top:10px;"
              >
                <el-icon><VideoPause /></el-icon>
                停止录制
              </el-button>
            </div>

            <div v-if="selectedVideo && !recording" class="video-preview">
              <video
                ref="videoRef"
                :src="videoUrl"
                controls
                class="preview-video"
              ></video>
              <div class="video-info">
                <span class="video-size">{{ selectedVideo.name }}</span>
                <span class="video-size">{{ formatSize(selectedVideo.raw.size) }}</span>
                <span class="video-duration">{{ selectedVideo.source === 'record' ? '拍摄视频' : '上传视频' }}</span>
              </div>
              <div class="video-actions">
                <el-button type="primary" icon="VideoCamera" @click="startRecording">
                  重新录制
                </el-button>
                <el-button icon="Delete" @click="removeVideo">删除视频</el-button>
              </div>
            </div>

            <video
              ref="cameraRef"
              style="display:none;"
              muted
              playsinline
            ></video>
          </div>
        </el-form-item>

        <!-- 事故地点 -->
        <el-form-item label="事故地点" prop="locationStr">
          <div class="location-picker">
            <el-input
              v-model="form.customLocation"
              placeholder="手动输入详细位置，或使用自动定位"
              class="location-input"
              @input="onCustomLocationInput"
            />
            <el-button @click="handleAutoLocate" :loading="locating" type="default">
              <el-icon><Aim /></el-icon>
              自动定位
            </el-button>
          </div>

          <!-- 位置已选提示 -->
          <div v-if="form.locationStr" class="location-result">
            <el-icon><LocationFilled /></el-icon>
            <span class="loc-coords" v-if="form.locationLat">
              {{ form.locationLat.toFixed(4) }}, {{ form.locationLng.toFixed(4) }}
            </span>
            {{ form.locationStr }}
          </div>

          <!-- 百度地图（选取模式） -->
          <div class="report-map-wrapper">
            <MapCard
              ref="mapCardRef"
              :height="'280px'"
              picker-mode
              :markers="mapMarkers"
              :center="mapCenter"
              :zoom="15"
              @location-select="onMapLocationSelect"
              @location-confirm="onMapLocationConfirm"
            />
            <p class="map-tip" v-if="!form.locationStr">
              <el-icon><InfoFilled /></el-icon>
              点击地图上的位置选择事故地点，或使用「自动定位」按钮
            </p>
          </div>
        </el-form-item>

        <!-- 场景识别标签（照片/视频自动提取后支持人工复核） -->
        <el-form-item label="场景识别">
          <div class="scene-recognition-field">
            <el-checkbox-group v-model="form.sceneLabels" class="scene-labels">
              <el-checkbox
                v-for="label in SCENE_LABEL_OPTIONS"
                :key="label"
                :label="label"
                :value="label"
              />
            </el-checkbox-group>
            <p class="scene-tip">
              {{ recognizedSceneLabels.length ? '已根据上传照片/视频自动勾选，可人工复核修改' : '可先人工勾选，提交后系统会自动补充识别标签' }}
            </p>
          </div>
        </el-form-item>

        <el-form-item v-if="extraSceneLabels.length" label="其他标签">
          <div class="scene-extra-tags">
            <el-tag
              v-for="label in extraSceneLabels"
              :key="label"
              size="small"
              effect="plain"
            >
              {{ label }}
            </el-tag>
          </div>
        </el-form-item>

        <el-form-item v-if="aiDetectedType" label="AI识别">
          <el-tag type="success" effect="dark">{{ aiDetectedType }}</el-tag>
        </el-form-item>

        <!-- 算法2结构化输入 -->
        <el-form-item label="道路信息">
          <div class="structured-grid">
            <el-input-number
              v-model="form.occupiedLanes"
              :min="0"
              :max="12"
              controls-position="right"
              placeholder="占用车道"
            />
            <el-select v-model="form.roadLevel" clearable placeholder="道路等级">
              <el-option label="高速" value="高速" />
              <el-option label="快速路" value="快速路" />
              <el-option label="主干路" value="主干路" />
              <el-option label="次干路" value="次干路" />
              <el-option label="支路" value="支路" />
            </el-select>
            <el-select v-model="form.roadStatus" clearable placeholder="路面状况">
              <el-option label="正常" value="正常" />
              <el-option label="湿滑" value="湿滑" />
              <el-option label="积水" value="积水" />
              <el-option label="结冰" value="结冰" />
              <el-option label="施工" value="施工" />
            </el-select>
          </div>
        </el-form-item>

        <el-form-item label="现场流量">
          <div class="structured-grid">
            <!--
              车流量：
              拥堵存储数字 1
              畅通存储数字 0
            -->
            <el-select
              v-model="form.trafficFlow"
              clearable
              placeholder="请选择车流量"
            >
              <el-option
                label="拥堵（1）"
                :value="1"
              />

              <el-option
                label="畅通（0）"
                :value="0"
              />
            </el-select>

            <!--
              人流量：
              拥挤存储数字 1
              不拥挤存储数字 0
            -->
            <el-select
              v-model="form.peopleFlow"
              clearable
              placeholder="请选择人流量"
            >
              <el-option
                label="拥挤（1）"
                :value="1"
              />

              <el-option
                label="不拥挤（0）"
                :value="0"
              />
            </el-select>
            <div class="weather-field">
              <el-select
                v-model="form.weather"
                clearable
                filterable
                allow-create
                default-first-option
                placeholder="定位后自动获取天气"
                :loading="weatherLoading"
              >
                <!--
                  百度可能返回“小雨、阵雨、雷阵雨”等动态值。
                  当返回值不在预设列表中时，保证下拉框仍能正常显示。
                -->
                <el-option
                  v-if="
                    form.weather &&
                    !WEATHER_OPTIONS.includes(form.weather)
                  "
                  :label="form.weather"
                  :value="form.weather"
                />

                <el-option
                  v-for="item in WEATHER_OPTIONS"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>

              <div
                v-if="weatherLoading"
                class="weather-status is-loading"
              >
                <el-icon class="is-loading">
                  <Loading />
                </el-icon>

                正在获取事故地点实时天气…
              </div>

              <div
                v-else-if="weatherDetail"
                class="weather-status"
              >
                <span class="weather-main">
                  {{ weatherDetail.text || form.weather }}
                  <template
                    v-if="
                      weatherDetail.temperatureC !== null &&
                      weatherDetail.temperatureC !== undefined
                    "
                  >
                    · {{ weatherDetail.temperatureC }}℃
                  </template>
                </span>

                <span
                  v-if="weatherDetail.humidityPercent !== null &&
                    weatherDetail.humidityPercent !== undefined"
                >
                  湿度 {{ weatherDetail.humidityPercent }}%
                </span>

                <span
                  v-if="
                    weatherDetail.windDirection ||
                    weatherDetail.windClass
                  "
                >
                  {{ weatherDetail.windDirection || '' }}
                  {{ weatherDetail.windClass || '' }}
                </span>

                <span
                  v-if="weatherDetail.city || weatherDetail.district"
                >
                  {{ weatherDetail.city || '' }}
                  {{ weatherDetail.district || '' }}
                </span>
              </div>

              <div
                v-else-if="weatherError"
                class="weather-status is-error"
              >
                {{ weatherError }}，可手动选择天气
              </div>
            </div>
          </div>
        </el-form-item>

        <!-- 旧数据兼容：事故类型由 AI 标签自动写入，不再让用户手动选择 -->
        <el-form-item v-if="false" label="事故类型" prop="accidentType">
          <div class="type-picker">
            <el-select v-model="form.accidentType" clearable style="width:320px">
              <el-option
                v-for="t in ACCIDENT_TYPE_OPTIONS"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
          </div>
        </el-form-item>

        <!-- 人员受伤（现场人员手动填写） -->
        <el-form-item label="是否受伤">
          <el-switch
            v-model="form.injuryReported"
            active-text="有人受伤"
            inactive-text="无人受伤"
            @change="onInjuryReportedChange"
          />
        </el-form-item>
        <el-form-item label="涉及人数">
          <el-input-number v-model="form.peopleInvolved" :min="0" placeholder="0" style="width:160px" />
        </el-form-item>
        <el-form-item v-if="form.injuryReported" label="受伤人数">
          <el-input-number v-model="form.injuredCount" :min="0" placeholder="0" style="width:160px" />
        </el-form-item>
        <el-form-item v-if="form.injuryReported" label="伤情描述">
          <el-input
            v-model="form.injuryEstimate"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="请描述受伤情况（如有）"
          />
        </el-form-item>

        <!-- 事故描述 -->
        <el-form-item label="事故描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请描述事故情况，如追尾、剐蹭、占用车道、是否有人受伤等"
          />
        </el-form-item>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit" size="large" class="submit-btn">
            <el-icon><Upload /></el-icon>
            {{ submitting ? '提交中...' : '提交事故' }}
          </el-button>
          <el-button @click="handleReset" size="large">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 已提交记录操作区 -->
    <div v-if="lastSubmission" class="page-card" style="margin-top:16px;">
      <div class="card-header">
        <h3>最近提交</h3>
        <el-tag size="small">{{ lastSubmission.caseNo }}</el-tag>
      </div>
      <div class="submission-summary">
        <div class="summary-item">
          <span class="summary-label">事故地点</span>
          <span class="summary-value">{{ lastSubmission.location?.name || form.locationStr }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">提交时间</span>
          <span class="summary-value">{{ lastSubmission.reportTime }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">识别状态</span>
          <span v-if="result" class="summary-value success">已完成</span>
          <span v-else class="summary-value pending">分析中...</span>
        </div>
        <div class="summary-item" v-if="recognizedSceneLabels.length">
          <span class="summary-label">场景标签</span>
          <span class="summary-value">{{ recognizedSceneLabels.join('、') }}</span>
        </div>
      </div>
      <div class="submission-actions">
        <el-button type="primary" icon="View" @click="openResultDialog">
          查看提交结果
        </el-button>
        <el-button icon="Refresh" @click="handleReset">重新上报</el-button>
      </div>
    </div>

    <!-- 提交结果弹窗（两阶段：即时建议 → 轮询预测结果） -->
    <el-dialog
      v-model="resultVisible"
      title="事故提交结果"
      width="660px"
      top="5vh"
      destroy-on-close
      class="public-report-dialog"
      @closed="stopPredictionPolling"
    >
      <div class="result-container">
        <!-- 成功横幅 -->
        <div class="result-success-banner">
          <el-icon :size="20"><CircleCheckFilled /></el-icon>
          事故已成功提交至指挥中心，编号：{{ submissionIncidentNo }}
        </div>

        <!-- ── 第一阶段：即时安全提示 ── -->
        <div v-if="publicReportMeta?.immediateAdvice" class="result-section advice-section">
          <h4>
            <el-icon style="vertical-align:-3px;"><WarningFilled /></el-icon>
            即时安全建议
          </h4>
          <div class="advice-card">
            <p class="calming-msg">{{ publicReportMeta.immediateAdvice.calmingMessage }}</p>
            <p class="advice-text">{{ publicReportMeta.immediateAdvice.immediateAdvice }}</p>
            <ul v-if="publicReportMeta.immediateAdvice.actionItems.length > 0" class="action-list">
              <li v-for="(item, idx) in publicReportMeta.immediateAdvice.actionItems" :key="idx">
                {{ item }}
              </li>
            </ul>
            <el-alert
              v-if="publicReportMeta.immediateAdvice.call120Required"
              title="检测到可能的人员受伤，请立即拨打 120 急救电话"
              type="danger"
              :closable="false"
              show-icon
              style="margin-top:10px;"
            />
          </div>
        </div>

        <!-- 预计交警到达 -->
        <div v-if="publicReportMeta?.estimatedPoliceArrivalText" class="result-section arrival-section">
          <h4>
            <el-icon style="vertical-align:-3px;"><Van /></el-icon>
            预计交警到达
          </h4>
          <el-alert
            :title="publicReportMeta.estimatedPoliceArrivalText"
            type="info"
            :closable="false"
            show-icon
          />
        </div>

        <el-divider />

        <!-- ── 第二阶段：智能预测结果 ── -->
        <div class="result-section prediction-section">
          <h4>
            <el-icon style="vertical-align:-3px;"><DataAnalysis /></el-icon>
            智能预测结果
            <el-tag
              v-if="predictionStatus === 'PROCESSING'"
              type="warning"
              size="small"
              style="margin-left:8px;"
            >
              分析中
            </el-tag>
            <el-tag
              v-else-if="predictionStatus === 'COMPLETED'"
              type="success"
              size="small"
              style="margin-left:8px;"
            >
              已完成
            </el-tag>
            <el-tag
              v-else-if="predictionStatus === 'FAILED'"
              type="danger"
              size="small"
              style="margin-left:8px;"
            >
              异常
            </el-tag>
          </h4>

          <!-- 处理中 -->
          <div v-if="predictionStatus === 'PROCESSING'" class="prediction-loading">
            <el-icon class="is-loading" :size="20"><Loading /></el-icon>
            <span>{{ predictionMessage }}</span>
            <el-progress
              :percentage="100"
              :show-text="false"
              :indeterminate="true"
              :stroke-width="4"
              style="margin-top:10px;"
            />
          </div>

          <!-- 已完成 -->
          <el-descriptions
            v-else-if="predictionStatus === 'COMPLETED' && predictionResult"
            :column="2"
            border
            size="small"
          >
            <el-descriptions-item label="场景类别">
              <template v-if="recognizedSceneLabels.length">
                <el-tag
                  v-for="label in recognizedSceneLabels"
                  :key="label"
                  size="small"
                  effect="plain"
                  style="margin-right:4px;"
                >{{ label }}</el-tag>
              </template>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <RiskBadge :level="predictionResult.riskLevel" size="small" />
            </el-descriptions-item>
            <el-descriptions-item label="预计恢复">
              {{ predictionResult.recoveryDurationMinutes != null ? predictionResult.recoveryDurationMinutes + ' 分钟' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="处置建议" :span="2">
              {{ predictionResult.suggestions || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="预测说明" :span="2">
              {{ predictionResult.explanation || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 异常 -->
          <el-alert
            v-else-if="predictionStatus === 'FAILED'"
            :title="predictionMessage"
            type="warning"
            show-icon
            :closable="false"
          />
        </div>

      </div>

      <template #footer>
        <el-button type="primary" @click="resultVisible = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAccidentStore } from '@/stores/accident'
import { useAiChatContext } from '@/composables/useAiChatContext'
import { publicReport, publicReportWithAttachments, getPredictionStatus } from '@/services/modules/accident'
import { reverseGeocode } from '@/services/modules/map'
import { getCurrentWeather } from '@/services/modules/weather'
import { getRealCurrentPosition, getBaiduIPLocation, bd09ToWgs84, wgs84ToBd09 } from '@/utils/location'
import { ElMessage } from 'element-plus'
import {
  Upload, Aim, LocationFilled, Loading, View,
  WarningFilled, VideoCamera, VideoPause, Delete, InfoFilled,
  CircleCheckFilled, Van, DataAnalysis,
} from '@element-plus/icons-vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'

const userStore = useUserStore()
const accidentStore = useAccidentStore()
const formRef = ref(null)
const mapCardRef = ref(null)
const submitting = ref(false)
const locating = ref(false)
const resultVisible = ref(false)
const result = ref(null)
const lastSubmission = ref(null)
const aiDetectedType = ref('') // AI 识别到的事故类型
const submissionIncidentNo = ref('') // 提交后的事故编号

/** 公共上报返回的元数据（即时提示、预计到达、预测提交状态） */
const publicReportMeta = ref(null)
/** 提交成功后显示绿色提示 */
const submitSucceeded = ref(false)

// ── 预测轮询状态 ──
const predictionStatus = ref('') // 'PROCESSING' | 'COMPLETED' | 'FAILED'
const predictionMessage = ref('')
const predictionResult = ref(null)
let predictionTimer = null
let predictionAttempts = 0
const predictionTrackingToken = ref('') // 匿名查询令牌

// ====== 表单数据（必须在 AI 聊天上下文之前定义，避免 TDZ）======
const form = reactive({
  images: [],
  video: null,
  customLocation: '',
  locationStr: '',
  locationName: '',   // 地点名称
  locationLat: null,  // WGS84 绾害
  locationLng: null,  // WGS84 经度
  accidentType: '',
  sceneLabels: [],
  description: '',
  occupiedLanes: null,
  trafficFlow: null,
  peopleFlow: null,
  weather: '',
  roadLevel: '',
  roadStatus: '',
  peopleInvolved: null,
  injuredCount: null,
  injuryEstimate: '',
  injuryReported: false,
})

const rules = {
  locationStr: [{ required: true, message: '请在地图上选择事故地点', trigger: 'change' }],
  description: [{ required: true, message: '请输入事故描述', trigger: 'blur' }],
  accidentType: [{ required: false }],
}

// ====== AI 聊天上下文 ======
const { setContext, resetContext } = useAiChatContext()

function syncChatContext() {
  setContext({
    incidentId: lastSubmission.value?.id || null,
    locationName: form.locationName || form.locationStr || '',
    description: form.description || '',
    accidentType: form.accidentType || '',
  })
}

onMounted(() => {
  syncChatContext()
})

watch(
  () => [form.locationName, form.description, form.accidentType, lastSubmission.value?.id],
  syncChatContext,
  { deep: true }
)

// 地图中心（BD09 坐标系，用于传给 MapCard）
const mapCenter = ref(null)

// ====== 实时天气 ======

/**
 * 天气接口加载状态。
 */
const weatherLoading = ref(false)

/**
 * 后端返回的完整天气信息。
 */
const weatherDetail = ref(null)

/**
 * 天气接口错误信息。
 */
const weatherError = ref('')

/**
 * 天气下拉框预设选项。
 *
 * 接口返回其他天气名称时，
 * el-select 的 allow-create 仍然可以正常显示。
 */
const WEATHER_OPTIONS = [
  '晴',
  '多云',
  '阴',
  '阵雨',
  '雷阵雨',
  '雷阵雨伴有冰雹',
  '小雨',
  '中雨',
  '大雨',
  '暴雨',
  '大暴雨',
  '特大暴雨',
  '雨夹雪',
  '阵雪',
  '小雪',
  '中雪',
  '大雪',
  '暴雪',
  '雾',
  '浓雾',
  '强浓雾',
  '霾',
  '浮尘',
  '扬沙',
  '沙尘暴',
  '强沙尘暴',
]

/**
 * 天气请求序号。
 *
 * 防止连续点击不同位置时，
 * 较早请求返回的数据覆盖最后选择地点的数据。
 */
let weatherRequestSequence = 0

/**
 * 地图点击后查询天气的防抖定时器。
 */
let weatherQueryTimer = null

const SCENE_LABEL_OPTIONS = ['car flip', 'car crash', 'car damage', 'fire/smoke']

const recognizedSceneLabels = computed(() => {
  if (result.value?.sceneLabels?.length) return result.value.sceneLabels
  if (form.sceneLabels.length) return form.sceneLabels
  if (!aiDetectedType.value) return []
  return aiDetectedType.value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
})

const extraSceneLabels = computed(() =>
  recognizedSceneLabels.value.filter((label) => !SCENE_LABEL_OPTIONS.includes(label))
)

// 事故类型预设
const ACCIDENT_TYPE_OPTIONS = [
  '追尾事故', '正面碰撞', '侧面刮擦', '车辆侧翻',
  '撞固定物', '车辆自燃', '货物散落', '其他',
]

// ====== 视频录制状态 ======
const recording = ref(false)
const videoBlob = ref(null)
const uploadedVideoFile = ref(null)
const videoUrl = ref('')
const recordElapsed = ref(0)
let mediaRecorder = null
let mediaStream = null
let recordTimer = null
const cameraRef = ref(null)
const videoRef = ref(null)

const selectedVideo = computed(() => {
  if (uploadedVideoFile.value) return uploadedVideoFile.value
  if (!videoBlob.value) return null
  return {
    raw: videoBlob.value,
    name: '现场视频.webm',
    source: 'record',
  }
})

function formatTime(seconds) {
  const m = String(Math.floor(seconds / 60)).padStart(2, '0')
  const s = String(seconds % 60).padStart(2, '0')
  return `${m}:${s}`
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

async function startRecording() {
  try {
    if (videoUrl.value) {
      URL.revokeObjectURL(videoUrl.value)
      videoUrl.value = ''
    }
    uploadedVideoFile.value = null
    videoBlob.value = null

    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: true,
    })

    if (cameraRef.value) {
      cameraRef.value.srcObject = mediaStream
    }

    const chunks = []
    mediaRecorder = new MediaRecorder(mediaStream, {
      mimeType: MediaRecorder.isTypeSupported('video/webm;codecs=vp9,opus')
        ? 'video/webm;codecs=vp9,opus'
        : MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus')
          ? 'video/webm;codecs=vp8,opus'
          : 'video/webm',
    })

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data)
    }

    mediaRecorder.onstop = () => {
      const blob = new Blob(chunks, { type: 'video/webm' })
      videoBlob.value = blob
      videoUrl.value = URL.createObjectURL(blob)
      if (mediaStream) {
        mediaStream.getTracks().forEach((t) => t.stop())
        mediaStream = null
      }
      recording.value = false
      clearInterval(recordTimer)
      recordTimer = null
      ElMessage.success('视频录制完成')
    }

    recordElapsed.value = 0
    recording.value = true
    mediaRecorder.start()

    recordTimer = setInterval(() => {
      recordElapsed.value++
      if (recordElapsed.value >= 20) {
        stopRecording()
      }
    }, 1000)
  } catch (err) {
    console.error('摄像头启动失败', err)
    ElMessage.error('无法访问摄像头，请检查权限设置')
    recording.value = false
  }
}

function handleVideoUpload(uploadFile) {
  const file = uploadFile.raw
  if (!file) return
  if (!file.type?.startsWith('video/')) {
    ElMessage.warning('请选择视频文件')
    return
  }
  if (videoUrl.value) {
    URL.revokeObjectURL(videoUrl.value)
  }
  videoBlob.value = null
  uploadedVideoFile.value = {
    raw: file,
    name: file.name,
    source: 'upload',
  }
  videoUrl.value = URL.createObjectURL(file)
  ElMessage.success('视频已选择')
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop()
  }
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
}

function removeVideo() {
  if (videoUrl.value) {
    URL.revokeObjectURL(videoUrl.value)
  }
  videoBlob.value = null
  uploadedVideoFile.value = null
  videoUrl.value = ''
  videoRef.value = null
}

function onInjuryReportedChange(value) {
  if (!value) {
    form.injuredCount = 0
    form.injuryEstimate = ''
  } else if (!form.injuredCount || form.injuredCount < 1) {
    form.injuredCount = 1
  }
}

/** 地图标记，给 MapCard 传入 BD09 坐标（百度地图使用） */
const mapMarkers = computed(() => {
  if (!form.locationLat || !form.locationLng) return null
  // 将表单中存的 WGS84 转为 BD09 在地图上显示
  const bd09 = wgs84ToBd09(form.locationLng, form.locationLat)
  return [{
    lng: bd09.lng,
    lat: bd09.lat,
    label: form.locationStr,
  }]
})

/** 手动输入地址 */
function onCustomLocationInput(val) {
  if (val) {
    form.locationStr = val
    form.locationName = val
    // 不自动清除坐标，用户可能已经在地图上选了点
  }
}

/**
 * 根据事故地点经纬度获取实时天气，
 * 并自动填写 form.weather。
 *
 * @param {number} longitude WGS84 经度
 * @param {number} latitude WGS84 纬度
 * @param {{ silent?: boolean }} options 查询配置
 */
async function fillWeatherByLocation(
  longitude,
  latitude,
  options = {}
) {
  const lng = Number(longitude)
  const lat = Number(latitude)

  if (
    !Number.isFinite(lng) ||
    !Number.isFinite(lat)
  ) {
    return
  }

  const currentSequence =
    ++weatherRequestSequence

  weatherLoading.value = true
  weatherError.value = ''

  try {
    /*
     * ReportAccident 表单中的 locationLng/locationLat
     * 已经转换成 WGS84，因此这里明确传 WGS84。
     */
    const weather =
      await getCurrentWeather(
        lng,
        lat,
        'WGS84'
      )

    /*
     * 如果用户已经选择了其他地点，
     * 忽略之前位置返回的旧天气。
     */
    if (
      currentSequence !==
      weatherRequestSequence
    ) {
      return
    }

    weatherDetail.value = weather

    /*
     * 使用百度返回的天气原文，
     * 例如：晴、小雨、阵雨、雷阵雨。
     */
    if (weather?.text) {
      form.weather =
        String(weather.text).trim()
    }

    if (!options.silent) {
      const temperature =
        weather?.temperatureC !== null &&
        weather?.temperatureC !== undefined
          ? `，${weather.temperatureC}℃`
          : ''

      ElMessage.success(
        `已自动获取天气：${
          weather?.text || '未知'
        }${temperature}`
      )
    }
  } catch (error) {
    if (
      currentSequence !==
      weatherRequestSequence
    ) {
      return
    }

    console.warn(
      '[ReportAccident] 获取实时天气失败：',
      error
    )

    weatherDetail.value = null
    weatherError.value =
      error?.response?.data?.message ||
      error?.message ||
      '实时天气获取失败'

    /*
     * 天气接口失败不能阻止事故地点选择，
     * 用户仍可手动选择天气。
     */
    if (!options.silent) {
      ElMessage.warning(
        `${weatherError.value}，请手动选择天气`
      )
    }
  } finally {
    if (
      currentSequence ===
      weatherRequestSequence
    ) {
      weatherLoading.value = false
    }
  }
}

/**
 * 地图选点时防抖查询天气。
 *
 * 用户连续点击地图时只查询最后一次选中的位置，
 * 减少百度天气接口调用次数。
 */
function scheduleWeatherQuery(
  longitude,
  latitude
) {
  if (weatherQueryTimer) {
    window.clearTimeout(
      weatherQueryTimer
    )
  }

  weatherQueryTimer =
    window.setTimeout(() => {
      fillWeatherByLocation(
        longitude,
        latitude,
        {
          silent: true,
        }
      )
    }, 500)
}

/**
 * 清除当前天气信息。
 */
function clearWeather() {
  /*
   * 让正在执行的旧请求失效。
   */
  weatherRequestSequence += 1

  if (weatherQueryTimer) {
    window.clearTimeout(
      weatherQueryTimer
    )

    weatherQueryTimer = null
  }

  weatherLoading.value = false
  weatherDetail.value = null
  weatherError.value = ''
  form.weather = ''
}


// ====== 地图位置选取 ======
/** 用户在地图上点击选位时触发（坐标来自百度地图，为 BD09） */
/**
 * 用户在百度地图上点击选位。
 *
 * MapCard 返回 BD09 坐标；
 * 表单和后端事故数据使用 WGS84，
 * 因此先转换，再查询天气。
 */
function onMapLocationSelect(data) {
  if (
    !data ||
    !Number.isFinite(Number(data.lng)) ||
    !Number.isFinite(Number(data.lat))
  ) {
    return
  }

  const wgs84 = bd09ToWgs84(
    Number(data.lng),
    Number(data.lat)
  )

  form.locationLat = wgs84.lat
  form.locationLng = wgs84.lng

  form.locationName =
    data.address ||
    data.formattedAddress ||
    form.customLocation ||
    '地图选点'

  form.locationStr =
    data.formattedAddress ||
    data.address ||
    data.semanticDescription ||
    `${wgs84.lat.toFixed(4)}, ${wgs84.lng.toFixed(4)}`

  /*
   * 选点后自动查询该地点实时天气。
   */
  scheduleWeatherQuery(
    wgs84.lng,
    wgs84.lat
  )
}

/** 用户点击「确认」按钮 */
/**
 * 用户确认地图选点。
 */
async function onMapLocationConfirm(
  data
) {
  if (data) {
    const lng = Number(data.lng)
    const lat = Number(data.lat)

    if (
      Number.isFinite(lng) &&
      Number.isFinite(lat)
    ) {
      const wgs84 =
        bd09ToWgs84(
          lng,
          lat
        )

      form.locationLat =
        wgs84.lat

      form.locationLng =
        wgs84.lng

      form.locationName =
        data.address ||
        data.formattedAddress ||
        form.locationName

      form.locationStr =
        data.formattedAddress ||
        data.address ||
        form.locationStr

      /*
       * 取消尚未执行的防抖请求，
       * 确认位置后立即查询天气。
       */
      if (weatherQueryTimer) {
        window.clearTimeout(
          weatherQueryTimer
        )

        weatherQueryTimer = null
      }

      await fillWeatherByLocation(
        wgs84.lng,
        wgs84.lat
      )
    }
  }

  ElMessage.success(
    `已选择位置：${form.locationStr}`
  )
}


// ====== 自动定位 ======
async function handleAutoLocate() {
  locating.value = true
  try {
    let coords
    let source = 'GPS'

    try {
      coords = await getRealCurrentPosition()
    } catch (gpsErr) {
      console.warn('[ReportAccident] GPS 定位失败，尝试百度 IP 定位:', gpsErr.message)
      try {
        coords = await getBaiduIPLocation()
        source = 'IP'
      } catch (ipErr) {
        console.warn('[ReportAccident] 百度 IP 定位也失败:', ipErr.message)
        ElMessage.warning('自动定位不可用，请点击地图选择位置')
        return
      }
    }

    const { lat, lng } = coords
    let addressText = `${lat.toFixed(4)}, ${lng.toFixed(4)}`

    try {
      const BMapGL = window.BMapGL
      if (BMapGL && typeof BMapGL.Geocoder === 'function') {
        const geocoder = new BMapGL.Geocoder()
        const bd09 = wgs84ToBd09(lng, lat)
        const result = await new Promise((resolve, reject) => {
          geocoder.getLocation(new BMapGL.Point(bd09.lng, bd09.lat), (res) => {
            res && res.address ? resolve(res) : reject(new Error('无地址'))
          })
        })
        if (result.address) addressText = result.address
      } else {
        const loc = await reverseGeocode(lng, lat, 'WGS84')
        addressText = loc.formattedAddress || loc.semanticDescription || addressText
      }
    } catch (addrErr) {
      console.warn('[ReportAccident] 地址解析失败，使用坐标作为地址:', addrErr.message)
    }

    form.locationLat = lat
    form.locationLng = lng
    form.locationName = addressText
    form.locationStr = addressText
    form.customLocation = addressText

    setMapCenterToWgs84(
      lng,
      lat
    )

    /*
    * 定位成功后，立即查询事故地点的实时天气。
    */
    await fillWeatherByLocation(
      lng,
      lat
    )

    if (source === 'IP') {
      ElMessage.info(
        `IP 定位成功：${addressText}`
      )
    } else {
      ElMessage.success(
        '定位成功'
      )
    }

  } catch (err) {
    ElMessage.error(err.message || '定位失败')
  } finally {
    locating.value = false
  }
}

/**
 * 将 WGS84 坐标转换为 BD09 并设为地图中心 */
async function setMapCenterToWgs84(lng, lat) {
  const bd09 = wgs84ToBd09(lng, lat)
  mapCenter.value = { lng: bd09.lng, lat: bd09.lat }
}

function clearLocation() {
  form.locationStr = ''
  form.locationName = ''
  form.customLocation = ''
  form.locationLat = null
  form.locationLng = null

  mapCenter.value = null

  /*
   * 地点清除后，天气也应清除，
   * 防止提交上一个地点的天气。
   */
  clearWeather()
}


// ====== 提交 ======
async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const hasImage = form.images.some((img) => img.raw)
  const hasVideo = !!selectedVideo.value?.raw
  if (!hasImage && !hasVideo) {
    ElMessage.warning('请至少上传一张照片或一个视频')
    return
  }

  submitting.value = true
  try {
    const payload = {
      images: form.images,
      video: selectedVideo.value ? { raw: selectedVideo.value.raw, name: selectedVideo.value.name } : null,
      location: {
        name: form.locationName || form.locationStr,
        area: form.customLocation || form.locationName,
        road: '',
        lat: form.locationLat,
        lng: form.locationLng,
      },
      description: form.description,
      accidentType: form.accidentType || '',
      sceneLabels: form.sceneLabels,
      occupiedLanes: form.occupiedLanes,
      trafficFlow: form.trafficFlow,
      peopleFlow: form.peopleFlow,
      weather: form.weather,
      roadLevel: form.roadLevel,
      roadStatus: form.roadStatus,
      peopleInvolved: form.peopleInvolved || 0,
      injuredCount: form.injuredCount || 0,
      injuryReported: form.injuryReported,
      injuryEstimate: form.injuryEstimate || '',
      reporter: userStore.nickname,
      reporterId: userStore.userInfo?.id || 0,
      coordinateType: 'WGS84',
    }
    const res = (hasImage || hasVideo) ? await publicReportWithAttachments(payload) : await publicReport(payload)

    if (res.code === 200) {
      const data = res.data
      const detail = data.incidentDetail

      // 最近提交记录
      lastSubmission.value = {
        id: detail?.id || null,
        caseNo: detail?.caseNo || '',
        reportTime: new Date().toLocaleString('zh-CN'),
        location: form.locationName,
      }

      submissionIncidentNo.value = detail?.caseNo || ''
      result.value = detail
      aiDetectedType.value = data.aiDetectedType || detail?.sceneLabels?.join(', ') || ''
      form.sceneLabels = Array.from(new Set([
        ...form.sceneLabels,
        ...(detail?.sceneLabels || []),
      ].filter(Boolean)))

      publicReportMeta.value = {
        immediateAdvice: data.immediateAdvice,
        estimatedPoliceArrivalMinutes: data.estimatedPoliceArrivalMinutes,
        estimatedPoliceArrivalText: data.estimatedPoliceArrivalText,
        predictionSubmit: data.predictionSubmit,
      }

      if (detail?.id) {
        accidentStore.updateAccident(detail.id, detail)
      }

      // 保存 trackingToken 用于后续匿名轮询
      predictionTrackingToken.value = data.trackingToken || ''

      submitSucceeded.value = true
      resultVisible.value = true
      ElMessage.success('事故提交成功')

      // 启动预测结果轮询
      if (detail?.id && data.trackingToken) {
        startPredictionPolling(detail.id, data.trackingToken)
      }
    }
  } catch (err) {
    const message = err?.code === 'ECONNABORTED'
      ? '提交超时：后端附件识别或预测服务响应过慢，请稍后重试'
      : (err?.response?.data?.message || err?.message || '提交失败，请检查后端服务')
    ElMessage.error(message)
  } finally {
    submitting.value = false
  }
}

function handleReset() {
  removeVideo()
  form.images = []
  form.customLocation = ''
  form.locationStr = ''
  form.locationName = ''
  form.locationLat = null
  form.locationLng = null
  form.accidentType = ''
  form.sceneLabels = []
  form.description = ''
  form.occupiedLanes = null
  form.trafficFlow = null
  form.peopleFlow = null
  clearWeather()
  form.roadLevel = ''
  form.roadStatus = ''
  form.peopleInvolved = null
  form.injuredCount = null
  form.injuryEstimate = ''
  form.injuryReported = false
  aiDetectedType.value = ''
  mapCenter.value = null
  result.value = null
  resultVisible.value = false
  lastSubmission.value = null
  publicReportMeta.value = null
  submitSucceeded.value = false
  submissionIncidentNo.value = ''
  stopPredictionPolling()
  formRef.value?.resetFields()
}

function openResultDialog() {
  resultVisible.value = true
}

// ── 预测结果轮询 ──

function startPredictionPolling(incidentId, trackingToken) {
  stopPredictionPolling()

  predictionStatus.value = 'PROCESSING'
  predictionMessage.value = 'AI 正在分析事故视频，预计约 1 分钟'
  predictionResult.value = null
  predictionAttempts = 0

  predictionTimer = window.setInterval(async () => {
    predictionAttempts += 1

    try {
      const res = await getPredictionStatus(incidentId, trackingToken)

      if (res.code === 200 && res.data.completed) {
        predictionResult.value = res.data.result
        predictionStatus.value = 'COMPLETED'
        predictionMessage.value = '事故预测已完成'
        stopPredictionPolling()
        return
      }

      if (predictionAttempts >= 36) {
        predictionStatus.value = 'FAILED'
        predictionMessage.value = '预测仍在处理中，可稍后在事故记录中查看'
        stopPredictionPolling()
      }
    } catch {
      // 404 表示尚无结果，继续等待
      if (predictionAttempts >= 36) {
        predictionStatus.value = 'FAILED'
        predictionMessage.value = '预测结果获取失败，请稍后查看'
        stopPredictionPolling()
      }
    }
  }, 5000)
}

function stopPredictionPolling() {
  if (predictionTimer) {
    window.clearInterval(predictionTimer)
    predictionTimer = null
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.report-page {
  max-width: 900px;
  margin: 0 auto;
}

.location-picker {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  width: 100%;

  .location-input {
    flex: 1;
    min-width: 180px;
  }

  .el-button {
    border-radius: 10px;
    height: 36px;
  }
}

.location-result {
  margin-top: 8px;
  padding: 8px 14px;
  background: linear-gradient(135deg, rgba($accent, 0.06), rgba($accent-secondary, 0.02));
  border: 1px solid rgba($accent, 0.10);
  border-radius: 8px;
  font-size: 13px;
  color: $accent;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;

  .loc-coords {
    font-family: $font-mono;
    font-size: 11px;
    background: rgba($accent, 0.08);
    padding: 1px 6px;
    border-radius: 4px;
    color: $text-secondary;
    font-weight: 400;
  }
}

.report-map-wrapper {
  margin-top: 10px;
  position: relative;
}

.map-tip {
  margin-top: 6px;
  font-size: 12px;
  color: $text-light;
  display: flex;
  align-items: center;
  gap: 4px;

  .el-icon { font-size: 14px; color: $accent; }
}

.submit-btn {
  padding: 12px 44px;
  font-size: 15px;
  height: 50px;
  border-radius: 12px;
}

.result-container {
  .result-stat {
    text-align: center;
    padding: 16px;
    background: rgba($border-light, 0.6);
    border-radius: 10px;
    .stat-label {
      display: block;
      font-size: 12px;
      color: $text-secondary;
      margin-bottom: 6px;
    }
    .stat-value {
      font-size: 20px;
      font-weight: 700;
      color: $text-primary;
      font-variant-numeric: tabular-nums;
      &.highlight {
        color: $accent;
      }
    }
  }

  .result-section {
    margin-top: 20px;
    h4 {
      font-family: $font-sans;
      font-size: 14px;
      font-weight: 600;
      margin-bottom: 10px;
      color: $text-primary;
    }
  }
}

.result-loading {
  padding: 20px;
}

.type-picker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.scene-labels,
.result-labels :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
}

.result-labels {
  padding: 12px 14px;
  margin-bottom: 14px;
  background: rgba($border-light, 0.75);
  border-radius: 8px;

  .label-title {
    display: block;
    font-size: 12px;
    color: $text-secondary;
    margin-bottom: 8px;
    font-weight: 600;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  h3 {
    font-family: $font-sans;
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
  }
}

.submission-summary {
  display: flex;
  gap: 24px;
  padding: 12px 0;
  flex-wrap: wrap;

  .summary-item {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .summary-label {
      font-size: 12px;
      color: $text-light;
    }

    .summary-value {
      font-size: 14px;
      color: $text-primary;
      font-weight: 500;

      &.success { color: $success; }
      &.pending { color: $warning; }
    }
  }
}

.submission-actions {
  display: flex;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid $border;
  flex-wrap: wrap;
}

.video-recorder {
  width: 100%;

  .video-placeholder {
    padding: 26px 20px;
    border: 2px dashed $border;
    border-radius: 14px;
    background: rgba($border-light, 0.4);
    width: 100%;
    transition: all 0.2s ease-out;

    &:hover {
      border-color: rgba($accent, 0.3);
      background: rgba($accent, 0.03);
    }

    .video-choice {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      align-items: stretch;
      gap: 18px;
      width: 100%;
    }

    .video-choice-divider {
      width: 1px;
      background: $border;
    }

    .video-choice-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 10px;
      min-height: 130px;
    }

    .el-icon { color: $text-light; }

    p {
      color: $text-light;
      font-size: 14px;
      margin: 0;
      font-weight: 500;
    }
  }

  .video-recording {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 24px;
    border: 2px solid $danger;
    border-radius: 14px;
    background: linear-gradient(135deg, rgba($danger, 0.04), rgba($danger, 0.01));

    .recording-indicator {
      display: flex;
      align-items: center;
      gap: 8px;

      .rec-dot {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: $danger;
        animation: recorder-blink 1s infinite;
      }

      .rec-timer {
        font-size: 20px;
        font-weight: 700;
        color: $danger;
        font-variant-numeric: tabular-nums;
        font-family: $font-mono;
      }
    }
  }

  .video-preview {
    width: 100%;

    .preview-video {
      width: 100%;
      max-height: 300px;
      border-radius: 10px;
      background: #000;
    }

    .video-info {
      display: flex;
      gap: 16px;
      padding: 8px 0;

      .video-size, .video-duration {
        font-size: 11px;
        color: $text-light;
        background: $border-light;
        padding: 2px 10px;
        border-radius: 20px;
        font-family: $font-mono;
      }
    }

    .video-actions {
      display: flex;
      gap: 10px;
    }
  }
}

.scene-recognition-field {
  width: 100%;
}

.scene-tip {
  margin-top: 6px;
  font-size: 12px;
  color: $text-light;
}

.scene-extra-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.structured-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;

  :deep(.el-input-number),
  :deep(.el-select) {
    width: 100%;
  }

.weather-field {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;

  :deep(.el-select) {
    width: 100%;
  }
}

.weather-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;

  .weather-main {
    color: #2563eb;
    font-weight: 600;
  }

  &.is-loading {
    color: #2563eb;
  }

  &.is-error {
    color: #d97706;
  }
}

}

@media (max-width: 640px) {
  .video-recorder {
    .video-placeholder {
      .video-choice {
        grid-template-columns: 1fr;
      }

      .video-choice-divider {
        width: 100%;
        height: 1px;
      }
    }
  }
}

.public-report-dialog {
  .result-success-banner {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    background: linear-gradient(135deg, rgba($success, 0.08), rgba($success, 0.02));
    border: 1px solid rgba($success, 0.15);
    border-radius: 10px;
    margin-bottom: 20px;
    font-size: 14px;
    color: $success;
    font-weight: 500;
  }

  .advice-section {
    .advice-card {
      background: linear-gradient(135deg, rgba($warning, 0.06), rgba($warning, 0.02));
      border: 1px solid rgba($warning, 0.12);
      border-radius: 10px;
      padding: 16px;

      .calming-msg {
        font-size: 15px;
        font-weight: 600;
        color: $text-primary;
        margin-bottom: 8px;
      }

      .advice-text {
        font-size: 14px;
        color: $text-secondary;
        line-height: 1.7;
        margin-bottom: 10px;
        white-space: pre-line;
      }

      .action-list {
        margin: 0;
        padding-left: 20px;
        list-style: disc;

        li {
          font-size: 13px;
          color: $text-secondary;
          line-height: 1.8;
        }
      }
    }
  }

  .arrival-section {
    .el-alert {
      background: linear-gradient(135deg, rgba($accent, 0.06), rgba($accent, 0.02));
      border: 1px solid rgba($accent, 0.10);
    }
  }

  .prediction-section {
    .trace-id {
      font-family: $font-mono;
      font-size: 11px;
      background: $border-light;
      padding: 1px 6px;
      border-radius: 4px;
      color: $text-secondary;
    }

    .prediction-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 24px 0;
      gap: 8px;
      color: $text-secondary;
      font-size: 14px;

      .is-loading {
        font-size: 24px;
        color: $accent;
        animation: rotating 2s linear infinite;
      }
    }
  }
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}



</style>



