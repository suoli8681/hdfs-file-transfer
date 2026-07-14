import request from './index'

export function getOverview() {
  return request.get('/dashboard/overview')
}

export function getRecentTasks(limit = 10) {
  return request.get('/dashboard/recent-tasks', { params: { limit } })
}