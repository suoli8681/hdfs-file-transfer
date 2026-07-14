export function statusType(status) {
  const map = { draft: 'info', pending: 'warning', running: 'warning', success: 'success', failed: 'danger', stopped: 'info', killed: 'danger', retrying: 'warning', dispatching: 'warning' }
  return map[status] || 'info'
}

export function statusLabel(status) {
  const map = { draft: '草稿', pending: '待执行', running: '运行中', success: '已完成', failed: '失败', stopped: '已停止', killed: '已终止', retrying: '重试中', dispatching: '派发中' }
  return map[status] || status
}

export function formatSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(2) + ' ' + units[i]
}