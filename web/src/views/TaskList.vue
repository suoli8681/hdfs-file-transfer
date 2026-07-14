<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>迁移任务</span>
        <el-button type="primary" size="small" @click="$router.push('/tasks/add')">新建任务</el-button>
      </div>
    </template>
    <div style="margin-bottom:15px;display:flex;flex-wrap:wrap;gap:10px;align-items:center">
      <el-input v-model="keyword" placeholder="搜索任务名称" style="width:180px" size="small" clearable />
      <el-select v-model="statusFilter" placeholder="状态" size="small" style="width:120px" clearable>
        <el-option label="草稿" value="draft" />
        <el-option label="待执行" value="pending" />
        <el-option label="运行中" value="running" />
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
    </div>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="taskName" label="任务名称" width="160" />
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
      <el-table-column prop="taskType" label="类型" width="80">
        <template #default="{ row }">{{ row.taskType === 'once' ? '一次性' : '定时' }}</template>
      </el-table-column>
      <el-table-column prop="agentId" label="执行Agent" width="130" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="completedFiles" label="进度" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'killed'" size="small" type="danger">已终止</el-tag>
          <el-progress v-else-if="row.totalFiles > 0" :percentage="Math.round(row.completedFiles / row.totalFiles * 100)" :stroke-width="12" />
          <el-tag v-else size="small" :type="$statusType(row.status)">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastExecTime" label="启动时间" width="160" />
      <el-table-column prop="completeTime" label="完成时间" width="160" />
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <!-- 草稿: 编辑、启动、操作记录、删除 -->
          <template v-if="row.status === 'draft'">
            <el-button type="primary" link size="small" @click="$router.push('/tasks/' + row.id + '/edit')">编辑</el-button>
            <el-button type="success" link size="small" :loading="startingIds.includes(row.id)" @click="handleStart(row)">启动</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
            <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" link size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
          <!-- 待执行: 停止、操作记录、删除 -->
          <template v-else-if="row.status === 'pending'">
            <el-popconfirm title="确定停止?将取消分发，不再执行该任务" @confirm="handleStop(row)">
              <template #reference><el-button type="warning" link size="small" :loading="stoppingIds.includes(row.id)">停止</el-button></template>
            </el-popconfirm>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
            <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" link size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
          <!-- 派发中: 停止、操作记录 -->
          <template v-else-if="row.status === 'dispatching'">
            <el-popconfirm title="确定停止?将取消分发，不再执行该任务" @confirm="handleStop(row)">
              <template #reference><el-button type="warning" link size="small" :loading="stoppingIds.includes(row.id)">停止</el-button></template>
            </el-popconfirm>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
          </template>
          <!-- 运行中/重试中: 强制终止、日志、操作记录 -->
          <template v-else-if="row.status === 'running' || row.status === 'retrying'">
            <el-popconfirm title="确定强制终止?将杀死进程并删除目标端已同步的文件" @confirm="handleForceKill(row)">
              <template #reference><el-button type="danger" link size="small" :loading="killingIds.includes(row.id)">强制终止</el-button></template>
            </el-popconfirm>
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
          </template>
          <!-- 已完成: 日志、校验结果、操作记录 -->
          <template v-else-if="row.status === 'success'">
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="primary" link size="small" @click="openVerify(row)">校验结果</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
          </template>
          <!-- 失败: 日志、操作记录 -->
          <template v-else-if="row.status === 'failed'">
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
          </template>
          <!-- 已停止: 日志、操作记录、删除 -->
          <template v-else-if="row.status === 'stopped'">
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
            <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
              <template #reference><el-button type="danger" link size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
          <!-- 已终止: 日志、操作记录 -->
          <template v-else-if="row.status === 'killed'">
            <el-button type="info" link size="small" @click="openLog(row)">日志</el-button>
            <el-button type="info" link size="small" @click="openOpLog(row)">操作记录</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="prev,pager,next" @current-change="fetchData" style="margin-top:15px;justify-content:center" />

    <!-- 日志弹窗 -->
    <el-dialog v-model="logVisible" :title="'日志 - ' + logTaskName" width="80%" top="30px" destroy-on-close>
      <div ref="logContainer" style="background:#1e1e1e;color:#d4d4d4;padding:15px;border-radius:4px;height:500px;overflow:auto;font-family:'Consolas','Courier New',monospace;font-size:13px;line-height:1.6">
        <div v-if="logs.length === 0" style="color:#666;text-align:center;padding:40px">暂无日志</div>
        <div v-for="(log, idx) in logs" :key="idx" style="white-space:pre-wrap;word-break:break-all">
          <span :style="{ color: log.logLevel === 'ERROR' ? '#f44747' : log.logLevel === 'WARN' ? '#dcdcaa' : '#6a9955' }">[{{ log.logLevel }}]</span>
          <span style="color:#888;margin:0 8px">{{ log.createTime }}</span>
          <span>{{ log.content }}</span>
        </div>
      </div>
      <template #footer>
        <el-pagination v-if="logTotal > 0" v-model:current-page="logPageNum" :page-size="logPageSize" :total="logTotal" layout="prev,pager,next" @current-change="fetchLogs" small />
      </template>
    </el-dialog>

    <!-- 校验结果弹窗 -->
    <el-dialog v-model="verifyVisible" :title="'校验结果 - ' + verifyTaskName" width="70%" destroy-on-close>
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

    <!-- 操作记录弹窗 -->
    <el-dialog v-model="opLogVisible" :title="'操作记录 - ' + opLogTaskName" width="70%" destroy-on-close>
      <el-table :data="opLogList" stripe v-loading="opLogLoading" style="width:100%">
        <el-table-column prop="operation" label="操作" width="100">
          <template #default="{ row }">
            <el-tag :type="opTagType(row.operation)" size="small">{{ opLabel(row.operation) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="detail" label="操作详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="操作时间" width="180" />
      </el-table>
      <el-pagination v-if="opLogTotal > 0" v-model:current-page="opLogPageNum" :page-size="opLogPageSize" :total="opLogTotal" layout="prev,pager,next" @current-change="fetchOpLogs" small style="margin-top:15px;justify-content:center" />
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { getTaskPage, startTask, stopTask, forceKillTask, deleteTask } from '../api/task'
import { getLogPage } from '../api/log'
import { getLatestVerify } from '../api/verify'
import { getOpLogPage } from '../api/oplog'
import { getAgentList } from '../api/agent'
import { ElMessage, ElMessageBox } from 'element-plus'

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

// Log dialog
const logVisible = ref(false)
const logTaskName = ref('')
const logs = ref([])
const logPageNum = ref(1)
const logPageSize = ref(100)
const logTotal = ref(0)
const logContainer = ref(null)
let logCurrentTaskId = null

// Verify dialog
const verifyVisible = ref(false)
const verifyTaskName = ref('')
const verifyData = ref(null)
const verifyLoading = ref(false)

// Op log dialog
const opLogVisible = ref(false)
const opLogTaskName = ref('')
const opLogList = ref([])
const opLogLoading = ref(false)
const opLogPageNum = ref(1)
const opLogPageSize = ref(20)
const opLogTotal = ref(0)
let opLogTaskId = null

const opLabel = (op) => ({
  create: '新建', edit: '编辑', start: '启动', stop: '停止',
  kill: '终止', delete: '删除'
}[op] || op)

const opTagType = (op) => ({
  create: 'success', edit: 'primary', start: 'success',
  stop: 'warning', kill: 'danger', delete: 'danger'
}[op] || 'info')

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value, pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      agentId: agentFilter.value || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined
    }
    const res = await getTaskPage(params)
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) { ElMessage.error('获取任务列表失败') }
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

const fetchLogs = async () => {
  if (!logCurrentTaskId) return
  try {
    const res = await getLogPage({ pageNum: logPageNum.value, pageSize: logPageSize.value, taskId: logCurrentTaskId })
    logs.value = res.records || []
    logTotal.value = res.total || 0
    nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = 0 })
  } catch (e) { logs.value = [] }
}

const openLog = (row) => {
  logCurrentTaskId = row.id
  logTaskName.value = row.taskName
  logPageNum.value = 1
  logVisible.value = true
  fetchLogs()
}

const openVerify = async (row) => {
  verifyTaskName.value = row.taskName
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

const fetchOpLogs = async () => {
  if (!opLogTaskId) return
  opLogLoading.value = true
  try {
    const res = await getOpLogPage({ pageNum: opLogPageNum.value, pageSize: opLogPageSize.value, taskId: opLogTaskId })
    opLogList.value = res.records || []
    opLogTotal.value = res.total || 0
  } catch (e) { opLogList.value = [] }
  opLogLoading.value = false
}

const openOpLog = (row) => {
  opLogTaskId = row.id
  opLogTaskName.value = row.taskName
  opLogPageNum.value = 1
  opLogVisible.value = true
  fetchOpLogs()
}

const startingIds = ref([])
const stoppingIds = ref([])
const killingIds = ref([])

const handleStart = async (row) => {
  if (startingIds.value.includes(row.id)) return
  try {
    await ElMessageBox.confirm('确定要启动该任务吗？启动后将开始执行数据迁移。', '确认启动', { type: 'warning' })
  } catch (e) {
    return
  }
  startingIds.value.push(row.id)
  try {
    await startTask(row.id)
    ElMessage.success('已启动')
    await fetchData()
  } catch (e) {
    ElMessage.error(e.message || '启动失败')
  }
  startingIds.value = startingIds.value.filter(id => id !== row.id)
}

const handleStop = async (row) => {
  if (stoppingIds.value.includes(row.id)) return
  stoppingIds.value.push(row.id)
  try {
    await stopTask(row.id)
    ElMessage.success('已停止')
    await fetchData()
  } catch (e) {
    ElMessage.error(e.message || '停止失败')
  }
  stoppingIds.value = stoppingIds.value.filter(id => id !== row.id)
}

const handleForceKill = async (row) => {
  if (killingIds.value.includes(row.id)) return
  killingIds.value.push(row.id)
  try {
    await forceKillTask(row.id)
    ElMessage.success('已强制终止')
    await fetchData()
  } catch (e) {
    ElMessage.error(e.message || '终止失败')
  }
  killingIds.value = killingIds.value.filter(id => id !== row.id)
}

const handleDelete = async (id) => { try { await deleteTask(id); ElMessage.success('已删除'); fetchData() } catch (e) { ElMessage.error(e.message || '删除失败') } }

onMounted(() => {
  fetchData()
  fetchAgents()
})
</script>