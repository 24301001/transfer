import request from '../request'

// ====== ????????? ======

const ROLE_MAP = {
  FIELD_OFFICER: 'POLICE',
  COMMAND_CENTER: 'COMMAND',
  RESCUE_WORKER: 'RESCUE',
  ADMIN: 'ADMIN',
}

const ROLE_MAP_REVERSE = {
  POLICE: 'FIELD_OFFICER',
  COMMAND: 'COMMAND_CENTER',
  RESCUE: 'RESCUE_WORKER',
  ADMIN: 'ADMIN',
}

const STATUS_MAP = {
  ENABLED: '???',
  DISABLED: '???',
}

const STATUS_MAP_REVERSE = {
  '???': 'ENABLED',
  '???': 'DISABLED',
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

function transformLoginResponse(data) {
  return {
    token: data.token,
    userInfo: {
      id: data.user.id,
      username: data.user.username,
      nickname: data.user.fullName,
      email: data.user.email || '',
      phone: data.user.phone || '',
      role: mapRoleToFrontend(data.user.role),
      status: mapStatusToFrontend(data.user.status),
    },
  }
}

// ====== ????? ======

/**
 * ???????????
 * GET /api/v1/auth/captcha
 * @returns {Promise<{captchaId: string, imageBase64: string, expireSeconds: number}>}
 */
export async function getCaptcha() {
  const res = await request.get('/v1/auth/captcha')
  return res.data
}

/**
 * ??????????????
 * POST /api/v1/auth/slider-captcha/challenge
 * @returns {Promise<SliderCaptchaChallenge>}
 */
export async function getSliderCaptchaChallenge() {
  const res = await request.post('/v1/auth/slider-captcha/challenge')
  return res.data
}

/**
 * ?????????????????
 * POST /api/v1/auth/slider-captcha/verify
 * @param {{ captchaId: string, sliderX: number }} data
 * @returns {Promise<{sliderToken: string, expireSeconds: number}>}
 */
export async function verifySliderCaptcha(data) {
  const res = await request.post('/v1/auth/slider-captcha/verify', {
    captchaId: data.captchaId,
    sliderX: data.sliderX,
  })
  return res.data
}

/**
 * ????????????????????????????? sliderToken??
 * POST /api/v1/auth/email-code
 * @param {{ purpose: string, username?: string, email?: string, sliderToken: string }} data
 * @returns {Promise<{message: string, expireSeconds: number, devCode?: string}>}
 */
export async function sendEmailCode(data) {
  const res = await request.post('/v1/auth/email-code', {
    purpose: data.purpose,
    username: data.username || undefined,
    email: data.email || undefined,
    sliderToken: data.sliderToken,
  })
  return res.data
}

// ====== ??? ======

/**
 * ???
 * POST /api/v1/auth/login
 * @param {{ username: string, password: string }} data
 */
export async function login(data) {
  const res = await request.post('/v1/auth/login', {
    username: data.username,
    password: data.password,
    sliderToken: data.sliderToken,
  })
  return {
    code: 200,
    data: transformLoginResponse(res.data),
  }
}

/**
 * ???
 * POST /api/v1/auth/register
 * @param {{ fullName: string, username: string, phone?: string, email: string, role: string, password: string, emailCode: string, sliderToken?: string }} data
 */
export async function register(data) {
  const res = await request.post('/v1/auth/register', {
    fullName: data.nickname || data.fullName,
    username: data.username,
    phone: data.phone || undefined,
    email: data.email,
    role: mapRoleToBackend(data.role),
    password: data.password,
    emailCode: data.emailCode,
    sliderToken: data.sliderToken,
  })
  return {
    code: 200,
    data: transformLoginResponse(res.data),
  }
}

/**
 * ??????
 * POST /api/v1/auth/password/reset
 */
export async function resetPassword(data) {
  const res = await request.post('/v1/auth/password/reset', {
    username: data.username,
    email: data.email,
    newPassword: data.newPassword,
    emailCode: data.emailCode,
  })
  return { code: 200, data: res.data }
}

/**
 * ????????????
 * GET /api/v1/auth/me
 */
export async function getUserInfo() {
  const res = await request.get('/v1/auth/me')
  return {
    code: 200,
    data: transformUser(res.data),
  }
}

// ====== ?????? ======

/**
 * ?????????
 * GET /api/v1/profile
 */
export async function getProfile() {
  const res = await request.get('/v1/profile')
  return {
    code: 200,
    data: transformUser(res.data),
  }
}

/**
 * ??????
 * PUT /api/v1/profile/name
 * @param {{ fullName: string }} data
 */
export async function updateProfileName(data) {
  const res = await request.put('/v1/profile/name', {
    fullName: data.fullName,
  })
  return {
    code: 200,
    data: transformUser(res.data),
  }
}

/**
 * ????????????????????????????
 * POST /api/v1/profile/password/email-code
 */
export async function sendProfilePasswordEmailCode(data) {
  const res = await request.post('/v1/profile/password/email-code', {
    captchaId: data.captchaId,
    captchaCode: data.captchaCode,
  })
  return res.data
}

/**
 * ???????????????
 * PUT /api/v1/profile/password
 */
export async function changeProfilePassword(data) {
  const res = await request.put('/v1/profile/password', {
    oldPassword: data.oldPassword,
    newPassword: data.newPassword,
    emailCode: data.emailCode,
    captchaId: data.captchaId,
    captchaCode: data.captchaCode,
  })
  return { code: 200, data: res.data }
}

// ====== ?????????????? ======

/**
 * ??????
 * GET /api/v1/admin/users
 */
export async function getUserList(params) {
  const backendParams = {}
  if (params) {
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
 * ??????
 * PUT /api/v1/admin/users/{id}
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
 * ??????
 * DELETE /api/v1/admin/users/{id}
 */
export async function deleteUser(data) {
  await request.delete(`/v1/admin/users/${data.id}`)
  return { code: 200, data: null }
}
