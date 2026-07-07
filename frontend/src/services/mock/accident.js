import Mock from 'mockjs'
import { mockUsers } from './user'

// ====== 事故类型枚举 ======
const ACCIDENT_TYPES = ['追尾事故', '车辆碰撞', '道路封闭', '施工占道', '车辆自燃', '护栏损坏', '货物散落', '涉水事故']

const RISK_LEVELS = ['低', '中', '高', '严重']
const ACCIDENT_STATUS = ['待处理', '处理中', '已处理', '已结案']

// ====== 生成模拟事故数据 ======
function generateAccident(id) {
  const type = ACCIDENT_TYPES[Mock.Random.natural(0, ACCIDENT_TYPES.length - 1)]
  const riskIdx = Mock.Random.natural(0, 3)
  const riskLevel = RISK_LEVELS[riskIdx]
  const status = ACCIDENT_STATUS[Mock.Random.natural(0, 3)]
  const lanes = Mock.Random.natural(1, 4)
  const congestionMin = Mock.Random.natural(15, 120)
  const recoveryMin = Mock.Random.natural(congestionMin + 10, congestionMin + 60)
  const reporter = mockUsers.filter((u) => u.role === 'POLICE')
  const reporterUser = reporter[Mock.Random.natural(0, reporter.length - 1)]

  return {
    id,
    caseNo: `A${String(new Date().getFullYear())}${String(Mock.Random.natural(1000, 9999))}`,
    type,
    riskLevel,
    riskScore: Mock.Random.natural(20, 99),
    status,
    confidence: Mock.Random.float(72, 98, 0, 1).toFixed(1) + '%',
    description: Mock.Random.pick([
      '小型轿车与SUV发生追尾，占用左侧车道，无人员受伤',
      '两辆货车发生侧面碰撞，占用中间两条车道，有人员轻伤',
      '道路施工占道，导致双向各仅剩一条车道通行',
      '货车货物散落，占用全部车道，需吊车清理',
      '车辆自燃，火势已控制，占用应急车道和部分行车道',
      '护栏损坏，车辆单方事故，占用紧急停车带',
    ]),
    location: Mock.Random.pick([
      { name: 'G15 沈海高速 K1200+500 段（北向）', road: 'G15 沈海高速', area: '浦东新区', lat: 31.25, lng: 121.55 },
      { name: 'G2 京沪高速 K980+200 段（南向）', road: 'G2 京沪高速', area: '嘉定区', lat: 31.32, lng: 121.28 },
      { name: 'S20 外环高速 K85+300 段（内圈）', road: 'S20 外环高速', area: '闵行区', lat: 31.12, lng: 121.38 },
      { name: '中环路 汶水路段（西向）', road: '中环路', area: '普陀区', lat: 31.26, lng: 121.40 },
      { name: '南北高架 北京路段（南向）', road: '南北高架', area: '黄浦区', lat: 31.23, lng: 121.47 },
      { name: '延安高架 虹桥枢纽入口（东向）', road: '延安高架', area: '长宁区', lat: 31.21, lng: 121.35 },
    ]),
    reporter: reporterUser?.nickname || '匿名上报',
    reporterId: reporterUser?.id || 0,
    reportTime: Mock.Random.datetime('2026-07-0' + Mock.Random.natural(1, 6) + ' HH:mm:ss'),
    congestionDuration: congestionMin + '分钟',
    recoveryTime: recoveryMin + '分钟',
    affectedLanes: lanes + '条',
    trafficFlow: Mock.Random.pick(['高峰', '平峰', '低峰']),
    weather: Mock.Random.pick(['晴', '多云', '小雨', '中雨', '大雾']),
    roadLevel: Mock.Random.pick(['高速', '快速路', '主干道', '次干道']),
    needSupport: riskIdx >= 2 ? ['清障车', '救护车', '交警支援'].slice(0, Mock.Random.natural(1, 3)) : [],
    supportAdvice: riskIdx >= 2
      ? '建议立即派警支援，通知附近医院做好接收准备，安排清障车前往现场'
      : '常规处置即可，安排清障车待命',
    disposalAdvice: Mock.Random.pick([
      '1. 在事故后方150米处放置警示标志；2. 引导车辆从左侧车道绕行；3. 通知清障车到场拖离故障车辆',
      '1. 封闭事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物；4. 疏散围观人员',
      '1. 封闭事故区域；2. 放置警示标志和锥桶；3. 疏导后方车辆减速慢行；4. 通知拖车救援',
    ]),
    images: Array.from({ length: Mock.Random.natural(1, 3) }, (_, i) => ({
      id: Mock.Random.guid(),
      url: `https://via.placeholder.com/600x400/1a56db/ffffff?text=事故现场+${i + 1}`,
      name: `现场照片_${i + 1}.jpg`,
    })),
    aiExplanation: '',
    processRecords: [
      { time: Mock.Random.datetime('2026-07-0' + Mock.Random.natural(1, 6) + ' HH:mm:ss'), action: '事故上报', operator: reporterUser?.nickname },
    ],
  }
}

// ====== 生成 20 条初始事故数据 ======
let accidentIdCounter = 20
const initialAccidents = Array.from({ length: 18 }, (_, i) => generateAccident(i + 1))

// Mock: GET /api/accident/list
Mock.mock('/api/accident/list', 'get', (options) => {
  const url = new URL(options.url, 'http://localhost')
  const page = +url.searchParams.get('page') || 1
  const pageSize = +url.searchParams.get('pageSize') || 20
  const riskLevel = url.searchParams.get('riskLevel')
  const status = url.searchParams.get('status')
  const area = url.searchParams.get('area')

  let list = [...initialAccidents]
  if (riskLevel) list = list.filter((a) => a.riskLevel === riskLevel)
  if (status) list = list.filter((a) => a.status === status)
  if (area) list = list.filter((a) => a.location.area.includes(area))

  const total = list.length
  const start = (page - 1) * pageSize
  const paged = list.slice(start, start + pageSize)

  return { code: 200, message: 'ok', data: { list: paged, total } }
})

// Mock: GET /api/accident/detail
Mock.mock('/api/accident/detail', 'get', (options) => {
  const url = new URL(options.url, 'http://localhost')
  const id = +url.searchParams.get('id')
  const accident = initialAccidents.find((a) => a.id === id)
  if (accident) {
    // 生成 AI 解释
    const explanation = `【系统分析结果】经图像识别与多源数据综合分析，该事件被判定为"${accident.type}"，置信度 ${accident.confidence}。结合当前${accident.trafficFlow}时段${accident.roadLevel}路段交通流特征（${accident.weather}天气），预计影响${accident.affectedLanes}车道。风险评估为"${accident.riskLevel}"等级，预计拥堵持续${accident.congestionDuration}，道路恢复需${accident.recoveryTime}。建议：${accident.disposalAdvice}。`
    return { code: 200, message: 'ok', data: { ...accident, aiExplanation: explanation } }
  }
  return { code: 404, message: '事故不存在', data: null }
})

// Mock: POST /api/accident/add
Mock.mock('/api/accident/add', 'post', (options) => {
  const data = JSON.parse(options.body)
  accidentIdCounter++
  const newAccident = {
    id: accidentIdCounter,
    caseNo: `A${new Date().getFullYear()}${String(1000 + accidentIdCounter)}`,
    reportTime: new Date().toLocaleString('zh-CN'),
    status: '待处理',
    type: data.accidentType || '-',
    riskLevel: '-',
    confidence: '-',
    riskScore: 0,
    congestionDuration: '分析中...',
    recoveryTime: '分析中...',
    affectedLanes: '-',
    trafficFlow: Mock.Random.pick(['高峰', '平峰', '低峰']),
    weather: Mock.Random.pick(['晴', '多云', '小雨']),
    roadLevel: Mock.Random.pick(['高速', '快速路', '主干道']),
    needSupport: [],
    supportAdvice: '',
    disposalAdvice: '',
    aiExplanation: '',
    images: data.images || [],
    video: data.video || null,
    location: data.location || { name: '', road: '', area: '', lat: 0, lng: 0 },
    description: data.description || '',
    reporter: data.reporter || '匿名',
    reporterId: data.reporterId || 0,
    processRecords: [
      { time: new Date().toLocaleString('zh-CN'), action: '事故上报', operator: data.reporter || '匿名' },
    ],
  }
  initialAccidents.unshift(newAccident)

  // 模拟 3 秒后识别完成（异步更新）
  setTimeout(() => {
    const idx = initialAccidents.findIndex((a) => a.id === newAccident.id)
    if (idx !== -1) {
      // 如果上报时选择了类型则保留，否则随机
      const type = data.accidentType || ACCIDENT_TYPES[Mock.Random.natural(0, ACCIDENT_TYPES.length - 1)]
      const riskIdx = Mock.Random.natural(0, 3)
      initialAccidents[idx] = {
        ...initialAccidents[idx],
        type,
        riskLevel: RISK_LEVELS[riskIdx],
        confidence: Mock.Random.float(72, 98, 0, 1).toFixed(1) + '%',
        riskScore: Mock.Random.natural(20, 99),
        congestionDuration: Mock.Random.natural(15, 120) + '分钟',
        recoveryTime: Mock.Random.natural(25, 180) + '分钟',
        affectedLanes: Mock.Random.natural(1, 4) + '条',
        needSupport: riskIdx >= 2 ? ['清障车', '救护车'].slice(0, Mock.Random.natural(1, 2)) : [],
        disposalAdvice: Mock.Random.pick([
          '1. 在事故后方150米处放置警示标志；2. 引导车辆从左侧车道绕行；3. 通知清障车到场拖离',
          '1. 封闭事故车道；2. 设置变道引导标志；3. 联系吊车清理散落货物',
          '1. 放置警示标志和锥桶；2. 疏导后方车辆减速慢行；3. 通知拖车救援',
        ]),
        aiExplanation: `【系统分析结果】经图像识别与多源数据综合分析，该事件被判定为"${type}"。风险评估为"${RISK_LEVELS[riskIdx]}"等级。`,
      }
    }
  }, 3000)

  return { code: 200, message: '事故提交成功', data: { id: newAccident.id, caseNo: newAccident.caseNo } }
})

// Mock: POST /api/accident/update
Mock.mock('/api/accident/update', 'post', (options) => {
  const data = JSON.parse(options.body)
  const idx = initialAccidents.findIndex((a) => a.id === data.id)
  if (idx !== -1) {
    initialAccidents[idx] = { ...initialAccidents[idx], ...data }
  }
  return { code: 200, message: '更新成功', data: null }
})

export { initialAccidents, generateAccident }
