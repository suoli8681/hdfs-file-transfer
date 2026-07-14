import request from './index'

export function getLoginLogPage(params) {
  return request.get('/login-logs/page', { params })
}
