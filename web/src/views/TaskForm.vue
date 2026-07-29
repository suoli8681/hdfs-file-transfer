<template>
  <el-card>
    <template #header><span>{{ isEdit ? '编辑任务' : '新建任务' }}</span></template>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" style="max-width:700px">
      <el-form-item label="任务名称" prop="taskName">
        <el-input v-model="form.taskName" placeholder="输入任务名称" />
      </el-form-item>
      <el-form-item label="任务类型" prop="taskType">
        <el-radio-group v-model="form.taskType">
          <el-radio value="once">一次性</el-radio>
          <el-radio value="scheduled">定时同步</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.taskType === 'scheduled'" label="Cron表达式" prop="cronExpr">
        <el-input v-model="form.cronExpr" placeholder="如: 0 0 2 * * ?" />
      </el-form-item>
      <el-form-item label="源集群" prop="sourceCluster">
        <el-select v-model="form.sourceCluster" filterable placeholder="选择源集群" style="width:100%">
          <el-option v-for="c in clusters" :key="c.id" :label="c.clusterName" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="源路径" prop="sourcePath">
        <el-input v-model="form.sourcePath" placeholder="如: /data/source 或 /data/${YYYYMMDD-1}" />
        <div style="font-size:12px;color:#909399;margin-top:4px;line-height:1.8">
          支持日期表达式，生成实例时自动替换为实际日期：
          <el-popover placement="bottom-start" :width="420" trigger="hover">
            <template #reference>
              <el-link type="primary" :underline="false" style="font-size:12px">查看示例</el-link>
            </template>
            <el-table :data="dateExprExamples" size="small" border>
              <el-table-column prop="expr" label="表达式" width="180" />
              <el-table-column prop="unit" label="偏移单位" width="80" />
              <el-table-column prop="example" label="示例" />
            </el-table>
          </el-popover>
        </div>
      </el-form-item>
      <el-form-item label="目标集群" prop="targetCluster">
        <el-select v-model="form.targetCluster" filterable placeholder="选择目标集群" style="width:100%">
          <el-option v-for="c in clusters" :key="c.id" :label="c.clusterName" :value="String(c.id)" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标路径" prop="targetPath">
        <el-input v-model="form.targetPath" placeholder="如: /data/target" />
      </el-form-item>
      <el-form-item label="distcp参数">
        <el-input v-model="form.distcpOptions" type="textarea" :rows="3" placeholder="-D mapreduce.job.name=myjob -update -skipcrccheck" />
        <div style="font-size:12px;color:#909399;margin-top:4px;line-height:1.8">
          常用参数：
          <el-popover placement="bottom-start" :width="500" trigger="hover">
            <template #reference>
              <el-link type="primary" :underline="false" style="font-size:12px">查看示例</el-link>
            </template>
            <el-table :data="distcpParams" size="small" border>
              <el-table-column prop="param" label="参数" width="260" />
              <el-table-column prop="desc" label="说明" />
            </el-table>
          </el-popover>
        </div>
      </el-form-item>
      <el-form-item label="执行Agent" prop="agentId">
        <el-select v-model="form.agentId" filterable placeholder="选择Agent" style="width:100%">
          <el-option v-for="a in agents" :key="a.agentId" :label="a.agentHost + '(' + a.agentId + ')'" :value="a.agentId" />
        </el-select>
      </el-form-item>
      <el-form-item label="告警通知">
        <el-switch v-model="form.alertEnabled" />
        <span style="margin-left:10px;font-size:12px;color:#909399">开启后任务失败时发送告警通知</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存' : '创建' }}</el-button>
        <el-button @click="$router.back()">取消</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTask, createTask, updateTask } from '../api/task'
import { getClusterList } from '../api/cluster'
import { getAgentList } from '../api/agent'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const submitting = ref(false)
const clusters = ref([])
const agents = ref([])
const formRef = ref(null)

const dateExprExamples = [
  { expr: '${YYYYMMDD}', unit: '—', example: '20260727' },
  { expr: '${YYYYMMDD-1}', unit: '天', example: '20260726' },
  { expr: '${YYYY-MM-DD}', unit: '—', example: '2026-07-27' },
  { expr: '${YYYY-MM-DD+1}', unit: '天', example: '2026-07-28' },
  { expr: '${YYYYMMDDHH}', unit: '—', example: '2026072717' },
  { expr: '${YYYYMMDDHH-1}', unit: '小时', example: '2026072716' },
  { expr: '${YYYYMMDDHHmm}', unit: '—', example: '202607271730' },
  { expr: '${YYYYMMDDHHmm-1}', unit: '分钟', example: '202607271729' },
  { expr: '${YYYYMMDDHHmmss}', unit: '—', example: '20260727173025' },
  { expr: '${YYYYMMDDHHmmss-1}', unit: '秒', example: '20260727173024' }
]

const distcpParams = [
  { param: '-update', desc: '增量同步，目标已存在的文件跳过（大小一致时）' },
  { param: '-skipcrccheck', desc: '跳过 CRC 校验，加快同步速度' },
  { param: '-overwrite', desc: '覆盖目标已存在的文件' },
  { param: '-delete', desc: '删除目标端源端不存在的文件（慎用）' },
  { param: '-p', desc: '保留权限、属主、组、时间戳等属性' },
  { param: '-i', desc: '忽略失败，继续复制其他文件' },
  { param: '-atomic', desc: '原子性提交，全部成功后才可见' },
  { param: '-m <N>', desc: '指定 map 任务数量，如 -m 10' },
  { param: '-bandwidth <MB>', desc: '限制每个 map 的带宽，如 -bandwidth 50（MB/s）' },
  { param: '-D mapreduce.job.name=xxx', desc: '指定 MapReduce 作业名称' },
  { param: '-D mapreduce.job.queuename=xxx', desc: '指定提交到的 YARN 队列' },
  { param: '-D dfs.replication=3', desc: '指定目标端副本数' },
  { param: '-D mapreduce.job.maxmap=20', desc: '限制最大 map 数量' },
  { param: '-strategy dynamic', desc: '使用动态策略分配任务（适用于大量小文件）' }
]

const requiredMsg = (label) => { return { required: true, message: '请选择' + label, trigger: 'change' } }
const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  cronExpr: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }],
  sourceCluster: requiredMsg('源集群'),
  sourcePath: [{ required: true, message: '请输入源路径', trigger: 'blur' }],
  targetCluster: requiredMsg('目标集群'),
  targetPath: [{ required: true, message: '请输入目标路径', trigger: 'blur' }],
  agentId: requiredMsg('执行Agent')
}

const form = ref({
  taskName: '', taskType: 'once', sourceCluster: '', sourcePath: '',
  targetCluster: '', targetPath: '', distcpOptions: '', cronExpr: '', agentId: '',
  alertEnabled: true
})

onMounted(async () => {
  try { clusters.value = await getClusterList() || [] } catch (e) {}
  try { agents.value = await getAgentList() || [] } catch (e) {}
  if (isEdit.value) {
    const data = await getTask(route.params.id)
    if (data) {
      form.value = {
        taskId: data.id, taskName: data.taskName, taskType: data.taskType,
        sourceCluster: String(data.sourceClusterId || ''),
        sourcePath: data.sourcePath,
        targetCluster: String(data.targetClusterId || ''),
        targetPath: data.targetPath,
        distcpOptions: data.distcpOptions || '',
        cronExpr: data.cronExpr || '',
        agentId: data.agentId || '',
        alertEnabled: data.alertEnabled != null ? data.alertEnabled : true
      }
    }
  }
})

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) { await updateTask(form.value) } else { await createTask(form.value) }
    ElMessage.success(isEdit.value ? '保存成功' : '创建成功')
    router.push('/tasks')
  } catch (e) { ElMessage.error(e.message || '操作失败') }
  submitting.value = false
}
</script>