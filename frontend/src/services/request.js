import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 响应拦截器：将后端直接返回的数据包装成前端组件期望的 {code, data} 格式
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 204 No Content 或空响应（如 DELETE）
    if (res === null || res === undefined) {
      return { code: 200, data: null }
    }
    // 后端直接返回数据（无 {code, data} 包装）→ 包装成前端兼容格式
    if (typeof res === 'object' && !('code' in res)) {
      return { code: 200, data: res }
    }
    // 已有 code 字段（兼容 MockJS 格式，目前不使用）
    if (typeof res === 'object' && 'code' in res) {
      if (res.code !== 200) {
        ElMessage.error(res.message || '请求失败')
        return Promise.reject(new Error(res.message || '请求失败'))
      }
      return res
    }
    // 原始类型响应
    return { code: 200, data: res }
  },
  (error) => {
    // HTTP 错误处理
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
