<template>
  <div>
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="3" v-for="card in cards" :key="card.label">
        <el-card shadow="hover">
          <div style="text-align: center">
            <div style="font-size: 28px; font-weight: bold; color: #409EFF">{{ card.value }}</div>
            <div style="font-size: 13px; color: #999; margin-top: 8px">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card>
          <template #header><span>最近任务</span></template>
          <el-table :data="recentTasks" stripe size="small" style="width: 100%">
            <el-table-column prop="taskName" label="任务名称" />
            <el-table-column prop="sourcePath" label="源路径" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastExecTime" label="启动时间" width="160" />
            <el-table-column prop="createTime" label="创建时间" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header><span>Agent状态</span></template>
          <div v-if="agents.length === 0" style="text-align:center;color:#999;padding:20px">暂无Agent</div>
          <div v-for="a in agents" :key="a.id" style="display:flex;align-items:center;padding:6px 0;border-bottom:1px solid #f0f0f0">
            <el-tag :type="agentStatusType(a.status)" size="small" style="margin-right:10px">{{ agentStatusLabel(a.status) }}</el-tag>
            <span style="flex:1">{{ a.agentHost }}</span>
            <span style="font-size:12px;color:#999">{{ a.runningTaskCount || 0 }}/{{ a.maxParallelTasks || 0 }} 任务</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getOverview, getRecentTasks } from '../api/dashboard'
import { getAgentList } from '../api/agent'
import { statusType, statusLabel } from '../utils'

const cards = ref([
  { label: '总任务数', value: 0 },
  { label: '运行中', value: 0 },
  { label: '已完成', value: 0 },
  { label: '失败', value: 0 },
  { label: '已停止', value: 0 },
  { label: '已终止', value: 0 },
  { label: '已迁移数据', value: '0 B' },
  { label: 'Agent在线', value: 0 }
])
const recentTasks = ref([])
const agents = ref([])

const agentStatusType = (s) => ({ online: 'success', running: 'primary', busy: 'warning', offline: 'danger' }[s] || 'info')
const agentStatusLabel = (s) => ({ online: '在线', running: '运行中', busy: '忙碌', offline: '离线' }[s] || s)

onMounted(async () => {
  try {
    const overview = await getOverview()
    cards.value[0].value = overview.totalTasks || 0
    cards.value[1].value = overview.runningTasks || 0
    cards.value[2].value = overview.successTasks || 0
    cards.value[3].value = overview.failedTasks || 0
    cards.value[4].value = overview.stoppedTasks || 0
    cards.value[5].value = overview.killedTasks || 0
    const bytes = overview.totalTransferredBytes || 0
    cards.value[6].value = bytes > 1073741824 ? (bytes / 1073741824).toFixed(2) + ' GB' : (bytes / 1048576).toFixed(2) + ' MB'
    cards.value[7].value = overview.onlineAgents || 0
  } catch (e) { console.error(e) }
  try { recentTasks.value = await getRecentTasks() } catch (e) {}
  try { agents.value = await getAgentList() } catch (e) {}
})
</script>