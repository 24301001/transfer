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

// ====== 管理员系统健康详情模拟 ======
Mock.mock(/\/api\/v1\/admin\/health/, 'get', () => {
  return {
    code: 200,
    message: 'ok',
    data: {
      status: 'UP',
      statusMessage: '系统运行正常',
      checkedAt: '2026-07-13T10:30:00',
      uptimeSeconds: 1234567,
      uptime: '14天 6小时 56分钟 7秒',
      application: {
        name: 'traffic-risk-backend',
        version: '0.0.1-SNAPSHOT',
        activeProfiles: ['dev', 'mock'],
        javaVersion: '17.0.5',
        springBootVersion: '3.1.0',
        timezone: 'Asia/Shanghai',
      },
      server: {
        hostName: 'dev-server-01',
        port: 8080,
        availableProcessors: 8,
        systemLoadAverage: 1.52,
        operatingSystem: 'Windows 11 10.0',
        architecture: 'amd64',
      },
      resources: {
        heapUsedBytes: 268435456,
        heapCommittedBytes: 536870912,
        heapMaxBytes: 1073741824,
        heapUsagePercent: 25.0,
        nonHeapUsedBytes: 83886080,
        diskTotalBytes: 500000000000,
        diskUsableBytes: 300000000000,
        diskUsagePercent: 40.0,
        processCpuUsagePercent: 12.5,
        systemCpuUsagePercent: 45.2,
      },
      components: {
        database: {
          status: 'UP',
          configured: true,
          responseTimeMs: 45,
          message: '数据库连接正常',
          details: { product: 'H2', version: '2.2.224', driver: 'H2 JDBC Driver', readOnly: false },
        },
        redis: {
          status: 'UP',
          configured: true,
          responseTimeMs: 12,
          message: 'Redis 连接正常',
          details: { ping: 'PONG' },
        },
        predictionModule: {
          status: 'UP',
          configured: true,
          responseTimeMs: 230,
          message: '事故预测模块响应正常',
          details: {},
        },
        yoloService: {
          status: 'NOT_CONFIGURED',
          configured: false,
          responseTimeMs: null,
          message: 'YOLO 服务地址未配置',
          details: {},
        },
        siliconFlowAi: {
          status: 'UP',
          configured: true,
          responseTimeMs: null,
          message: '硅基流动 AI 已配置（未执行外部连通性探测）',
          details: { model: 'DeepSeek-V3' },
        },
        baiduMap: {
          status: 'UP',
          configured: true,
          responseTimeMs: null,
          message: '百度地图已配置（未执行外部连通性探测）',
          details: { serverKeyConfigured: true, browserKeyConfigured: true },
        },
        mail: {
          status: 'UP',
          configured: true,
          responseTimeMs: null,
          message: '邮件服务已配置（未执行外部连通性探测）',
          details: { host: 'smtp.example.com', account: 'n***@example.com' },
        },
      },
      businessMetrics: {
        totalUsers: 100,
        enabledUsers: 95,
        disabledUsers: 5,
        totalIncidents: 156,
        activeIncidents: 12,
        closedIncidents: 140,
        totalDispatchTasks: 98,
        activeDispatchTasks: 4,
        completedDispatchTasks: 90,
        totalEmergencyVehicles: 20,
        availableEmergencyVehicles: 15,
        outOfServiceEmergencyVehicles: 2,
      },
      warnings: [],
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
