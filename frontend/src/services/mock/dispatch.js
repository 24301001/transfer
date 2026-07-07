import Mock from 'mockjs'
import { initialAccidents } from './accident'
import { mockUsers } from './user'

const TASK_STATUSES = ['待接收', '已出发', '已到达', '处理中', '已完成']
const VEHICLE_TYPES = ['清障车', '救护车', '警车', '工程车']

// ====== 生成调度任务 ======
function generateTask(id, accidentId) {
  const accident = initialAccidents.find((a) => a.id === accidentId)
  const statusIdx = Mock.Random.natural(0, 4)
  const rescueUsers = mockUsers.filter((u) => u.role === 'RESCUE')
  const assignerUsers = mockUsers.filter((u) => u.role === 'COMMAND')

  return {
    id,
    accidentId,
    caseNo: accident?.caseNo || `A${Mock.Random.natural(1000, 9999)}`,
    accidentType: accident?.type || '车辆碰撞',
    riskLevel: accident?.riskLevel || '中',
    location: accident?.location || { name: '', road: '', area: '' },
    status: TASK_STATUSES[statusIdx],
    vehicleType: VEHICLE_TYPES[Mock.Random.natural(0, VEHICLE_TYPES.length - 1)],
    vehiclePlate: Mock.Random.pick(['沪A·' + Mock.Random.string('ABCDEFGHJKLMNPQ', 1) + Mock.Random.string('0123456789', 4), '沪B·' + Mock.Random.string('0123456789', 5)]),
    assignedTo: rescueUsers[Mock.Random.natural(0, rescueUsers.length - 1)]?.nickname || '王队长',
    assignedToId: rescueUsers[Mock.Random.natural(0, rescueUsers.length - 1)]?.id || 0,
    assigner: assignerUsers[Mock.Random.natural(0, assignerUsers.length - 1)]?.nickname || '李指挥',
    assignerId: assignerUsers[Mock.Random.natural(0, assignerUsers.length - 1)]?.id || 0,
    createTime: Mock.Random.datetime('2026-07-0' + Mock.Random.natural(1, 6) + ' HH:mm:ss'),
    updateTime: Mock.Random.datetime('2026-07-0' + Mock.Random.natural(1, 6) + ' HH:mm:ss'),
    feedback: '',
    notes: Mock.Random.pick([
      '注意现场安全，放置警示标志',
      '需携带破拆工具',
      '需要吊车协助',
      '注意漏油情况',
    ]),
  }
}

// ====== 初始任务 ======
let taskIdCounter = 15
const initialTasks = Array.from({ length: 12 }, (_, i) => generateTask(i + 1, Mock.Random.natural(1, Math.min(initialAccidents.length, 15))))

// 为已有事故关联任务
initialAccidents.forEach((a) => {
  const hasTask = initialTasks.find((t) => t.accidentId === a.id)
  if (hasTask) {
    a.dispatchTaskId = hasTask.id
  }
})

// Mock: GET /api/dispatch/list
Mock.mock('/api/dispatch/list', 'get', (options) => {
  const url = new URL(options.url, 'http://localhost')
  const page = +url.searchParams.get('page') || 1
  const pageSize = +url.searchParams.get('pageSize') || 20
  const status = url.searchParams.get('status')

  let list = [...initialTasks]
  if (status) list = list.filter((t) => t.status === status)

  const total = list.length
  const start = (page - 1) * pageSize
  const paged = list.slice(start, start + pageSize)

  return { code: 200, message: 'ok', data: { list: paged, total } }
})

// Mock: GET /api/dispatch/detail
Mock.mock('/api/dispatch/detail', 'get', (options) => {
  const url = new URL(options.url, 'http://localhost')
  const id = +url.searchParams.get('id')
  const task = initialTasks.find((t) => t.id === id)
  if (task) {
    // 关联事故详情
    const accident = initialAccidents.find((a) => a.id === task.accidentId)
    return { code: 200, message: 'ok', data: { ...task, accident } }
  }
  return { code: 404, message: '任务不存在', data: null }
})

// Mock: POST /api/dispatch/create
Mock.mock('/api/dispatch/create', 'post', (options) => {
  const data = JSON.parse(options.body)
  taskIdCounter++
  const newTask = {
    id: taskIdCounter,
    createTime: new Date().toLocaleString('zh-CN'),
    updateTime: new Date().toLocaleString('zh-CN'),
    status: '待接收',
    feedback: '',
    ...data,
  }
  initialTasks.unshift(newTask)

  // 更新事故关联
  const accident = initialAccidents.find((a) => a.id === data.accidentId)
  if (accident) {
    accident.dispatchTaskId = newTask.id
  }

  return { code: 200, message: '任务创建成功', data: { id: newTask.id } }
})

// Mock: POST /api/dispatch/updateStatus
Mock.mock('/api/dispatch/updateStatus', 'post', (options) => {
  const { id, status, feedback } = JSON.parse(options.body)
  const task = initialTasks.find((t) => t.id === id)
  if (task) {
    task.status = status
    task.updateTime = new Date().toLocaleString('zh-CN')
    if (feedback) task.feedback = feedback
  }
  return { code: 200, message: '状态更新成功', data: null }
})

export { initialTasks, generateTask }
