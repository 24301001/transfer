import Mock from 'mockjs'

// ====== 操作日志模拟 ======
Mock.mock('/api/system/logs', 'get', (options) => {
  const url = new URL(options.url, 'http://localhost')
  const page = +url.searchParams.get('page') || 1
  const pageSize = +url.searchParams.get('pageSize') || 20

  const actions = ['登录系统', '事故上报', '查看事故详情', '创建调度任务', '更新任务状态', '修改用户信息', '导出数据', '系统配置修改']
  const users = ['张警官', '李指挥', '王队长', '赵管理', '刘警官', '陈师傅']
  const modules = ['认证模块', '事故模块', '调度模块', '系统管理']

  const list = Array.from({ length: 50 }, (_, i) => ({
    id: i + 1,
    time: Mock.Random.datetime('2026-07-0' + Mock.Random.natural(1, 6) + ' HH:mm:ss'),
    user: Mock.Random.pick(users),
    action: Mock.Random.pick(actions),
    module: Mock.Random.pick(modules),
    ip: Mock.Random.ip(),
    detail: Mock.Random.sentence(5, 12),
  }))

  const start = (page - 1) * pageSize
  const paged = list.slice(start, start + pageSize)

  return { code: 200, message: 'ok', data: { list: paged, total: list.length } }
})

// ====== 系统健康状态模拟 ======
Mock.mock('/api/system/health', 'get', () => {
  return {
    code: 200,
    message: 'ok',
    data: {
      overall: 'healthy',
      services: [
        { name: 'Web 服务', status: 'healthy', uptime: '15天 6小时', responseTime: '45ms' },
        { name: '数据库', status: 'healthy', uptime: '30天 12小时', responseTime: '12ms' },
        { name: '图像识别服务', status: 'healthy', uptime: '7天 3小时', responseTime: '230ms' },
        { name: 'DeepSeek 大模型', status: 'healthy', uptime: '15天', responseTime: '580ms' },
        { name: '短信服务', status: 'degraded', uptime: '15天', responseTime: '1200ms' },
        { name: '百度地图 API', status: 'healthy', uptime: '30天', responseTime: '65ms' },
        { name: '邮件服务', status: 'healthy', uptime: '30天', responseTime: '320ms' },
        { name: '缓存服务', status: 'healthy', uptime: '15天', responseTime: '2ms' },
      ],
      stats: {
        totalAccidents: 156,
        todayAccidents: 7,
        pendingTasks: 3,
        activeUsers: 12,
        cpuUsage: '32%',
        memoryUsage: '56%',
        diskUsage: '41%',
      },
    },
  }
})

// ====== 基础数据统计模拟 ======
Mock.mock('/api/system/data', 'get', () => {
  const accidentByType = {
    追尾事故: { count: 48, trend: 'up' },
    车辆碰撞: { count: 35, trend: 'down' },
    道路封闭: { count: 12, trend: 'stable' },
    施工占道: { count: 28, trend: 'up' },
    车辆自燃: { count: 8, trend: 'stable' },
    护栏损坏: { count: 15, trend: 'down' },
    货物散落: { count: 6, trend: 'stable' },
    涉水事故: { count: 4, trend: 'down' },
  }

  const riskDistribution = {
    低: 42,
    中: 58,
    高: 36,
    严重: 20,
  }

  const monthlyStats = Array.from({ length: 6 }, (_, i) => {
    const m = new Date()
    m.setMonth(m.getMonth() - 5 + i)
    return {
      month: `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`,
      accidents: Mock.Random.natural(20, 40),
      avgResponseTime: Mock.Random.natural(8, 25) + 'min',
    }
  })

  return {
    code: 200,
    message: 'ok',
    data: {
      accidentByType,
      riskDistribution,
      monthlyStats,
      totalAccidents: 156,
      totalDispatches: 98,
      successRate: '94.5%',
      avgProcessTime: '32分钟',
    },
  }
})
