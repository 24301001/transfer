import request from '../request'

// ====== 枚举映射工具 ======

/** 后端角色 → 前端角色 */
const ROLE_MAP = {
  FIELD_OFFICER: 'POLICE',
  COMMAND_CENTER: 'COMMAND',
  RESCUE_WORKER: 'RESCUE',
  ADMIN: 'ADMIN',
}

/** 前端角色 → 后端角色 */
const ROLE_MAP_REVERSE = {
  POLICE: 'FIELD_OFFICER',
  COMMAND: 'COMMAND_CENTER',
  RESCUE: 'RESCUE_WORKER',
  ADMIN: 'ADMIN',
}

/** 后端用户状态 → 前端中文状态 */
const STATUS_MAP = {
  ENABLED: '启用',
  DISABLED: '禁用',
}

/** 前端中文状态 → 后端状态 */
const STATUS_MAP_REVERSE = {
  '启用': 'ENABLED',
  '禁用': 'DISABLED',
}

function mapRoleToFrontend(backendRole) {
  return ROLE_MAP[backendRole] || backendRole
}

function mapRoleToBackend(frontendRole) {
  return ROLE_MAP_REVERSE[frontendRole] || frontendRole
}

function mapStatusToFrontend(backendStatus) {
  return STATUS_MAP[backendStatus] || backendStatus
}

function mapStatusToBackend(frontendStatus) {
  return STATUS_MAP_REVERSE[frontendStatus] || frontendStatus
}

/** 转换后端用户对象 → 前端用户对象 */
function transformUser(user) {
  return {
    id: user.id,
    username: user.username,
    nickname: user.fullName || user.nickname,
    phone: user.phone || '',
    email: user.email || '',
    role: mapRoleToFrontend(user.role),
    status: mapStatusToFrontend(user.status),
    createTime: user.createdAt,
    updateTime: user.updatedAt,
  }
}

/** 转换后端登录/注册响应 → 前端格式 */
function transformLoginResponse(data) {
  return {
    token: data.token,
    userInfo: {
      id: data.user.id,
      username: data.user.username,
      nickname: data.user.fullName,
      role: mapRoleToFrontend(data.user.role),
      status: mapStatusToFrontend(data.user.status),
    },
  }
}

// ====== API 函数 ======

/**
 * 登录
 * POST /api/v1/auth/login
 * @param {{ username: string, password: string }} data
 */
export async function login(data) {
  const res = await request.post('/v1/auth/login', data)
  return {
    code: 200,
    data: transformLoginResponse(res.data),
  }
}

/**
 * 注册
 * POST /api/v1/auth/register
 * @param {{ username: string, nickname: string, password: string, role: string }} data
 */
export async function register(data) {
  const res = await request.post('/v1/auth/register', {
    fullName: data.nickname,
    username: data.username,
    password: data.password,
    role: mapRoleToBackend(data.role),
  })
  return {
    code: 200,
    data: transformLoginResponse(res.data),
  }
}

/**
 * 获取当前用户信息
 * GET /api/v1/auth/me
 */
export async function getUserInfo() {
  const res = await request.get('/v1/auth/me')
  return {
    code: 200,
    data: transformUser(res.data),
  }
}

/**
 * 用户列表
 * GET /api/v1/admin/users
 * @param {{ page?: number, pageSize?: number }} params
 */
export async function getUserList(params) {
  const backendParams = {}
  if (params) {
    // 页码转换：前端从1开始 → 后端从0开始
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
  }
  const res = await request.get('/v1/admin/users', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map(transformUser),
      total: res.data.totalElements || 0,
    },
  }
}

/**
 * 更新用户
 * PUT /api/v1/admin/users/{id}
 * @param {{ id: number, nickname?: string, role?: string, status?: string, password?: string }} data
 */
export async function updateUser(data) {
  const { id, ...rest } = data
  const body = {}
  if (rest.nickname !== undefined) body.fullName = rest.nickname
  if (rest.role !== undefined) body.role = mapRoleToBackend(rest.role)
  if (rest.status !== undefined) body.status = mapStatusToBackend(rest.status)
  if (rest.password !== undefined) body.password = rest.password
  if (rest.phone !== undefined) body.phone = rest.phone
  if (rest.email !== undefined) body.email = rest.email

  await request.put(`/v1/admin/users/${id}`, body)
  return { code: 200, data: null }
}

/**
 * 删除用户
 * DELETE /api/v1/admin/users/{id}
 * @param {{ id: number }} data
 */
export async function deleteUser(data) {
  await request.delete(`/v1/admin/users/${data.id}`)
  return { code: 200, data: null }
}
