import request from '../api'

export function login(username, password) {
  return request.post('/auth/login', { username, password }, { skipAuth: true })
}

export function register(data) {
  return request.post('/auth/register', data, { skipAuth: true })
}

export function getUserInfo() {
  return request.get('/auth/info')
}

export function updateProfile(data) {
  return request.put('/auth/profile', data)
}

export function changePassword(data) {
  return request.post('/auth/password', data)
}