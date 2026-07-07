import request from '../request'

export function getSystemLogs(params) {
  return request.get('/system/logs', { params })
}

export function getSystemHealth() {
  return request.get('/system/health')
}

export function getSystemData() {
  return request.get('/system/data')
}
