import request from './index'

export function getOpLogPage(params) {
  return request.get('/task-logs/page', { params })
}

export function getOpLogList(taskId) {
  return request.get(`/task-logs/list/${taskId}`)
}