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
            <!-- 未录制且没有视频 -->
            <div v-if="!videoBlob && !recording" class="video-placeholder">
              <el-icon :size="48"><VideoCamera /></el-icon>
              <p>录制现场视频</p>
              <el-button type="primary" icon="VideoCamera" @click="startRecording">
                开始录制
              </el-button>
            </div>

            <!-- 录制中 -->
            <div v-if="recording" class="video-recording">
              <div class="recording-indicator">
                <span class="rec-dot"></span>
                <span class="rec-timer">{{ formatTime(recordElapsed) }} / 20秒</span>
              </div>
              <el-progress
                :percentage="(recordElapsed / 20) * 100"
                :stroke-width="6"
                :color="recordElapsed >= 15 ? '#ef4444' : '#1a56db'"
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

            <!-- 录制完成预览 -->
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

            <!-- 隐藏的实时预览画面（录制时不可见，作为摄像头 feed） -->
            <video
              ref="cameraRef"
              style="display:none;"
              muted
              playsinline
            ></video>
          </div>
        </el-form-item>

        <!-- 事故地点 -->
        <el-form-item label="事故地点" prop="location">
          <div class="location-picker">
            <el-select
              v-model="form.locationPreset"
              placeholder="选择预设路段"
              clearable
              filterable
              class="location-select"
              @change="onPresetChange"
            >
              <el-option
                v-for="loc in PRESET_LOCATIONS"
                :key="loc.id"
                :label="loc.name"
                :value="loc.id"
              >
                <div class="loc-option">
                  <span class="loc-name">{{ loc.name }}</span>
                  <span class="loc-area">{{ loc.area }}</span>
                </div>
              </el-option>
            </el-select>
            <el-input
              v-model="form.customLocation"
              placeholder="或手动输入详细位置"
              class="location-input"
            />
            <el-button @click="handleAutoLocate" :loading="locating" type="default">
              <el-icon><Aim /></el-icon>
              自动定位
            </el-button>
          </div>
          <div v-if="form.locationStr" class="location-result">
            <el-icon><LocationFilled /></el-icon>
            {{ form.locationStr }}
          </div>
          <!-- 地图占位 -->
          <MapCard
            v-if="form.locationStr"
            :height="'180px'"
            :title="form.locationStr"
            hint="点击放大地图"
            :markers="mapMarkers"
          />
        </el-form-item>

        <!-- 事故类型 -->
        <el-form-item label="事故类型" prop="accidentType">
          <div class="type-picker">
            <el-select
              v-model="form.accidentType"
              placeholder="选择事故类型"
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
          </div>
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

    <!-- 识别结果展示 -->
    <el-dialog v-model="resultVisible" title="系统识别结果" width="700px" top="5vh" destroy-on-close>
      <div v-if="result" class="result-container">
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

        <el-divider />

        <div class="result-section">
          <h4>预计影响</h4>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="影响车道">{{ result.affectedLanes }}</el-descriptions-item>
                <el-descriptions-item label="拥堵持续时间">{{ result.congestionDuration }}</el-descriptions-item>
                <el-descriptions-item label="道路恢复时间">{{ result.recoveryTime }}</el-descriptions-item>
                <el-descriptions-item label="当前车流">{{ result.trafficFlow }}</el-descriptions-item>
              </el-descriptions>
            </el-col>
            <el-col :span="12">
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="天气">{{ result.weather }}</el-descriptions-item>
                <el-descriptions-item label="道路等级">{{ result.roadLevel }}</el-descriptions-item>
                <el-descriptions-item label="事故编号">{{ result.caseNo }}</el-descriptions-item>
              </el-descriptions>
            </el-col>
          </el-row>
        </div>

        <div class="result-section">
          <h4>初步处置建议</h4>
          <el-alert
            :title="result.disposalAdvice"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>

        <div class="result-section">
          <h4>AI 分析说明</h4>
          <el-alert
            :title="result.aiExplanation"
            type="info"
            :closable="false"
            show-icon
          />
        </div>
      </div>

      <div v-else class="result-loading">
        <el-skeleton :rows="6" animated />
        <div style="text-align:center;margin-top:16px;">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <p>系统正在分析事故数据，请稍候...</p>
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
import { ref, reactive, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAccidentStore } from '@/stores/accident'
import { addAccident as apiAddAccident, getAccidentDetail } from '@/services/modules/accident'
import { PRESET_LOCATIONS, mockGetCurrentLocation } from '@/utils/location'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Aim, LocationFilled, Loading, View, Collection, WarningFilled, ChatLineSquare, VideoCamera, VideoPause, Delete } from '@element-plus/icons-vue'
import PhotoUploader from '@/components/PhotoUploader.vue'
import MapCard from '@/components/MapCard.vue'
import RiskBadge from '@/components/RiskBadge.vue'

const userStore = useUserStore()
const accidentStore = useAccidentStore()
const formRef = ref(null)
const submitting = ref(false)
const locating = ref(false)
const resultVisible = ref(false)
const adviceVisible = ref(false)
const result = ref(null)
const lastSubmission = ref(null)

// 事故类型预设选项
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
    // 清除上一次录制的视频
    if (videoUrl.value) {
      URL.revokeObjectURL(videoUrl.value)
      videoUrl.value = ''
    }
    videoBlob.value = null

    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: true,
    })

    // 显示摄像头画面到隐藏的 video 元素
    if (cameraRef.value) {
      cameraRef.value.srcObject = mediaStream
    }

    // 创建 MediaRecorder
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
      // 释放摄像头
      if (mediaStream) {
        mediaStream.getTracks().forEach((t) => t.stop())
        mediaStream = null
      }
      recording.value = false
      clearInterval(recordTimer)
      recordTimer = null
      ElMessage.success('视频录制完成')
    }

    // 开始录制，20秒后自动停止
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

const form = reactive({
  images: [],
  video: null,
  locationPreset: null,
  customLocation: '',
  locationStr: '',
  location: null,
  accidentType: '',
  description: '',
})

const rules = {
  images: [{ required: true, message: '请上传至少一张事故照片', trigger: 'change' }],
  locationStr: [{ required: true, message: '请选择或输入事故地点', trigger: 'change' }],
  description: [{ required: true, message: '请输入事故描述', trigger: 'blur' }],
  accidentType: [{ required: false }],
}

const mapMarkers = computed(() => {
  if (!form.location) return null
  return [{ x: 50, y: 50, label: form.locationStr, type: 'danger' }]
})

function onPresetChange(val) {
  if (val) {
    const loc = PRESET_LOCATIONS.find((l) => l.id === val)
    if (loc) {
      form.locationStr = loc.name
      form.location = { name: loc.name, road: loc.road, area: loc.area, lat: 31.2 + Math.random() * 0.3, lng: 121.4 + Math.random() * 0.3 }
      form.customLocation = ''
    }
  } else {
    form.locationStr = form.customLocation
    form.location = form.customLocation ? { name: form.customLocation, road: '', area: '', lat: 0, lng: 0 } : null
  }
}

async function handleAutoLocate() {
  locating.value = true
  try {
    const loc = await mockGetCurrentLocation()
    form.locationStr = loc.address
    form.location = { name: loc.address, road: '', area: '', lat: loc.lat, lng: loc.lng }
    form.locationPreset = null
    form.customLocation = loc.address
    ElMessage.success('定位成功')
  } catch {
    ElMessage.error('定位失败，请手动输入')
  } finally {
    locating.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await apiAddAccident({
      images: form.images.map((img) => ({ name: img.name, url: img.url })),
      video: videoBlob.value ? { name: '现场视频.webm', size: videoBlob.value.size } : null,
      location: form.location,
      description: form.description,
      accidentType: form.accidentType || '',
      reporter: userStore.nickname,
      reporterId: userStore.userInfo?.id || 0,
    })

    if (res.code === 200) {
      lastSubmission.value = {
        id: res.data.id,
        caseNo: res.data.caseNo,
        reportTime: new Date().toLocaleString('zh-CN'),
        location: form.location,
      }
      ElMessage.success('事故提交成功！系统正在分析识别...')

      // 显示结果弹窗，模拟等待识别完成
      resultVisible.value = true
      setTimeout(async () => {
        try {
          const detailRes = await getAccidentDetail(res.data.id)
          if (detailRes.code === 200) {
            result.value = detailRes.data
            accidentStore.updateAccident(res.data.id, detailRes.data)
          }
        } catch {
          result.value = {
            id: res.data.id,
            caseNo: res.data.caseNo,
            type: form.accidentType || '追尾事故',
            riskLevel: '中',
            confidence: '86.5%',
            congestionDuration: '35分钟',
            recoveryTime: '50分钟',
            affectedLanes: '2条',
            trafficFlow: '平峰',
            weather: '晴',
            roadLevel: '快速路',
            disposalAdvice: '1. 在事故后方放置警示标志；2. 引导车辆绕行；3. 通知清障车到场',
            supportAdvice: '常规处置即可',
            aiExplanation: '【系统分析结果】经图像识别分析，判定为' + (form.accidentType || '追尾事故') + '，综合风险评估为"中"等级。',
          }
        }
      }, 3000)
    }
  } catch {
    // 错误已在拦截器中处理
  } finally {
    submitting.value = false
  }
}

function handleReset() {
  // 清除视频
  removeVideo()
  form.images = []
  form.locationPreset = null
  form.customLocation = ''
  form.locationStr = ''
  form.location = null
  form.accidentType = ''
  form.description = ''
  result.value = null
  resultVisible.value = false
  adviceVisible.value = false
  lastSubmission.value = null
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

  .location-select {
    width: 320px;
  }

  .location-input {
    flex: 1;
    min-width: 180px;
  }
}

.loc-option {
  display: flex;
  justify-content: space-between;
  .loc-name { font-size: 13px; }
  .loc-area { font-size: 12px; color: #9ca3af; }
}

.location-result {
  margin-top: 8px;
  padding: 6px 12px;
  background: #eff6ff;
  border-radius: 6px;
  font-size: 13px;
  color: $primary;
  display: flex;
  align-items: center;
  gap: 6px;
}

.submit-btn {
  padding: 12px 40px;
  font-size: 16px;
}

.result-container {
  .result-stat {
    text-align: center;
    padding: 16px;
    background: #f8fafc;
    border-radius: 8px;
    .stat-label {
      display: block;
      font-size: 12px;
      color: #6b7280;
      margin-bottom: 6px;
    }
    .stat-value {
      font-size: 20px;
      font-weight: 600;
      color: $text-primary;
      &.highlight {
        color: $primary;
      }
    }
  }

  .result-section {
    margin-top: 16px;
    h4 {
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
    font-size: 16px;
    font-weight: 600;
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

      &.success { color: #10b981; }
      &.pending { color: #f59e0b; }
    }
  }
}

.submission-actions {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
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
    border: 2px dashed #d1d5db;
    border-radius: 12px;
    background: #fafafa;
    width: 100%;
    transition: border-color 0.2s;

    &:hover {
      border-color: $primary;
    }

    p {
      color: $text-light;
      font-size: 14px;
      margin: 0;
    }
  }

  .video-recording {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 24px;
    border: 2px solid #ef4444;
    border-radius: 12px;
    background: #fef2f2;

    .recording-indicator {
      display: flex;
      align-items: center;
      gap: 8px;

      .rec-dot {
        width: 12px;
        height: 12px;
        border-radius: 50%;
        background: #ef4444;
        animation: blink 1s infinite;
      }

      .rec-timer {
        font-size: 20px;
        font-weight: 700;
        color: #ef4444;
        font-variant-numeric: tabular-nums;
      }
    }
  }

  .video-preview {
    width: 100%;

    .preview-video {
      width: 100%;
      max-height: 300px;
      border-radius: 8px;
      background: #000;
    }

    .video-info {
      display: flex;
      gap: 16px;
      padding: 8px 0;

      .video-size, .video-duration {
        font-size: 12px;
        color: $text-light;
        background: #f3f4f6;
        padding: 2px 10px;
        border-radius: 12px;
      }
    }

    .video-actions {
      display: flex;
      gap: 10px;
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.advice-container {
  .advice-section {
    margin-bottom: 20px;

    h4 {
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
