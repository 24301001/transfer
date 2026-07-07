import request from '../request'

// ====== 枚举映射 ======

/** 后端任务类型 → 前端车辆类型 */
const TASK_TYPE_MAP = {
  POLICE: '警车',
  CLEARANCE: '清障车',
  RESCUE: '救援车',
  MEDICAL: '救护车',
}

/** 前端车辆类型 → 后端任务类型 */
const TASK_TYPE_MAP_REVERSE = {
  '警车': 'POLICE',
  '清障车': 'CLEARANCE',
  '救援车': 'RESCUE',
  '救护车': 'MEDICAL',
  '救援': 'RESCUE',
  '工程车': 'CLEARANCE', // 后端没有工程车，映射为清障
}

/** 后端任务状态 → 前端中文 */
const TASK_STATUS_MAP = {
  DISPATCHED: '待接收',
  DEPARTED: '已出发',
  ARRIVED: '已到达',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

/** 前端中文状态 → 后端 */
const TASK_STATUS_MAP_REVERSE = {
  '待接收': 'DISPATCHED',
  '已出发': 'DEPARTED',
  '已到达': 'ARRIVED',
  '处理中': 'PROCESSING',
  '已完成': 'COMPLETED',
  '已取消': 'CANCELLED',
}

/** 前端风险等级 → 后端 */
const RISK_LEVEL_MAP_REVERSE = {
  '低': 'LOW',
  '中': 'MEDIUM',
  '高': 'HIGH',
  '严重': 'CRITICAL',
}

/** 前端救援人员名 → ID 映射（后端数据库自动生成，此处为硬编码占位） */
const RESCUE_USER_MAP = {
  '王队长': 3,   // rescue01
  '陈师傅': 3,
  '刘师傅': 3,
  '张师傅': 3,
}

/** 前端指挥人员名 → ID 映射 */
const COMMAND_USER_MAP = {
  '李指挥': 2,  // command01
}

// ====== 转换函数 ======

function mapTaskType(type) {
  return TASK_TYPE_MAP[type] || type
}

function mapTaskTypeReverse(type) {
  return TASK_TYPE_MAP_REVERSE[type] || type
}

function mapTaskStatus(status) {
  return TASK_STATUS_MAP[status] || status
}

function mapTaskStatusReverse(status) {
  return TASK_STATUS_MAP_REVERSE[status] || status
}

function mapRiskLevelReverse(level) {
  return RISK_LEVEL_MAP_REVERSE[level] || level
}

/** 转换后端 DispatchTask → 前端调度任务对象 */
function transformTask(task) {
  return {
    id: task.id,
    taskNo: task.taskNo,
    accidentId: task.incidentId,
    caseNo: '',
    accidentType: '',
    riskLevel: task.riskLevel ? { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }[task.riskLevel] : '-',
    location: {
      name: task.locationName || '',
      road: '',
      area: '',
    },
    status: mapTaskStatus(task.status),
    taskType: task.taskType,
    vehicleType: mapTaskType(task.taskType),
    vehiclePlate: '',
    assignedTo: '',
    assignedToId: task.receiverUserId,
    assigner: '',
    assignerId: task.assignedByUserId,
    createTime: task.createdAt,
    updateTime: task.updatedAt,
    feedback: task.feedback || '',
    notes: task.advice || '',
    vehicleRequired: task.vehicleRequired,
  }
}

// ====== API 函数 ======

/**
 * 调度任务列表
 * GET /api/v1/dispatch-tasks
 * @param {{ page?: number, pageSize?: number, status?: string }} params
 */
export async function getDispatchList(params) {
  const backendParams = {}
  if (params) {
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
    if (params.status) backendParams.status = mapTaskStatusReverse(params.status)
  }
  const res = await request.get('/v1/dispatch-tasks', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(transformTask),
      total: res.data.totalElements || 0,
    },
  }
}

/**
 * 调度任务详情 — 后端暂无 GET /{id} 接口，从列表过滤
 * @param {number} id
 */
export async function getDispatchDetail(id) {
  // 获取足够大的列表然后过滤
  const res = await request.get('/v1/dispatch-tasks', {
    params: { page: 0, size: 100 },
  })
  const task = (res.data.content || []).find((t) => t.id === Number(id))
  if (!task) {
    console.warn('[dispatch] 任务 #' + id + ' 未找到')
    return { code: 404, data: null }
  }
  return {
    code: 200,
    data: transformTask(task),
  }
}

/**
 * 创建调度任务
 * POST /api/v1/dispatch-tasks
 * @param {object} data - 前端格式的调度任务数据
 */
export async function createDispatch(data) {
  const body = {
    incidentId: data.accidentId,
    taskType: mapTaskTypeReverse(data.vehicleType || '清障车'),
    receiverUserId: RESCUE_USER_MAP[data.assignedTo] || data.assignedToId || null,
    assignedByUserId: COMMAND_USER_MAP[data.assigner] || data.assignerId || null,
    vehicleType: data.vehicleType || '',
    advice: data.notes || '',
  }

  const res = await request.post('/v1/dispatch-tasks', body)
  return {
    code: 200,
    data: { id: res.data.id },
  }
}

/**
 * 更新调度任务状态
 * PUT /api/v1/dispatch-tasks/{id}/status
 * @param {{ id: number, status: string, feedback?: string }} data
 */
export async function updateDispatchStatus(data) {
  const body = {
    status: mapTaskStatusReverse(data.status),
  }
  if (data.feedback) {
    body.feedback = data.feedback
  }

  const res = await request.put(`/v1/dispatch-tasks/${data.id}/status`, body)
  return {
    code: 200,
    data: res.data ? transformTask(res.data) : null,
  }
}
