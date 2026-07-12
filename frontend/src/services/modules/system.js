import request from '../request'

/**
 * 系统健康检查
 * GET /api/health
 * @returns {Promise<{status: string, time: string, dependencies: object}>}
 */
export async function getSystemHealth() {
  const res = await request.get('/health')
  return {
    code: 200,
    data: {
      status: res.data.status,
      time: res.data.time,
      dependencies: res.data.dependencies || {},
    },
  }
}

/**
 * 操作日志（仅用户账号操作）
 * GET /api/v1/admin/operation-logs
 * @param {{ page?: number, pageSize?: number, operationType?: string, keyword?: string, startTime?: string, endTime?: string }} params
 */
export async function getSystemLogs(params) {
  const backendParams = {}
  if (params) {
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
    if (params.operationType) backendParams.operationType = params.operationType
    if (params.keyword) backendParams.keyword = params.keyword
    if (params.startTime) backendParams.startTime = params.startTime
    if (params.endTime) backendParams.endTime = params.endTime
  }
  const res = await request.get('/v1/admin/operation-logs', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map((log) => {
        // detail 格式: "username, role=..., status=..."  或 "username"
        const detail = log.detail || ''
        const targetUser = detail.split(',')[0] || ''
        return {
          id: log.id,
          time: log.createdAt,
          operatorUserId: log.operatorUserId,
          operatorName: String(log.operatorUserId || ''),
          objectType: log.objectType || '',
          action: log.operationType || '',
          detail: detail,
          targetUser: targetUser,
          ip: log.ipAddress || '',
          objectId: log.objectId || '',
        }
      }),
      total: res.data.totalElements || 0,
    },
  }
}
