import request from './index'

export function getVerifyPage(params) {
  return request.get('/verify/page', { params })
}

export function getLatestVerify(taskId) {
  return request.get(`/verify/latest/${taskId}`)
}