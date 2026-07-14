import request from '../request'

// ====== 枚举映射 ======

const TASK_TYPE_MAP = {
  POLICE: '警车',
  CLEARANCE: '清障车',
  RESCUE: '救援车',
  MEDICAL: '救护车',
}

const TASK_TYPE_MAP_REVERSE = {
  警车: 'POLICE',
  清障车: 'CLEARANCE',
  救援车: 'RESCUE',
  救护车: 'MEDICAL',
  救援: 'RESCUE',
  工程车: 'CLEARANCE',
}

const TASK_STATUS_MAP = {
  DISPATCHED: '待接收',
  DEPARTED: '已出发',
  ARRIVED: '已到达',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const TASK_STATUS_MAP_REVERSE = {
  待接收: 'DISPATCHED',
  已出发: 'DEPARTED',
  已到达: 'ARRIVED',
  处理中: 'PROCESSING',
  已完成: 'COMPLETED',
  已取消: 'CANCELLED',
}

const RISK_LEVEL_MAP = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重',
}

const INCIDENT_STATUS_MAP = {
  REPORTED: '已上报',
  PREDICTION_REQUESTED: '预测中',
  PREDICTED: '待调度',
  DISPATCHED: '已调度',
  PROCESSING: '处理中',
  CLEARED: '已清障',
  CLOSED: '已结案',
}

const VEHICLE_TYPE_LABELS = {
  AMBULANCE: '救护车',
  CLEARANCE_TRUCK: '清障车',
}

const VEHICLE_STATUS_LABELS = {
  AVAILABLE: '可调度',
  DISPATCHED: '已调度',
  EN_ROUTE: '前往现场',
  ARRIVED: '已到达',
  OUT_OF_SERVICE: '停用',
}

/*
 * 兼容当前大屏旧调度弹窗。
 * 新 ETA 调度页面会直接使用后端返回的真实用户 ID。
 */
const RESCUE_USER_MAP = {
  王队长: 3,
  陈师傅: 3,
  刘师傅: 3,
  张师傅: 3,
}

const COMMAND_USER_MAP = {
  李指挥: 2,
}

function mapTaskType(type) {
  return TASK_TYPE_MAP[type] || type || '-'
}

function mapTaskTypeReverse(type) {
  return TASK_TYPE_MAP_REVERSE[type] || type
}

function mapTaskStatus(status) {
  return TASK_STATUS_MAP[status] || status || '-'
}

function mapTaskStatusReverse(status) {
  return TASK_STATUS_MAP_REVERSE[status] || status
}

function transformTask(task = {}) {
  return {
    id: task.id,
    taskNo: task.taskNo || '',

    accidentId: task.incidentId,
    caseNo: task.incidentNo || '',
    accidentType: task.accidentType || '',

    riskLevel:
      RISK_LEVEL_MAP[task.riskLevel] ||
      task.riskLevel ||
      '-',

    location: {
      name: task.locationName || '',
      road: '',
      area: '',
      lng:
        task.longitude ||
        task.baiduLongitude ||
        null,
      lat:
        task.latitude ||
        task.baiduLatitude ||
        null,
    },

    status: mapTaskStatus(task.status),
    statusCode: task.status,

    taskType: task.taskType,

    vehicleType: task.emergencyVehicleId
      ? VEHICLE_TYPE_LABELS[task.vehicleType] ||
        mapTaskType(task.taskType)
      : mapTaskType(task.taskType),

    vehicleId: task.emergencyVehicleId || null,
    vehiclePlate: task.emergencyVehicleNo || '',
    vehicleName: task.emergencyVehicleName || '',

    assignedTo: task.receiverName || '',
    assignedToId: task.receiverUserId,

    assigner: task.assignerName || '',
    assignerId: task.assignedByUserId,

    createTime: task.createdAt,
    updateTime: task.updatedAt,

    feedback: task.feedback || '',
    notes: task.advice || '',

    vehicleRequired: task.vehicleRequired,

    distanceKm:
      task.dispatchDistanceKm ?? null,

    speedKmh:
      task.dispatchSpeedKmh ?? null,

    estimatedArrivalMinutes:
      task.estimatedArrivalMinutes ?? null,

    // ---- Algorithm4 dispatch recommendation ----
    dispatchPlan: parseDispatchPlan(task.dispatchPlan) || [],
    dispatchModelVersion: task.dispatchModelVersion || '',
    dispatchTraceId: task.dispatchTraceId || '',
    // ---- Algorithm3 recovery recommendation ----
    recoveryRecommendation: task.recoveryRecommendation || '',
    recoveryConfidence: task.recoveryConfidence ?? null,
    recoveryLevel: task.recoveryLevel || '',
  }
}

/**
 * Parse Algorithm4 dispatchPlan JSON string into displayable array.
 */
function parseDispatchPlan(raw) {
  if (!raw || typeof raw !== 'string') return null
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return null
    return arr.map(item => ({
      taskType: item.taskType || '',
      priority: item.priority || '',
      units: item.recommendedUnits ?? item.units ?? 1,
      etaMin: item.estimatedArrivalMinutes ?? item.etaMin ?? 0,
      reasoning: item.reasoning || '',
      score: item.score != null ? (item.score * 100).toFixed(0) + '%' : '-',
    }))
  } catch {
    return null
  }
}

function transformCommandIncident(incident = {}) {
  return {
    id: incident.id,
    caseNo: incident.incidentNo || '',

    type:
      incident.accidentType ||
      '待识别',

    riskLevel:
      RISK_LEVEL_MAP[incident.riskLevel] ||
      incident.riskLevel ||
      '-',

    riskLevelCode: incident.riskLevel,

    status:
      INCIDENT_STATUS_MAP[incident.status] ||
      incident.status ||
      '-',

    statusCode: incident.status,

    locationName:
      incident.mapFormattedAddress ||
      incident.locationName ||
      incident.address ||
      '未填写位置',

    address: incident.address || '',

    longitude:
      incident.baiduLongitude ||
      incident.longitude ||
      null,

    latitude:
      incident.baiduLatitude ||
      incident.latitude ||
      null,

    supportRequired:
      Boolean(incident.supportRequired),

    supportReason:
      incident.supportReason || '',

    congestionMinutes:
      incident.predictedCongestionMinutes ?? null,

    recoveryMinutes:
      incident.predictedRecoveryMinutes ?? null,

    reportTime: incident.reportTime,

    dispatchTaskCount:
      incident.dispatchTaskCount || 0,

    activeDispatchTaskCount:
      incident.activeDispatchTaskCount || 0,
  }
}

function transformEta(vehicle = {}) {
  return {
    vehicleId: vehicle.vehicleId,
    vehicleNo: vehicle.vehicleNo || '',
    vehicleName: vehicle.vehicleName || '',

    vehicleType: vehicle.vehicleType,

    vehicleTypeLabel:
      VEHICLE_TYPE_LABELS[vehicle.vehicleType] ||
      vehicle.vehicleType,

    status: vehicle.status,

    statusLabel:
      VEHICLE_STATUS_LABELS[vehicle.status] ||
      vehicle.status,

    longitude:
      vehicle.baiduLongitude ||
      vehicle.longitude ||
      null,

    latitude:
      vehicle.baiduLatitude ||
      vehicle.latitude ||
      null,

    speedKmh: vehicle.speedKmh ?? null,
    distanceKm: vehicle.distanceKm ?? null,

    estimatedArrivalMinutes:
      vehicle.estimatedArrivalMinutes ?? null,

    fastest: Boolean(vehicle.fastest),
    message: vehicle.message || '',
  }
}

// ====== 原有任务接口，继续给大屏和救援端使用 ======

export async function getDispatchList(
  params = {}
) {
  const backendParams = {}

  if (params.page) {
    backendParams.page = params.page - 1
  }

  if (params.pageSize) {
    backendParams.size = params.pageSize
  }

  if (params.status) {
    backendParams.status =
      mapTaskStatusReverse(params.status)
  }

  const res = await request.get(
    '/v1/dispatch-tasks',
    {
      params: backendParams,
    }
  )

  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(
        transformTask
      ),
      total: res.data.totalElements || 0,
    },
  }
}

export async function getDispatchDetail(id) {
  const res = await request.get(
    '/v1/dispatch-tasks',
    {
      params: {
        page: 0,
        size: 100,
      },
    }
  )

  const task = (res.data.content || []).find(
    (item) => item.id === Number(id)
  )

  if (!task) {
    return {
      code: 404,
      data: null,
    }
  }

  const detail = transformTask(task)

  if (task.incidentId) {
    try {
      const incidentRes = await request.get(
        `/v1/incidents/${task.incidentId}`
      )

      const incident =
        incidentRes.data?.incident ||
        incidentRes.data

      if (incident) {
        detail.location.lng =
          incident.baiduLongitude ||
          incident.longitude ||
          detail.location.lng

        detail.location.lat =
          incident.baiduLatitude ||
          incident.latitude ||
          detail.location.lat

        detail.location.name =
          incident.mapFormattedAddress ||
          incident.locationName ||
          detail.location.name

        detail.caseNo =
          incident.incidentNo ||
          detail.caseNo

        detail.accidentType =
          incident.confirmedAccidentType ||
          incident.initialAccidentType ||
          detail.accidentType
      }
    } catch (error) {
      console.warn(
        '[dispatch] 无法获取关联事故详情:',
        error.message
      )
    }
  }

  return {
    code: 200,
    data: detail,
  }
}

export async function createDispatch(data) {
  const body = {
    incidentId: data.accidentId,

    taskType: mapTaskTypeReverse(
      data.vehicleType || '清障车'
    ),

    receiverUserId:
      data.assignedToId ||
      RESCUE_USER_MAP[data.assignedTo] ||
      null,

    assignedByUserId:
      data.assignerId ||
      COMMAND_USER_MAP[data.assigner] ||
      null,

    vehicleRequired:
      Boolean(data.vehicleType),

    vehicleType:
      data.vehicleType || '',

    advice:
      data.notes || '',
  }

  const res = await request.post(
    '/v1/dispatch-tasks',
    body
  )

  return {
    code: 200,
    data: {
      id: res.data.id,
    },
  }
}

/**
 * 获取清障人员任务详情和导航链接。
 *
 * currentPosition 不为空时，将清障人员当前 GPS 坐标传给后端，
 * 由后端生成“当前位置 → 事故地点”的导航链接。
 *
 * 浏览器 Geolocation 返回的是 WGS84 坐标。
 *
 * @param {string|number} taskId 调度任务 ID
 * @param {{lng: number, lat: number, coordinateType?: string}|null} currentPosition
 */
export async function getClearanceRescueDetail(
  taskId,
  currentPosition = null
) {
  const config = {}

  if (currentPosition) {
    const longitude = Number(
      currentPosition.lng
    )

    const latitude = Number(
      currentPosition.lat
    )

    if (
      !Number.isFinite(longitude) ||
      !Number.isFinite(latitude)
    ) {
      throw new Error(
        '当前位置坐标无效'
      )
    }

    config.params = {
      longitude,
      latitude,
      coordinateType:
        currentPosition.coordinateType ||
        'WGS84',
    }
  }

  const res = await request.get(
    `/v1/dispatch-tasks/${taskId}/clearance-rescue-detail`,
    config
  )

  return {
    code: 200,
    data: res.data,
  }
}


export async function updateDispatchStatus(
  data
) {
  const body = {
    status:
      mapTaskStatusReverse(data.status),
  }

  if (data.feedback) {
    body.feedback = data.feedback
  }

  const res = await request.put(
    `/v1/dispatch-tasks/${data.id}/status`,
    body
  )

  return {
    code: 200,
    data:
      res.data
        ? transformTask(res.data)
        : null,
  }
}

// ====== 指挥中心 ETA 选车接口 ======

/**
 * 获取指挥中心事故列表。
 */
export async function getCommandIncidents(
  params = {}
) {
  const backendParams = {
    page: Math.max(
      (params.page || 1) - 1,
      0
    ),

    size:
      params.pageSize || 100,

    sort:
      'createdAt,desc',
  }

  if (params.status) {
    backendParams.status = params.status
  }

  if (params.riskLevel) {
    backendParams.riskLevel =
      params.riskLevel
  }

  if (params.keyword) {
    backendParams.keyword =
      params.keyword
  }

  const res = await request.get(
    '/v1/command-center/incidents',
    {
      params: backendParams,
    }
  )

  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(
        transformCommandIncident
      ),

      total:
        res.data.totalElements || 0,
    },
  }
}

/**
 * 获取可接受任务的清障救援人员。
 */
export async function getResponders(
  role = 'RESCUE_WORKER'
) {
  const res = await request.get(
    '/v1/command-center/responders',
    {
      params: {
        role,
      },
    }
  )

  return {
    code: 200,

    data: (res.data || []).map(
      (user) => ({
        id: user.id,

        fullName:
          user.fullName ||
          user.username,

        username:
          user.username,

        phone:
          user.phone || '',

        role:
          user.role,
      })
    ),
  }
}

/**
 * 查询某次事故的可用车辆 ETA。
 */
export async function getVehicleEtas(
  incidentId,
  vehicleType
) {
  const res = await request.get(
    `/v1/command-center/incidents/${incidentId}/vehicle-etas`,
    {
      params: {
        vehicleType,
      },
    }
  )

  return {
    code: 200,

    data: (res.data || []).map(
      transformEta
    ),
  }
}

/**
 * 调度指挥人员手动选择的车辆。
 */
export async function dispatchSelectedVehicle(
  incidentId,
  data
) {
  const res = await request.post(
    `/v1/command-center/incidents/${incidentId}/vehicle-dispatch`,
    {
      vehicleType:
        data.vehicleType,

      vehicleId:
        data.vehicleId,

      receiverUserId:
        data.receiverUserId || null,

      assignedByUserId:
        data.assignedByUserId || null,

      advice:
        data.advice || '',
    }
  )

  return {
    code: 200,
    data: transformTask(res.data),
  }
}

/**
 * 查询某事故已经产生的调度任务。
 */
export async function getIncidentDispatchTasks(
  incidentId
) {
  const res = await request.get(
    `/v1/command-center/incidents/${incidentId}/dispatch-tasks`
  )

  return {
    code: 200,

    data: (res.data || []).map(
      transformTask
    ),
  }
}

/**
 * 获取事故的 AI 分析后附件（YOLO 标注图片/视频），供指挥中心查看。
 * GET /api/v1/command-center/incidents/{incidentId}/ai-attachments
 */
function parseJson(value) {
  if (!value) return null
  try {
    return typeof value === 'string' ? JSON.parse(value) : value
  } catch {
    return null
  }
}

function firstNonEmpty(...values) {
  return values.find((value) => typeof value === 'string' && value.trim())?.trim() || ''
}

function findNestedValue(source, keys) {
  if (!source || typeof source !== 'object') return ''

  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.trim()) {
      return value.trim()
    }
  }

  for (const value of Object.values(source)) {
    if (Array.isArray(value)) {
      for (const item of value) {
        const nested = findNestedValue(item, keys)
        if (nested) return nested
      }
      continue
    }

    if (value && typeof value === 'object') {
      const nested = findNestedValue(value, keys)
      if (nested) return nested
    }
  }

  return ''
}

/**
 * 将 YOLO 输出的绝对 URL（如 http://localhost:8000/runs/api/videos/x.mp4）
 * 转换为相对路径（/runs/api/videos/x.mp4），统一走 Vite 代理 / Spring Boot 静态资源。
 */
function normalizeAnnotatedUrl(url) {
  if (!url) return ''
  return String(url).trim().replace(/^https?:\/\/[^/]+/i, '')
}

function resolveAnnotatedUrl(att = {}) {
  const detection = parseJson(att.aiDetectionJson)

  return normalizeAnnotatedUrl(firstNonEmpty(
    att.annotatedFileUrl,
    att.annotatedUrl,
    att.resultFileUrl,
    att.processedFileUrl,
    att.yoloOutputUrl,
    att.outputVideoUrl,
    att.outputImageUrl,
    att.output_video_url,
    att.output_image_url,
    findNestedValue(detection, [
      'annotatedFileUrl',
      'annotatedUrl',
      'resultFileUrl',
      'processedFileUrl',
      'yoloOutputUrl',
      'output_video_url',
      'outputVideoUrl',
      'output_image_url',
      'outputImageUrl',
    ])
  ))
}

function inferAttachmentType(att = {}) {
  if (att.attachmentType) return att.attachmentType

  const contentType = String(att.contentType || '').toLowerCase()
  const fileName = String(att.originalFilename || att.fileName || '').toLowerCase()

  if (contentType.startsWith('image/') || /\.(png|jpe?g|webp|gif|bmp)$/i.test(fileName)) {
    return 'PHOTO'
  }

  if (contentType.startsWith('video/') || /\.(mp4|webm|mov|m4v|avi)$/i.test(fileName)) {
    return 'VIDEO'
  }

  return att.attachmentType || 'OTHER'
}

function normalizeAiDetectedTypes(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join(',')
  return value || ''
}

/**
 * 受保护的原始附件接口不能直接放到 <video>/<img> 中：浏览器不会给媒体标签自动带 Authorization。
 * Dashboard 会用这个 API 路径通过 axios 取 Blob，再转成 objectURL 作为兜底。
 */
function attachmentFileApiPath(incidentId, attachmentId) {
  return `/v1/incidents/${incidentId}/attachments/${attachmentId}/file`
}

export async function getIncidentAttachmentBlobUrl(incidentId, attachmentId) {
  const res = await request.get(
    attachmentFileApiPath(incidentId, attachmentId),
    { responseType: 'blob' }
  )

  return URL.createObjectURL(res.data)
}

export async function getIncidentAttachments(incidentId) {
  const res = await request.get(
    `/v1/command-center/incidents/${incidentId}/ai-attachments`
  )

  return {
    code: 200,
    data: (res.data || []).map((att) => {
      const annotatedFileUrl = resolveAnnotatedUrl(att)
      const attachmentType = inferAttachmentType(att)
      const fileApiPath = attachmentFileApiPath(incidentId, att.id)

      return {
        id: att.id,
        incidentId: att.incidentId || incidentId,
        fileName: att.fileName,
        originalFilename: att.originalFilename,
        contentType: att.contentType,
        attachmentType,
        recognitionStatus: att.recognitionStatus,
        aiDetectedTypes: normalizeAiDetectedTypes(att.aiDetectedTypes),
        aiDetectionJson: att.aiDetectionJson || '',
        /** YOLO 输出标注文件，已转为相对路径走后端代理 */
        annotatedFileUrl,
        hasAnnotatedMedia: Boolean(annotatedFileUrl),
        reviewed: att.reviewed,
        /** 原始文件接口信息（兜底用 Blob 加载，避免媒体标签丢 Authorization） */
        fileApiPath,
        fileUrl: `/api${fileApiPath}`,
        displayUrl: annotatedFileUrl || '',
      }
    }),
  }
}
