import request from '../request'

export function getAccidentList(params) {
  return request.get('/accident/list', { params })
}

export function getAccidentDetail(id) {
  return request.get('/accident/detail', { params: { id } })
}

export function addAccident(data) {
  return request.post('/accident/add', data)
}

export function updateAccident(data) {
  return request.post('/accident/update', data)
}
