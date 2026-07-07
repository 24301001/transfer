import request from '../request'

export function login(data) {
  return request.post('/user/login', data)
}

export function register(data) {
  return request.post('/user/register', data)
}

export function getUserInfo() {
  return request.get('/user/info')
}

export function getUserList(params) {
  return request.get('/user/list', { params })
}

export function updateUser(data) {
  return request.post('/user/update', data)
}

export function deleteUser(data) {
  return request.post('/user/delete', data)
}
