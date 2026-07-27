import request from './index'

export function getTaskPage(params) {
  return request.get('/tasks/page', { params })
}

export function getTask(id) {
  return request.get(`/tasks/${id}`)
}

export function getTaskInstances(id) {
  return request.get(`/tasks/${id}/instances`)
}

export function getInstancePage(params) {
  return request.get('/task-instances/page', { params })
}

export function getInstance(id) {
  return request.get(`/task-instances/${id}`)
}

export function forceKillInstance(id) {
  return request.post(`/task-instances/${id}/force-kill`)
}

export function exportInstances(params) {
  return request.get('/task-instances/export', {
    params,
    responseType: 'blob',
    skipAuth: false
  })
}

export function exportTasks(params) {
  return request.get('/tasks/export', {
    params,
    responseType: 'blob',
    skipAuth: false
  })
}

export function createTask(data) {
  return request.post('/tasks', data)
}

export function updateTask(data) {
  return request.put('/tasks', data)
}

export function onlineTask(id) {
  return request.post(`/tasks/${id}/online`)
}

export function offlineTask(id) {
  return request.post(`/tasks/${id}/offline`)
}

export function executeTask(id) {
  return request.post(`/tasks/${id}/execute`)
}

export function forceKillTask(id) {
  return request.post(`/tasks/${id}/force-kill`)
}

export function deleteTask(id) {
  return request.delete(`/tasks/${id}`)
}