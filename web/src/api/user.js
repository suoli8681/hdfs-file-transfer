import request from './index'

export function getUserPage(params) {
  return request.get('/users/page', { params })
}

export function createUser(data) {
  return request.post('/users', data)
}

export function updateUser(data) {
  return request.put('/users', data)
}

export function updateUserStatus(id, status) {
  return request.post(`/users/${id}/status`, { status })
}

export function resetPassword(id, newPassword) {
  return request.post(`/users/${id}/reset-password`, { newPassword })
}
