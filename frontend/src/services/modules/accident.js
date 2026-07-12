import request from '../request'

// ====== 枚举映射 ======

/** 后端事故状态 → 前端中文状态 */
const INCIDENT_STATUS_MAP = {
  REPORTED: '待处理',
  PREDICTED: '处理中',
  DISPATCHED: '处理中',
  PROCESSING: '处理中',
  CLEARED: '已处理',
  CLOSED: '已结案',
}

/** 前端中文状态 → 后端状态 */
const INCIDENT_STATUS_MAP_REVERSE = {
  '待处理': 'REPORTED',
  '处理中': 'PREDICTED',
  '已处理': 'CLEARED',
  '已结案': 'CLOSED',
}

/** 后端风险等级 → 前端中文 */
const RISK_LEVEL_MAP = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '严重',
}

/** 前端中文风险等级 → 后端 */
const RISK_LEVEL_MAP_REVERSE = {
  '低': 'LOW',
  '中': 'MEDIUM',
  '高': 'HIGH',
  '严重': 'CRITICAL',
}

// ====== 转换函数 ======

function mapIncidentStatus(status) {
  return INCIDENT_STATUS_MAP[status] || status
}

function mapIncidentStatusReverse(status) {
  return INCIDENT_STATUS_MAP_REVERSE[status] || status
}

function mapRiskLevel(level) {
  return RISK_LEVEL_MAP[level] || level
}

function mapRiskLevelReverse(level) {
  return RISK_LEVEL_MAP_REVERSE[level] || level
}

/** 格式化分钟数为前端显示格式 */
function formatMinutes(minutes) {
  if (minutes === null || minutes === undefined) return '-'
  return minutes + '分钟'
}

/** 格式化车道数为前端显示格式 */
function formatLanes(lanes) {
  if (lanes === null || lanes === undefined) return '-'
  return lanes + '条'
}

/** 格式化置信度为前端显示格式 */
function formatConfidence(value) {
  if (value === null || value === undefined) return '-'
  return (value * 100).toFixed(1) + '%'
}

/** 转换后端 Incident 对象 → 前端事故对象（列表用） */
function transformIncident(inc) {
  return {
    id: inc.id,
    caseNo: inc.incidentNo,
    type: inc.confirmedAccidentType || inc.initialAccidentType || '-',
    riskLevel: mapRiskLevel(inc.riskLevel),
    status: mapIncidentStatus(inc.status),
    confidence: formatConfidence(inc.confidence),
    description: inc.description || '',
    // 地点：后端扁平字段 → 前端嵌套对象
    location: {
      name: inc.locationName || '',
      area: inc.address || '',
      road: inc.roadName || '',
      lng: inc.longitude || 0,
      lat: inc.latitude || 0,
    },
    reporter: '',
    reporterId: inc.reportUserId,
    reportTime: inc.createdAt,
    // 预测信息
    congestionDuration: formatMinutes(inc.predictedCongestionMinutes),
    recoveryTime: formatMinutes(inc.predictedRecoveryMinutes),
    affectedLanes: formatLanes(inc.occupiedLanes),
    trafficFlow: inc.trafficFlow || '-',
    weather: inc.weather || '-',
    roadLevel: inc.roadLevel || '-',
    riskScore: inc.riskLevel ? { LOW: 20, MEDIUM: 40, HIGH: 70, CRITICAL: 90 }[inc.riskLevel] : 0,
    // 处置建议
    disposalAdvice: inc.suggestion || '',
    supportAdvice: '',
    aiExplanation: inc.explanation || '',
    needSupport: [],
    // 附件（列表接口不含附件，留空）
    images: [],
    processRecords: [],
  }
}

/** 转换后端 IncidentDetailResponse → 前端事故详情对象 */
function transformIncidentDetail(data) {
  const incident = data.incident || data
  const base = transformIncident(incident)

  // 附加附件（通过后端文件接口加载）
  const incidentId = data.incident?.id || base.id
  if (data.attachments && Array.isArray(data.attachments)) {
    base.images = data.attachments.map((a) => ({
      id: a.id,
      url: `/api/v1/incidents/${incidentId}/attachments/${a.id}/file`,
      name: a.originalFilename || a.fileName,
    }))
  }

  // 附加预测结果
  if (data.predictions && data.predictions.length > 0) {
    const pred = data.predictions[0]
    base.riskLevel = mapRiskLevel(pred.riskLevel)
    base.confidence = formatConfidence(pred.confidence)
    base.congestionDuration = formatMinutes(pred.congestionDurationMinutes)
    base.recoveryTime = formatMinutes(pred.recoveryDurationMinutes)
    base.disposalAdvice = pred.suggestions || base.disposalAdvice
    base.aiExplanation = pred.explanation || base.aiExplanation
    base.type = pred.accidentType || base.type
  }

  // 附加调度任务信息（含处置反馈）
  if (data.dispatchTasks && data.dispatchTasks.length > 0) {
    base.dispatchTaskId = data.dispatchTasks[0].id
    base.dispatchTasks = data.dispatchTasks.map((t) => ({
      id: t.id,
      status: t.status,
      feedback: t.feedback || '',
      vehicleType: t.taskType || '',
    }))
    // 取最近一条有反馈的作为处置反馈
    const feedbackTask = data.dispatchTasks.find((t) => t.feedback)
    base.dispatchFeedback = feedbackTask ? feedbackTask.feedback : ''
  }

  return base
}

// ====== API 函数 ======

/**
 * 事故列表
 * GET /api/v1/incidents
 * @param {{ page?: number, pageSize?: number, status?: string, riskLevel?: string, keyword?: string }} params
 */
export async function getAccidentList(params) {
  const backendParams = {}
  if (params) {
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
    if (params.status) backendParams.status = mapIncidentStatusReverse(params.status)
    if (params.riskLevel) backendParams.riskLevel = mapRiskLevelReverse(params.riskLevel)
    if (params.keyword) backendParams.keyword = params.keyword
  }
  const res = await request.get('/v1/incidents', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(transformIncident),
      total: res.data.totalElements || 0,
    },
  }
}

/**
 * 事故详情
 * GET /api/v1/incidents/{id}
 * @param {number} id
 */
export async function getAccidentDetail(id) {
  const res = await request.get(`/v1/incidents/${id}`)
  return {
    code: 200,
    data: transformIncidentDetail(res.data),
  }
}

/**
 * 上报事故
 * POST /api/v1/incidents
 * @param {object} data - 前端格式的事故数据
 */
export async function addAccident(data) {
  // 前端格式 → 后端 CreateIncidentRequest
  const body = {
    locationName: data.location?.name || '',
    address: data.location?.area || '',
    roadName: data.location?.road || '',
    longitude: data.location?.lng,
    latitude: data.location?.lat,
    coordinateType: data.coordinateType || null,
    initialAccidentType: data.accidentType || '',
    description: data.description || '',
    reportUserId: data.reporterId || null,
  }

  const res = await request.post('/v1/incidents', body)
  return {
    code: 200,
    data: {
      id: res.data.id,
      caseNo: res.data.incidentNo,
    },
  }
}

/**
 * 上报事故（含附件，multipart）
 * POST /api/v1/incidents/with-attachments
 * @param {object} data - 前端格式的事故数据，images 中需含 raw File 对象
 */
export async function addAccidentWithAttachments(data) {
  const formData = new FormData()

  // 构建 CreateIncidentRequest JSON
  const body = {
    locationName: data.location?.name || '',
    address: data.location?.area || '',
    roadName: data.location?.road || '',
    longitude: data.location?.lng,
    latitude: data.location?.lat,
    coordinateType: data.coordinateType || null,
    initialAccidentType: data.accidentType || '',
    description: data.description || '',
    reportUserId: data.reporterId || null,
    occupiedLanes: data.occupiedLanes || null,
  }
  formData.append('incident', new Blob([JSON.stringify(body)], { type: 'application/json' }))

  // 添加文件
  const files = (data.images || []).filter((img) => img.raw)
  files.forEach((img) => {
    formData.append('files', img.raw, img.name)
  })

  const res = await request.post('/v1/incidents/with-attachments', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

  // with-attachments 返回 IncidentDetailResponse: { incident, attachments, ... }
  return {
    code: 200,
    data: {
      id: res.data.incident?.id || res.data.id,
      caseNo: res.data.incident?.incidentNo || res.data.incidentNo,
    },
  }
}

// ====== PublicIncidentSubmitResponse 转换 ======

/** 转换后端 PublicIncidentSubmitResponse → 前端扁平结构 */
function transformPublicReportResponse(data) {
  // 从附件中提取 AI 检测事故类型
  const attachments = data.incidentDetail?.attachments || [];
  const aiDetected = attachments
    .filter(a => a.aiDetectedTypes)
    .flatMap(a => (a.aiDetectedTypes || '').split(','))
    .filter(t => t.trim())
    .join(', ');

  return {
    incidentDetail: data.incidentDetail ? transformIncidentDetail(data.incidentDetail) : null,
    aiDetectedType: aiDetected || null,
    immediateAdvice: data.immediateAdvice ? {
      calmingMessage: data.immediateAdvice.calmingMessage || '',
      immediateAdvice: data.immediateAdvice.immediateAdvice || '',
      actionItems: data.immediateAdvice.actionItems || [],
      casualtyDetected: !!data.immediateAdvice.casualtyDetected,
      call120Required: !!data.immediateAdvice.call120Required,
      emergencyPhone: data.immediateAdvice.emergencyPhone || '',
      aiGenerated: !!data.immediateAdvice.aiGenerated,
    } : null,
    estimatedPoliceArrivalMinutes: data.estimatedPoliceArrivalMinutes ?? null,
    estimatedPoliceArrivalText: data.estimatedPoliceArrivalText || '',
    predictionSubmit: data.predictionSubmit ? {
      submitted: !!data.predictionSubmit.submitted,
      status: data.predictionSubmit.status || '',
      message: data.predictionSubmit.message || '',
      dataModuleTraceId: data.predictionSubmit.dataModuleTraceId || '',
    } : null,
  }
}

/**
 * 公共事故上报（JSON，无附件）
 * POST /api/v1/incidents/public-report
 * @param {object} data - 前端格式的事故数据
 * @returns {Promise<{code: number, data: object}>}
 */
export async function publicReport(data) {
  const body = {
    locationName: data.location?.name || '',
    address: data.location?.area || '',
    roadName: data.location?.road || '',
    longitude: data.location?.lng,
    latitude: data.location?.lat,
    coordinateType: data.coordinateType || null,
    initialAccidentType: data.accidentType || '',
    description: data.description || '',
    reportUserId: data.reporterId || null,
    occupiedLanes: data.occupiedLanes || null,
    peopleInvolved: data.peopleInvolved || null,
    injuredCount: data.injuredCount || null,
    injuryEstimate: data.injuryEstimate || '',
  }
  const res = await request.post('/v1/incidents/public-report', body)
  return {
    code: 200,
    data: transformPublicReportResponse(res.data),
  }
}

/**
 * 公共事故上报（Multipart，含照片/视频）
 * POST /api/v1/incidents/public-report (multipart)
 * @param {object} data - 前端格式的事故数据，images 中需含 raw File 对象
 * @returns {Promise<{code: number, data: object}>}
 */
export async function publicReportWithAttachments(data) {
  const formData = new FormData()

  // 构建 CreateIncidentRequest JSON
  const body = {
    locationName: data.location?.name || '',
    address: data.location?.area || '',
    roadName: data.location?.road || '',
    longitude: data.location?.lng,
    latitude: data.location?.lat,
    coordinateType: data.coordinateType || null,
    initialAccidentType: data.accidentType || '',
    description: data.description || '',
    reportUserId: data.reporterId || null,
    occupiedLanes: data.occupiedLanes || null,
    peopleInvolved: data.peopleInvolved || null,
    injuredCount: data.injuredCount || null,
    injuryEstimate: data.injuryEstimate || '',
  }
  formData.append('incident', new Blob([JSON.stringify(body)], { type: 'application/json' }))

  // 照片 → backend consumes "photos"
  const photos = (data.images || []).filter((img) => img.raw)
  photos.forEach((img) => {
    formData.append('photos', img.raw, img.name)
  })

  // 视频 → backend consumes "videos"
  if (data.video?.raw) {
    formData.append('videos', data.video.raw, data.video.name)
  }

  const res = await request.post('/v1/incidents/public-report', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

  return {
    code: 200,
    data: transformPublicReportResponse(res.data),
  }
}

/**
 * 事故历史记录（管理员用）
 * GET /api/v1/admin/incidents/history
 * @param {{ page?: number, pageSize?: number, status?: string, riskLevel?: string, accidentType?: string, keyword?: string }} params
 */
export async function getAccidentHistory(params) {
  const backendParams = {}
  if (params) {
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
    if (params.status) backendParams.status = mapIncidentStatusReverse(params.status)
    if (params.riskLevel) backendParams.riskLevel = mapRiskLevelReverse(params.riskLevel)
    if (params.accidentType) backendParams.accidentType = params.accidentType
    if (params.keyword) backendParams.keyword = params.keyword
  }
  const res = await request.get('/v1/admin/incidents/history', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(transformIncident),
      total: res.data.totalElements || 0,
      page: res.data.number || 0,
      size: res.data.size || 20,
    },
  }
}

/**
 * 更新事故 — 后端暂无此接口，保留占位
 */
export async function updateAccident() {
  console.warn('[accident] updateAccident: 后端暂无事故更新接口')
  return { code: 200, data: null }
}
