import request from './index'

export function createConversation(configId) {
  return request.post('/ai/conversations', null, { params: { configId: configId || undefined } })
}

export function listConversations() {
  return request.get('/ai/conversations')
}

export function getMessages(id) {
  return request.get(`/ai/conversations/${id}/messages`)
}

export function deleteConversation(id) {
  return request.delete(`/ai/conversations/${id}`)
}

export function listConfigs() {
  return request.get('/ai/configs')
}

export function createConfig(data) {
  return request.post('/ai/configs', data)
}

export function updateConfig(data) {
  return request.put('/ai/configs', data)
}

export function setDefaultConfig(id) {
  return request.post(`/ai/configs/${id}/default`)
}

export function testConnection(id) {
  return request.post(`/ai/configs/${id}/test`)
}
