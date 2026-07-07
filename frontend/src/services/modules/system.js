import request from '../request'

/**
 * 操作日志
 * GET /api/v1/admin/operation-logs
 * @param {{ page?: number, pageSize?: number }} params
 */
export async function getSystemLogs(params) {
  const backendParams = {}
  if (params) {
    if (params.page) backendParams.page = params.page - 1
    if (params.pageSize) backendParams.size = params.pageSize
  }
  const res = await request.get('/v1/admin/operation-logs', { params: backendParams })
  return {
    code: 200,
    data: {
      list: (res.data.content || []).map((log) => ({
        id: log.id,
        time: log.createdAt,
        user: String(log.operatorUserId || ''),
        module: log.objectType || '',
        action: log.operationType || '',
        detail: log.detail || '',
        ip: log.ipAddress || '',
      })),
      total: res.data.totalElements || 0,
    },
  }
}

/**
 * 系统健康检查
 * GET /api/health
 * 后端返回 { status, time, dependencies }，前端需要 { overall, services, stats }
 */
export async function getSystemHealth() {
  const res = await request.get('/health')
  const raw = res.data || {}
  // 转换 dependencies Map → services 数组
  const services = Object.entries(raw.dependencies || {}).map(([name, status]) => ({
    name,
    status: status === 'UP' || status === 'healthy' ? 'healthy' : 'degraded',
    uptime: '-',
    responseTime: '-',
  }))
  return {
    code: 200,
    data: {
      overall: raw.status === 'UP' || raw.status === 'healthy' ? 'healthy' : 'degraded',
      services: services.length > 0 ? services : [
        { name: 'Web 服务', status: 'healthy', uptime: '-', responseTime: '-' },
        { name: '数据库', status: 'healthy', uptime: '-', responseTime: '-' },
      ],
      stats: {
        totalAccidents: 0,
        todayAccidents: 0,
        pendingTasks: 0,
        activeUsers: 0,
        cpuUsage: '-',
        memoryUsage: '-',
        diskUsage: '-',
      },
    },
  }
}

/**
 * 系统统计数据 — 后端暂无此接口，返回空数据
 */
export async function getSystemData() {
  console.warn('[system] getSystemData: 后端暂无统计数据接口')
  return {
    code: 200,
    data: {
      accidentByType: {},
      riskDistribution: {},
      monthlyStats: [],
      totalAccidents: 0,
      totalDispatches: 0,
      successRate: '-',
      avgProcessTime: '-',
    },
  }
}
