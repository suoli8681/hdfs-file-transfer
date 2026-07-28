import request from './index'

export function getAlertConfigs() {
  return request.get('/alert-config')
}

export function updateAlertConfig(data) {
  return request.put('/alert-config/config', data)
}

export function updateAlertWebhook(data) {
  return request.put('/alert-config/webhook', data)
}

export function testAlertWebhook(data) {
  return request.post('/alert-config/test', data)
}
