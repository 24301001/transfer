import Mock from 'mockjs'
import './user'
import './accident'
import './dispatch'
import './system'

// 设置 Mock 响应延迟
Mock.setup({
  timeout: '200-600',
})
