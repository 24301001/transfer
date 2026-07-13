import request from '../request'

/**
 * 管理员系统健康详情
 * GET /api/v1/admin/health
 * 需要 ADMIN 角色 JWT，建议 20~30 秒刷新一次
 *
 * @returns {Promise<{
 *   status: string,            // UP | DEGRADED | DOWN
 *   statusMessage: string,     // 中文状态描述
 *   checkedAt: string,         // ISO 检测时间
 *   uptimeSeconds: number,
 *   uptime: string,            // "14天 6小时 56分钟 7秒"
 *   application: { name, version, activeProfiles, javaVersion, springBootVersion, timezone },
 *   server: { hostName, port, availableProcessors, systemLoadAverage, operatingSystem, architecture },
 *   resources: {
 *     heapUsedBytes, heapCommittedBytes, heapMaxBytes, heapUsagePercent,
 *     nonHeapUsedBytes,
 *     diskTotalBytes, diskUsableBytes, diskUsagePercent,
 *     processCpuUsagePercent, systemCpuUsagePercent
 *   },
 *   components: Record<string, { status, configured, responseTimeMs, message, details }>,
 *   businessMetrics: {
 *     totalUsers, enabledUsers, disabledUsers,
 *     totalIncidents, activeIncidents, closedIncidents,
 *     totalDispatchTasks, activeDispatchTasks, completedDispatchTasks,
 *     totalEmergencyVehicles, availableEmergencyVehicles, outOfServiceEmergencyVehicles
 *   },
 *   warnings: string[]
 * }>}
 */
export async function getSystemHealth() {
  const res = await request.get('/v1/admin/health')
  // 后端直接返回 AdminHealthResponse 记录体，包装为统一格式
  return {
    code: 200,
    data: res.data,
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
