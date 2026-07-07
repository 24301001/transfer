import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端直接返回数据，没有 {code, data} 包装
    if (res && typeof res === 'object' && 'code' in res) {
      // 兼容 MockJS 的 {code, data} 格式
      if (res.code !== 200) {
        ElMessage.error(res.message || '请求失败')
        return Promise.reject(new Error(res.message || '请求失败'))
      }
      return res.data ?? res
    }
    // 后端真实接口：直接返回数据
    return res
  },
  (error) => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
