<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>任务日志</span>
        <div style="display:flex;gap:10px">
          <el-select v-model="selectedTask" filterable placeholder="选择任务" style="width:280px" size="small" @change="onTaskChange">
            <el-option v-for="t in taskOptions" :key="t.id" :label="t.taskName + ' (ID:' + t.id + ')'" :value="t.id" />
          </el-select>
          <el-button type="primary" size="small" @click="fetchLogs" :disabled="!selectedTask">查询</el-button>
        </div>
      </div>
    </template>
    <div v-if="selectedTask" style="margin-bottom:10px;font-size:13px;color:#666">
      当前查看：<strong>{{ currentTaskName }}</strong>
    </div>
    <div ref="logContainer" style="background:#1e1e1e;color:#d4d4d4;padding:15px;border-radius:4px;height:600px;overflow:auto;font-family:'Consolas','Courier New',monospace;font-size:13px;line-height:1.6">
      <div v-if="logs.length === 0" style="color:#666;text-align:center;padding:40px">暂无日志</div>
      <div v-for="(log, idx) in logs" :key="idx" style="white-space:pre-wrap;word-break:break-all">
        <span :style="{ color: log.logLevel === 'ERROR' ? '#f44747' : log.logLevel === 'WARN' ? '#dcdcaa' : '#6a9955' }">[{{ log.logLevel }}]</span>
        <span style="color:#888;margin:0 8px">{{ log.createTime }}</span>
        <span>{{ log.content }}</span>
      </div>
    </div>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total,sizes,prev,pager,next" @current-change="fetchLogs" @size-change="fetchLogs" style="margin-top:10px;justify-content:flex-end" />
  </el-card>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { getLogPage } from '../api/log'
import { getTaskPage } from '../api/task'

const logs = ref([])
const selectedTask = ref(null)
const taskOptions = ref([])
const pageNum = ref(1)
const pageSize = ref(100)
const total = ref(0)
const logContainer = ref(null)

const currentTaskName = computed(() => {
  const t = taskOptions.value.find(x => x.id === selectedTask.value)
  return t ? t.taskName : ''
})

const fetchTasks = async () => {
  try {
    const res = await getTaskPage({ pageNum: 1, pageSize: 200 })
    taskOptions.value = res.records || []
  } catch (e) {}
}

const fetchLogs = async () => {
  if (!selectedTask.value) return
  try {
    const res = await getLogPage({ pageNum: pageNum.value, pageSize: pageSize.value, taskId: selectedTask.value })
    logs.value = res.records || []
    total.value = res.total || 0
    nextTick(() => {
      if (logContainer.value) logContainer.value.scrollTop = 0
    })
  } catch (e) { logs.value = [] }
}

const onTaskChange = () => {
  pageNum.value = 1
  fetchLogs()
}

onMounted(fetchTasks)
</script>
