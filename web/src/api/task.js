import request from './index'

export function getTaskPage(params) {
  return request.get('/tasks/page', { params })
}

export function getTask(id) {
  return request.get(`/tasks/${id}`)
}

export function createTask(data) {
  return request.post('/tasks', data)
}

export function updateTask(data) {
  return request.put('/tasks', data)
}

export function startTask(id) {
  return request.post(`/tasks/${id}/start`)
}

export function stopTask(id) {
  return request.post(`/tasks/${id}/stop`)
}

export function forceKillTask(id) {
  return request.post(`/tasks/${id}/force-kill`)
}

export function deleteTask(id) {
  return request.delete(`/tasks/${id}`)
}