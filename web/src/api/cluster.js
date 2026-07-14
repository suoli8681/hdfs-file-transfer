import request from './index'

export function getClusterPage(params) {
  return request.get('/clusters/page', { params })
}

export function getClusterList() {
  return request.get('/clusters/list')
}

export function getCluster(id) {
  return request.get(`/clusters/${id}`)
}

export function createCluster(data) {
  return request.post('/clusters', data)
}

export function updateCluster(data) {
  return request.put('/clusters', data)
}

export function deleteCluster(id) {
  return request.delete(`/clusters/${id}`)
}

export function testClusterConnect(id) {
  return request.post(`/clusters/test-connect/${id}`)
}