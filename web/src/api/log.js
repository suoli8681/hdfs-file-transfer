import request from './index'

export function getLogPage(params) {
  return request.get('/logs/page', { params })
}