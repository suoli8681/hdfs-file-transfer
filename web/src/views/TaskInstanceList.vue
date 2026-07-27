<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>任务实例</span>
        <el-button type="primary" size="small" @click="$router.push('/tasks')">返回任务列表</el-button>
      </div>
    </template>
    <div style="margin-bottom:15px;display:flex;flex-wrap:wrap;gap:10px;align-items:center">
      <el-input v-model="keyword" placeholder="搜索实例名称" style="width:220px" size="small" clearable />
      <el-select v-model="statusFilter" placeholder="状态" size="small" style="width:120px" clearable>
        <el-option label="待执行" value="pending" />
        <el-option label="派发中" value="dispatching" />
        <el-option label="运行中" value="running" />
        <el-option label="重试中" value="retrying" />
        <el-option label="已完成" value="success" />
        <el-option label="失败" value="failed" />
        <el-option label="已停止" value="stopped" />
        <el-option label="已终止" value="killed" />
      </el-select>
      <el-select v-model="agentFilter" placeholder="执行Agent" size="small" style="width:200px" clearable filterable>
        <el-option v-for="a in agents" :key="a.agentId" :label="a.agentHost + '(' + a.agentId + ')'" :value="a.agentId" />
      </el-select>
      <el-date-picker v-model="dateRange" type="datetimerange" range-separator="至" start-placeholder="启动开始时间" end-placeholder="完成结束时间" size="small" style="width:360px" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" />
      <el-button type="primary" size="small" @click="fetchData">查询</el-button>
      <el-button size="small" @click="resetFilter">重置</el-button>
      <el-button type="success" size="small" :loading="exporting" @click="handleExport">导出Excel</el-button>
    </div>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="instanceName" label="实例名称" min-width="220" show-overflow-tooltip />
      <el-table-column label="源集群/路径" min-width="200">
        <template #default="{ row }">
          <div style="font-size:12px;color:#409EFF">{{ row.sourceClusterName || '--' }}</div>
          <div style="font-size:12px">{{ row.sourcePath }}</div>
        </template>
      </el-table-column>
      <el-table-column label="目标集群/路径" min-width="200">
        <template #default="{ row }">
          <div style="font-size:12px;color:#409EFF">{{ row.targetClusterName || '--' }}</div>
          <div style="font-size:12px">{{ row.targetPath }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="agentId" label="执行Agent" width="130" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'killed'" size="small" type="danger">已终止</el-tag>
          <el-progress v-else-if="row.totalFiles > 0" :percentage="Math.round(row.completedFiles / row.totalFiles * 100)" :stroke-width="12" />
          <el-tag v-else size="small" :type="$statusType(row.status)">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastExecTime" label="启动时间" width="160" />
      <el-table-column prop="completeTime" label="完成时间" width="160" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'running' || row.status === 'retrying'">
            <el-button type="primary" link size="small" @click="openDetail(row)">查看</el-button>
            <el-popconfirm title="确定强制终止?将杀死进程并删除目标端已同步的文件" @confirm="handleForceKill(row)">
              <template #reference><el-button type="danger" link size="small" :loading="killingIds.includes(row.id)">强制终止</el-button></template>
            </el-popconfirm>
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
          </template>
          <template v-else-if="row.status === 'success'">
            <el-button type="primary" link size="small" @click="openDetail(row)">查看</el-button>
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="primary" link size="small" @click="openVerify(row)">校验结果</el-button>
          </template>
          <template v-else-if="row.status === 'failed' || row.status === 'stopped' || row.status === 'killed'">
            <el-button type="primary" link size="small" @click="openDetail(row)">查看</el-button>
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
          </template>
          <template v-else>
            <el-button type="primary" link size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top:15px;display:flex;justify-content:flex-end;align-items:center;gap:10px">
      <el-pagination v-if="total > 0" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total,sizes,prev,pager,next" @current-change="fetchData" @size-change="fetchData" />
    </div>

    <!-- 实例详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="'实例详情 - ' + detailName" width="70%" destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions v-if="detailData" :column="2" border>
          <el-descriptions-item label="实例名称">{{ detailData.instanceName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="$statusType(detailData.status)" size="small">{{ $statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="源集群">{{ detailData.sourceClusterName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="源路径">{{ detailData.sourcePath || '--' }}</el-descriptions-item>
          <el-descriptions-item label="目标集群">{{ detailData.targetClusterName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="目标路径">{{ detailData.targetPath || '--' }}</el-descriptions-item>
          <el-descriptions-item label="执行Agent">{{ detailData.agentId || '--' }}</el-descriptions-item>
          <el-descriptions-item label="distcp参数">{{ detailData.distcpOptions || '--' }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ detailData.retryCount != null ? detailData.retryCount : 0 }} / {{ detailData.maxRetryCount != null ? detailData.maxRetryCount : 0 }}</el-descriptions-item>
          <el-descriptions-item label="总文件数">{{ detailData.totalFiles || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已完成文件数">{{ detailData.completedFiles || 0 }}</el-descriptions-item>
          <el-descriptions-item label="总数据量">{{ $formatSize(detailData.totalSize) }}</el-descriptions-item>
          <el-descriptions-item label="已完成数据量">{{ $formatSize(detailData.completedSize) }}</el-descriptions-item>
          <el-descriptions-item label="启动时间">{{ detailData.lastExecTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ detailData.completeTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createTime || '--' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.errorMsg" label="错误信息" :span="2">
            <span style="color:#f56c6c">{{ detailData.errorMsg }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无数据" />
      </div>
    </el-dialog>

    <!-- 日志弹窗 -->
    <el-dialog v-model="logVisible" :title="'日志 - ' + logName" width="80%" top="30px" destroy-on-close>
      <div ref="logContainer" style="background:#1e1e1e;color:#d4d4d4;padding:15px;border-radius:4px;height:500px;overflow:auto;font-family:'Consolas','Courier New',monospace;font-size:13px;line-height:1.6">
        <div v-if="logs.length === 0" style="color:#666;text-align:center;padding:40px">暂无日志</div>
        <div v-for="(log, idx) in logs" :key="idx" style="white-space:pre-wrap;word-break:break-all">
          <span :style="{ color: log.logLevel === 'ERROR' ? '#f44747' : log.logLevel === 'WARN' ? '#dcdcaa' : '#6a9955' }">[{{ log.logLevel }}]</span>
          <span style="color:#888;margin:0 8px">{{ log.createTime }}</span>
          <span>{{ log.content }}</span>
        </div>
      </div>
      <template #footer>
        <el-pagination v-if="logTotal > 0" v-model:current-page="logPageNum" v-model:page-size="logPageSize" :total="logTotal" :page-sizes="[10, 20, 50, 100]" layout="total,sizes,prev,pager,next" @current-change="fetchLogs" @size-change="fetchLogs" small style="justify-content:flex-end" />
      </template>
    </el-dialog>

    <!-- 校验结果弹窗 -->
    <el-dialog v-model="verifyVisible" :title="'校验结果 - ' + verifyName" width="70%" destroy-on-close>
      <div v-loading="verifyLoading">
        <el-descriptions v-if="verifyData" :column="2" border>
          <el-descriptions-item label="校验状态">
            <el-tag :type="verifyData.verifyStatus === 'match' ? 'success' : verifyData.verifyStatus === 'mismatch' ? 'danger' : 'info'" size="small">
              {{ {match:'一致',mismatch:'不一致',error:'异常',pending:'待校验'}[verifyData.verifyStatus] || verifyData.verifyStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="校验时间">{{ verifyData.createTime }}</el-descriptions-item>
          <el-descriptions-item label="源端文件数">{{ verifyData.sourceFileCount }}</el-descriptions-item>
          <el-descriptions-item label="目标端文件数">{{ verifyData.targetFileCount }}</el-descriptions-item>
          <el-descriptions-item label="源端数据量">{{ $formatSize(verifyData.sourceTotalSize) }}</el-descriptions-item>
          <el-descriptions-item label="目标端数据量">{{ $formatSize(verifyData.targetTotalSize) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ verifyData.errorMessage || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="verifyData.diffFileList" label="差异文件" :span="2">
            <div style="max-height:300px;overflow:auto">
              <pre style="white-space:pre-wrap;font-size:12px">{{ verifyData.diffFileList }}</pre>
            </div>
          </el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="暂无校验结果" />
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getInstancePage, getInstance, forceKillInstance, exportInstances } from '../api/task'
import { getLogPage } from '../api/log'
import { getLatestVerify } from '../api/verify'
import { getAgentList } from '../api/agent'
import { ElMessage } from 'element-plus'

const route = useRoute()
const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const statusFilter = ref('')
const agentFilter = ref('')
const dateRange = ref([])
const agents = ref([])
const parentTaskId = ref(route.query.parentTaskId ? Number(route.query.parentTaskId) : null)

// Detail dialog
const detailVisible = ref(false)
const detailName = ref('')
const detailData = ref(null)
const detailLoading = ref(false)

// Log dialog
const logVisible = ref(false)
const logName = ref('')
const logs = ref([])
const logPageNum = ref(1)
const logPageSize = ref(100)
const logTotal = ref(0)
const logContainer = ref(null)
let logCurrentId = null

// Verify dialog
const verifyVisible = ref(false)
const verifyName = ref('')
const verifyData = ref(null)
const verifyLoading = ref(false)

const killingIds = ref([])
const exporting = ref(false)

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value, pageSize: pageSize.value,
      parentTaskId: parentTaskId.value || undefined,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      agentId: agentFilter.value || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined
    }
    const res = await getInstancePage(params)
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) { ElMessage.error('获取实例列表失败') }
  loading.value = false
}

const fetchAgents = async () => {
  try { agents.value = await getAgentList() || [] } catch (e) { agents.value = [] }
}

const resetFilter = () => {
  keyword.value = ''
  statusFilter.value = ''
  agentFilter.value = ''
  dateRange.value = []
  fetchData()
}

const openDetail = async (row) => {
  detailName.value = row.instanceName
  detailVisible.value = true
  detailData.value = null
  detailLoading.value = true
  try {
    const res = await getInstance(row.id)
    detailData.value = res
  } catch (e) {
    detailData.value = null
  }
  detailLoading.value = false
}

const fetchLogs = async () => {
  if (!logCurrentId) return
  try {
    const res = await getLogPage({ pageNum: logPageNum.value, pageSize: logPageSize.value, taskId: logCurrentId })
    logs.value = res.records || []
    logTotal.value = res.total || 0
    nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = 0 })
  } catch (e) { logs.value = [] }
}

const openLog = (row) => {
  logCurrentId = row.id
  logName.value = row.instanceName
  logPageNum.value = 1
  logVisible.value = true
  fetchLogs()
}

const openVerify = async (row) => {
  verifyName.value = row.instanceName
  verifyVisible.value = true
  verifyData.value = null
  verifyLoading.value = true
  try {
    const res = await getLatestVerify(row.id)
    verifyData.value = res
  } catch (e) {
    verifyData.value = null
  }
  verifyLoading.value = false
}

const handleForceKill = async (row) => {
  if (killingIds.value.includes(row.id)) return
  killingIds.value.push(row.id)
  try {
    await forceKillInstance(row.id)
    ElMessage.success('已强制终止')
    await fetchData()
  } catch (e) {
    ElMessage.error(e.message || '终止失败')
  }
  killingIds.value = killingIds.value.filter(id => id !== row.id)
}

const handleExport = async () => {
  exporting.value = true
  try {
    const params = {
      parentTaskId: parentTaskId.value || undefined,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      agentId: agentFilter.value || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined
    }
    const res = await exportInstances(params)
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    const disposition = res.headers['content-disposition'] || ''
    const match = disposition.match(/filename\*?=(?:UTF-8'')?(.+)/i)
    link.setAttribute('download', match ? decodeURIComponent(match[1]) : '任务实例列表.xlsx')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
  exporting.value = false
}

onMounted(() => {
  fetchData()
  fetchAgents()
})
</script>
