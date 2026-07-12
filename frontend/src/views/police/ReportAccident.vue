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
            <div v-if="!videoBlob && !recording" class="video-placeholder">
              <el-icon :size="48"><VideoCamera /></el-icon>
              <p>录制现场视频</p>
              <el-button type="primary" icon="VideoCamera" @click="startRecording">
                开始录制
              </el-button>
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

            <div v-if="videoBlob && !recording" class="video-preview">
              <video
                ref="videoRef"
                :src="videoUrl"
                controls
                class="preview-video"
              ></video>
              <div class="video-info">
                <span class="video-size">{{ formatSize(videoBlob.size) }}</span>
                <span class="video-duration">20秒</span>
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

        <!-- 事故类型 -->
        <el-form-item label="事故类型" prop="accidentType">
          <div class="type-picker">
            <el-select
              v-model="form.accidentType"
              placeholder="选择事故类型（AI将自动识别）"
              clearable
              style="width:320px"
            >
              <el-option
                v-for="t in ACCIDENT_TYPE_OPTIONS"
                :key="t"
                :label="t"
                :value="t"
              />
            </el-select>
            <el-tag v-if="aiDetectedType" type="success" effect="dark" style="margin-left:8px;">
              AI识别: {{ aiDetectedType }}
            </el-tag>
          </div>
        </el-form-item>

        <!-- 行人受伤（现场人员手动填写，AI不检测） -->
        <el-form-item label="涉及人数">
          <el-input-number v-model="form.peopleInvolved" :min="0" placeholder="0" style="width:160px" />
        </el-form-item>
        <el-form-item label="受伤人数">
          <el-input-number v-model="form.injuredCount" :min="0" placeholder="0" style="width:160px" />
        </el-form-item>
        <el-form-item label="伤情描述">
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
            placeholder="请描述事故情况，如：追尾、碰撞、占用车道、是否有人员受伤等"
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
      </div>
      <div class="submission-actions">
        <el-button type="primary" icon="View" @click="openResultDialog" :disabled="!result">
          查看系统识别结果
        </el-button>
        <el-button icon="ChatLineSquare" @click="openAdviceDialog" :disabled="!result">
          查看初步处置建议
        </el-button>
        <el-button icon="Refresh" @click="handleReset">重新上报</el-button>
      </div>
    </div>

    <!-- 提交结果弹窗 -->
    <el-dialog
      v-model="resultVisible"
      title="事故提交结果"
      width="780px"
      top="5vh"
      destroy-on-close
      class="public-report-dialog"
    >
      <div v-if="result" class="result-container">
        <div v-if="submitSucceeded" class="result-success-banner">
          <el-icon :size="20"><CircleCheckFilled /></el-icon>
          事故已成功提交至指挥中心，系统正在进行智能分析
        </div>

        <!-- ── 1. 市民即时安全提示 ── -->
        <div v-if="publicReportMeta?.immediateAdvice" class="result-section advice-section">
          <h4>
            <el-icon style="vertical-align:-3px;"><WarningFilled /></el-icon>
            市民即时安全提示
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
              title="检测到可能的人员受伤，请立即拨打 120 急救电话！"
              type="danger"
              :closable="false"
              show-icon
              style="margin-top:10px;"
            />
          </div>
        </div>

        <!-- ── 2. 预计交警到达时间 ── -->
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

        <!-- ── 3. 预测模块提交状态 ── -->
        <div v-if="publicReportMeta?.predictionSubmit" class="result-section prediction-section">
          <h4>
            <el-icon style="vertical-align:-3px;"><DataAnalysis /></el-icon>
            智能预测状态
          </h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="提交状态">
              <el-tag :type="publicReportMeta.predictionSubmit.submitted ? 'success' : 'warning'" size="small">
                {{ publicReportMeta.predictionSubmit.submitted ? '已提交' : '未提交' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态描述">
              {{ publicReportMeta.predictionSubmit.status || publicReportMeta.predictionSubmit.message || '-' }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="publicReportMeta.predictionSubmit.dataModuleTraceId"
              label="追踪编号"
            >
              <code class="trace-id">{{ publicReportMeta.predictionSubmit.dataModuleTraceId }}</code>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-divider />

        <!-- ── 4. 事故识别结果 ── -->
        <div class="result-section">
          <h4>事故识别结果</h4>
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="result-stat">
                <span class="stat-label">事故类型</span>
                <span class="stat-value highlight">{{ result.type }}</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="result-stat">
                <span class="stat-label">风险等级</span>
                <RiskBadge :level="result.riskLevel" size="large" />
              </div>
            </el-col>
            <el-col :span="8">
              <div class="result-stat">
                <span class="stat-label">识别可信度</span>
                <span class="stat-value">{{ result.confidence }}</span>
              </div>
            </el-col>
          </el-row>

        </div>
      </div>

      <template #footer>
        <el-button @click="resultVisible = false; adviceVisible = false">关闭</el-button>
        <el-button type="primary" @click="resultVisible = false">我知道了</el-button>
      </template>
    </el-dialog>

    <!-- 处置建议独立弹窗 -->
    <el-dialog v-model="adviceVisible" title="初步处置建议" width="600px" top="20vh" destroy-on-close>
      <div v-if="result" class="advice-container">
        <div class="advice-section">
          <h4>
            <el-icon style="vertical-align:-2px;"><WarningFilled /></el-icon>
            事故信息
          </h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="事故编号">{{ result.caseNo }}</el-descriptions-item>
            <el-descriptions-item label="事故类型">{{ result.type }}</el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <RiskBadge :level="result.riskLevel" size="small" />
            </el-descriptions-item>
            <el-descriptions-item label="影响车道">{{ result.affectedLanes }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="advice-section">
          <h4>
            <el-icon style="vertical-align:-2px;"><Collection /></el-icon>
            初步处置建议
          </h4>
          <el-alert
            :title="result.disposalAdvice"
            type="warning"
            :closable="false"
            show-icon
            class="advice-alert"
          />
        </div>

        <div class="advice-section">
          <h4>
            <el-icon style="vertical-align:-2px;"><ChatLineSquare /></el-icon>
            预计影响
          </h4>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="拥堵时长">{{ result.congestionDuration }}</el-descriptions-item>
            <el-descriptions-item label="恢复时间">{{ result.recoveryTime }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="advice-section" v-if="result.supportAdvice">
          <h4>支援建议</h4>
          <el-alert :title="result.supportAdvice" type="info" :closable="false" show-icon />
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="adviceVisible = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAccidentStore } from '@/stores/accident'
import { useAiChatContext } from '@/composables/useAiChatContext'
import { publicReport, publicReportWithAttachments } from '@/services/modules/accident'
import { reverseGeocode } from '@/services/modules/map'
import { getRealCurrentPosition, getBaiduIPLocation, bd09ToWgs84, wgs84ToBd09 } from '@/utils/location'
import { ElMessage } from 'element-plus'
import {
  Upload, Aim, LocationFilled, Loading, View, Collection,
  WarningFilled, ChatLineSquare, VideoCamera, VideoPause, Delete, InfoFilled,
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
const adviceVisible = ref(false)
const result = ref(null)
const lastSubmission = ref(null)
const aiDetectedType = ref('') // AI 识别到的事故类型

/** 公共上报返回的元数据（即时提示、预计到达、预测提交状态） */
const publicReportMeta = ref(null)
/** 提交成功后显示绿色提示 */
const submitSucceeded = ref(false)

// ====== 表单数据（必须在 AI 聊天上下文之前定义，避免 TDZ） ======
const form = reactive({
  images: [],
  video: null,
  customLocation: '',
  locationStr: '',
  locationName: '',   // 地点名称
  locationLat: null,  // WGS84 纬度
  locationLng: null,  // WGS84 经度
  accidentType: '',
  description: '',
  peopleInvolved: null,
  injuredCount: null,
  injuryEstimate: '',
})

const rules = {
  images: [{ required: true, message: '请上传至少一张事故照片', trigger: 'change' }],
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

// 地图中心（BD09 坐标系，用于传递给 MapCard）
const mapCenter = ref(null)

// 事故类型预设
const ACCIDENT_TYPE_OPTIONS = [
  '追尾事故', '正面碰撞', '侧面刮擦', '车辆侧翻',
  '撞固定物', '车辆自燃', '货物散落', '其他',
]

// ====== 视频录制状态 ======
const recording = ref(false)
const videoBlob = ref(null)
const videoUrl = ref('')
const recordElapsed = ref(0)
let mediaRecorder = null
let mediaStream = null
let recordTimer = null
const cameraRef = ref(null)
const videoRef = ref(null)

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
    console.error('摄像头启动失败:', err)
    ElMessage.error('无法访问摄像头，请检查权限设置')
    recording.value = false
  }
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
  videoUrl.value = ''
  videoRef.value = null
}

/** 地图标记 — 给 MapCard 传递 BD09 坐标（百度地图使用） */
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
    // 不自动清除坐标，用户可能已在地图上选了点
  }
}

// ====== 地图位置选取 ======
/** 用户在地图上点击选位时触发（坐标来自百度地图，为 BD09） */
function onMapLocationSelect(data) {
  // data 包含 BD09 的 lng, lat 和地址信息
  // 转换 BD09 → WGS84 用于后端存储
  const wgs84 = bd09ToWgs84(data.lng, data.lat)

  form.locationLat = wgs84.lat
  form.locationLng = wgs84.lng
  form.locationName = data.address || data.formattedAddress || form.customLocation || '地图选点'
  form.locationStr = data.formattedAddress || data.address || data.semanticDescription || `${wgs84.lat.toFixed(4)}, ${wgs84.lng.toFixed(4)}`

  // 地图中心自动跟随选择点（MapCard 已 panTo）
}

/** 用户点击「确认」按钮 */
function onMapLocationConfirm(data) {
  // 同 onMapLocationSelect 逻辑
  if (data) {
    const wgs84 = bd09ToWgs84(data.lng, data.lat)
    form.locationLat = wgs84.lat
    form.locationLng = wgs84.lng
    form.locationName = data.address || form.locationName
    form.locationStr = data.address || form.locationStr
  }
  ElMessage.success(`已选择位置: ${form.locationStr}`)
}

// ====== 自动定位 ======
async function handleAutoLocate() {
  locating.value = true
  try {
    // ---------- 1. 优先使用真实 GPS ----------
    let coords
    let source = 'GPS'
    try {
      coords = await getRealCurrentPosition()
    } catch (gpsErr) {
      console.warn('[ReportAccident] GPS 定位失败，尝试百度 IP 定位:', gpsErr.message)

      // ---------- 2. 降级：百度地图 IP 定位 ----------
      try {
        coords = await getBaiduIPLocation()
        source = 'IP'
      } catch (ipErr) {
        console.warn('[ReportAccident] 百度 IP 定位也失败:', ipErr.message)
        ElMessage.warning('自动定位不可用，请点击地图选择位置')
        locating.value = false
        return
      }
    }

    const { lat, lng } = coords // WGS84

    // 反向地理编码获取地址
    // 优先用 BMapGL.Geocoder（客户端走浏览器 AK，不走后端，避免 502）
    let addressText = `${lat.toFixed(4)}, ${lng.toFixed(4)}`
    try {
      const BMapGL = window.BMapGL
      if (BMapGL && typeof BMapGL.Geocoder === 'function') {
        const geocoder = new BMapGL.Geocoder()
        const bd09 = wgs84ToBd09(lng, lat)
        const result = await new Promise((resolve, reject) => {
          geocoder.getLocation(
            new BMapGL.Point(bd09.lng, bd09.lat),
            (res) => {
              res && res.address ? resolve(res) : reject(new Error('无地址'))
            }
          )
        })
        if (result.address) {
          addressText = result.address
        }
      } else {
        // Geocoder 不可用时降级调用后端
        const loc = await reverseGeocode(lng, lat, 'WGS84')
        addressText = loc.formattedAddress || loc.semanticDescription || addressText
      }
    } catch (addrErr) {
      console.warn('[ReportAccident] 地址解析失败，使用坐标作为地址:', addrErr.message)
      // 地址解析失败不影响定位，直接用坐标
    }

    // 更新表单
    form.locationLat = lat
    form.locationLng = lng
    form.locationName = addressText
    form.locationStr = addressText
    form.customLocation = addressText

    // 设置地图中心（WGS84 → MapCard 需要 BD09）
    setMapCenterToWgs84(lng, lat)

    if (source === 'IP') {
      ElMessage.info(`IP 定位成功（${addressText}）`)
    } else {
      ElMessage.success('定位成功')
    }
  } catch (err) {
    ElMessage.error(err.message || '定位失败')
  } finally {
    locating.value = false
  }
}

/**
 * 将 WGS84 坐标转换为 BD09 并设为地图中心
 */
async function setMapCenterToWgs84(lng, lat) {
  const bd09 = wgs84ToBd09(lng, lat)
  mapCenter.value = { lng: bd09.lng, lat: bd09.lat }
}

function clearLocation() {
  form.locationStr = ''
  form.locationName = ''
  form.locationLat = null
  form.locationLng = null
  mapCenter.value = null
}

// ====== 提交 ======
async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const hasRealFiles = form.images.some((img) => img.raw)
    const payload = {
      images: form.images,
      video: videoBlob.value ? { raw: videoBlob.value, name: '现场视频.webm' } : null,
      location: {
        name: form.locationName || form.locationStr,
        area: form.customLocation || form.locationName,
        road: '',
        lat: form.locationLat,
        lng: form.locationLng,
      },
      description: form.description,
      accidentType: form.accidentType || '',
      peopleInvolved: form.peopleInvolved || 0,
      injuredCount: form.injuredCount || 0,
      injuryEstimate: form.injuryEstimate || '',
      reporter: userStore.nickname,
      reporterId: userStore.userInfo?.id || 0,
      coordinateType: 'WGS84',
    }
    const res = hasRealFiles ? await publicReportWithAttachments(payload) : await publicReport(payload)

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

      // 识别结果数据（与原有 result 结构一致）
      result.value = detail

      // 公共上报附加元数据
      publicReportMeta.value = {
        immediateAdvice: data.immediateAdvice,
        estimatedPoliceArrivalMinutes: data.estimatedPoliceArrivalMinutes,
        estimatedPoliceArrivalText: data.estimatedPoliceArrivalText,
        predictionSubmit: data.predictionSubmit,
      }

      if (detail?.id) {
        accidentStore.updateAccident(detail.id, detail)
      }

      submitSucceeded.value = true
      resultVisible.value = true
      ElMessage.success('事故提交成功！')
    }
  } catch {
    // 错误已在拦截器中处理
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
  form.description = ''
  form.peopleInvolved = null
  form.injuredCount = null
  form.injuryEstimate = ''
  aiDetectedType.value = ''
  mapCenter.value = null
  result.value = null
  resultVisible.value = false
  adviceVisible.value = false
  lastSubmission.value = null
  publicReportMeta.value = null
  submitSucceeded.value = false
  formRef.value?.resetFields()
}

function openResultDialog() {
  resultVisible.value = true
}

function openAdviceDialog() {
  adviceVisible.value = true
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
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    padding: 40px 20px;
    border: 2px dashed $border;
    border-radius: 14px;
    background: rgba($border-light, 0.4);
    width: 100%;
    transition: all 0.2s ease-out;

    &:hover {
      border-color: rgba($accent, 0.3);
      background: rgba($accent, 0.03);
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
  }
}

.advice-container {
  .advice-section {
    margin-bottom: 20px;

    h4 {
      font-family: $font-sans;
      font-size: 14px;
      font-weight: 600;
      margin-bottom: 10px;
      color: $text-primary;
    }
  }

  .advice-alert {
    white-space: pre-line;
  }
}
</style>
