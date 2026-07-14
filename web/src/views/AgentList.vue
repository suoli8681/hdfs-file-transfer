<template>
  <el-card>
    <template #header><span>Agent节点</span></template>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="agentId" label="Agent ID" width="200" />
      <el-table-column prop="agentHost" label="主机地址" width="140" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'online' ? 'success' : row.status === 'busy' ? 'warning' : 'danger'" size="small">
            {{ {online:'在线',offline:'离线',busy:'忙碌'}[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="runningTaskCount" label="运行任务" width="80" />
      <el-table-column prop="maxParallelTasks" label="最大并行" width="80" />
      <el-table-column label="资源" width="160">
        <template #default="{ row }">
          <div style="font-size:12px">CPU: {{ (row.cpuUsage || 0).toFixed(1) }}%</div>
          <div style="font-size:12px">内存: {{ (row.memoryUsage || 0).toFixed(1) }}%</div>
        </template>
      </el-table-column>
      <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="160" />
      <el-table-column prop="version" label="版本" width="80" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAgentList } from '../api/agent'

const list = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try { list.value = await getAgentList() || [] } catch (e) {}
  loading.value = false
})
</script>