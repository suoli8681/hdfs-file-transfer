<template>
  <el-card>
    <template #header><span>告警配置</span></template>

    <!-- Webhook 配置 -->
    <div style="margin-bottom:20px">
      <div style="font-weight:bold;margin-bottom:10px">通知渠道</div>
      <el-table :data="webhooks" stripe border style="width:100%">
        <el-table-column label="渠道" width="120">
          <template #default="{ row }">
            <el-tag :type="row.webhookType === 'wechat' ? 'success' : 'primary'" size="small">
              {{ webhookLabel(row.webhookType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column label="Webhook地址" min-width="400">
          <template #default="{ row }">
            <el-input v-model="row.webhook" size="small" :placeholder="webhookPlaceholder(row.webhookType)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :loading="savingIds.includes(row.id)" @click="handleSaveWebhook(row)">保存</el-button>
            <el-button type="primary" link size="small" :loading="testingIds.includes(row.id)" @click="handleTest(row)">测试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 告警类型开关 -->
    <div>
      <div style="font-weight:bold;margin-bottom:10px">告警类型</div>
      <el-table :data="configs" stripe border style="width:100%">
        <el-table-column label="告警类型" width="200">
          <template #default="{ row }">
            <el-tag :type="alertTagType(row.alertType)" size="small">{{ alertLabel(row.alertType) }}</el-tag>
            <span style="margin-left:8px">{{ row.remark }}</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleConfigUpdate(row)" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="300">
          <template #default="{ row }">
            {{ alertDescription(row.alertType) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAlertConfigs, updateAlertConfig, updateAlertWebhook, testAlertWebhook } from '../api/alert'
import { ElMessage } from 'element-plus'

const configs = ref([])
const webhooks = ref([])
const loading = ref(false)
const testingIds = ref([])

const alertLabel = (type) => ({
  task_failed: '任务失败',
  agent_offline: 'Agent离线',
  agent_online: 'Agent上线',
  verify_mismatch: '校验不一致'
}[type] || type)

const alertTagType = (type) => ({
  task_failed: 'danger',
  agent_offline: 'warning',
  agent_online: 'success',
  verify_mismatch: 'info'
}[type] || 'info')

const alertDescription = (type) => ({
  task_failed: '任务实例执行失败时触发',
  agent_offline: 'Agent心跳超时离线时触发',
  agent_online: 'Agent从离线恢复为在线时触发',
  verify_mismatch: '数据校验源目标端不一致时触发'
}[type] || '')

const webhookLabel = (type) => ({
  wechat: '企业微信',
  dingtalk: '钉钉'
}[type] || type)

const webhookPlaceholder = (type) => type === 'wechat'
  ? 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx'
  : 'https://oapi.dingtalk.com/robot/send?access_token=xxx'

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getAlertConfigs()
    configs.value = (res.configs || []).map(item => ({ ...item, enabled: !!item.enabled }))
    webhooks.value = (res.webhooks || []).map(item => ({ ...item, enabled: !!item.enabled }))
  } catch (e) {
    ElMessage.error('获取告警配置失败')
  }
  loading.value = false
}

const handleConfigUpdate = async (row) => {
  try {
    await updateAlertConfig(row)
    ElMessage.success(row.enabled ? '已开启' : '已关闭')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
    fetchData()
  }
}

const savingIds = ref([])

const handleToggleEnabled = async (row) => {
  if (row.enabled && !row.webhook) {
    row.enabled = false
    ElMessage.warning('请先填写Webhook地址后再开启')
    return
  }
  savingIds.value.push(row.id)
  try {
    await updateAlertWebhook(row)
    ElMessage.success(row.enabled ? '已开启' : '已关闭')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
    fetchData()
  }
  savingIds.value = savingIds.value.filter(id => id !== row.id)
}

const handleSaveWebhook = async (row) => {
  savingIds.value.push(row.id)
  try {
    await updateAlertWebhook(row)
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
    fetchData()
  }
  savingIds.value = savingIds.value.filter(id => id !== row.id)
}

const handleTest = async (row) => {
  if (!row.webhook) {
    ElMessage.warning('请先填写Webhook地址')
    return
  }
  testingIds.value.push(row.id)
  try {
    await testAlertWebhook(row)
    ElMessage.success('测试消息已发送，请检查接收端')
  } catch (e) {
    ElMessage.error('测试发送失败，请检查webhook地址')
  }
  testingIds.value = testingIds.value.filter(id => id !== row.id)
}

onMounted(fetchData)
</script>
