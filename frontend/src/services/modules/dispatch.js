import request from '../request'

export function getDispatchList(params) {
  return request.get('/dispatch/list', { params })
}

export function getDispatchDetail(id) {
  return request.get('/dispatch/detail', { params: { id } })
}

export function createDispatch(data) {
  return request.post('/dispatch/create', data)
}

export function updateDispatchStatus(data) {
  return request.post('/dispatch/updateStatus', data)
}
