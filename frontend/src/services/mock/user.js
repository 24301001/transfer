import Mock from 'mockjs'
import { ROLES } from '@/utils/role'

// ====== 预设用户数据 ======
const mockUsers = [
  { id: 1, username: 'police1', password: '123456', nickname: '张警官', role: ROLES.POLICE.key, avatar: '' },
  { id: 2, username: 'command1', password: '123456', nickname: '李指挥', role: ROLES.COMMAND.key, avatar: '' },
  { id: 3, username: 'rescue1', password: '123456', nickname: '王队长', role: ROLES.RESCUE.key, avatar: '' },
  { id: 4, username: 'admin1', password: '123456', nickname: '赵管理', role: ROLES.ADMIN.key, avatar: '' },
  { id: 5, username: 'police2', password: '123456', nickname: '刘警官', role: ROLES.POLICE.key, avatar: '' },
  { id: 6, username: 'rescue2', password: '123456', nickname: '陈师傅', role: ROLES.RESCUE.key, avatar: '' },
]

// Mock: POST /api/user/login
Mock.mock('/api/user/login', 'post', (options) => {
  const { username, password } = JSON.parse(options.body)
  const user = mockUsers.find((u) => u.username === username && u.password === password)
  if (user) {
    const token = Mock.Random.guid()
    const { password: _, ...userInfo } = user
    return {
      code: 200,
      message: '登录成功',
      data: {
        token,
        userInfo,
      },
    }
  }
  return { code: 401, message: '用户名或密码错误', data: null }
})

// Mock: POST /api/user/register
Mock.mock('/api/user/register', 'post', (options) => {
  const { username, password, nickname, role } = JSON.parse(options.body)
  // 检查重复
  if (mockUsers.find((u) => u.username === username)) {
    return { code: 400, message: '用户名已存在', data: null }
  }
  const newUser = {
    id: mockUsers.length + 1,
    username,
    password,
    nickname,
    role,
    avatar: '',
    createTime: new Date().toLocaleString('zh-CN'),
  }
  mockUsers.push(newUser)
  const token = Mock.Random.guid()
  const { password: _, ...userInfo } = newUser
  return { code: 200, message: '注册成功', data: { token, userInfo } }
})

// Mock: GET /api/user/info
Mock.mock('/api/user/info', 'get', (options) => {
  // 从 header 取 token 模拟鉴权
  return { code: 200, message: 'ok', data: null }
})

// Mock: GET /api/user/list (管理员用)
Mock.mock('/api/user/list', 'get', () => {
  const list = mockUsers.map(({ password: _, ...u }) => ({
    ...u,
    status: u.id !== 2 ? '启用' : '禁用',
    createTime: '2025-0' + (u.id % 12 + 1) + '-' + String(10 + u.id).padStart(2, '0') + ' 09:00:00',
  }))
  return { code: 200, message: 'ok', data: { list, total: list.length } }
})

// Mock: POST /api/user/update
Mock.mock('/api/user/update', 'post', (options) => {
  const data = JSON.parse(options.body)
  const user = mockUsers.find((u) => u.id === data.id)
  if (user) {
    Object.assign(user, data)
  }
  return { code: 200, message: '更新成功', data: null }
})

// Mock: POST /api/user/delete
Mock.mock('/api/user/delete', 'post', (options) => {
  const { id } = JSON.parse(options.body)
  const idx = mockUsers.findIndex((u) => u.id === id)
  if (idx !== -1) mockUsers.splice(idx, 1)
  return { code: 200, message: '删除成功', data: null }
})

export { mockUsers }
