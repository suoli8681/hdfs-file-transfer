import request from './index'

export function getAgentList() {
  return request.get('/agents/list')
}