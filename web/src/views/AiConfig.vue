<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>AI 模型配置</span>
        <el-button type="primary" size="small" @click="openForm()">添加配置</el-button>
      </div>
    </template>
    <el-table :data="configs" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="configName" label="配置名称" width="140" />
      <el-table-column prop="modelName" label="模型名称" width="140" />
      <el-table-column prop="baseUrl" label="API 地址" min-width="250" show-overflow-tooltip />
      <el-table-column prop="temperature" label="温度" width="80" />
      <el-table-column prop="maxTokens" label="最大Token" width="100" />
      <el-table-column prop="isDefault" label="默认" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="success" size="small">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openForm(row)">编辑</el-button>
          <el-button type="info" link size="small" :loading="testingIds.includes(row.id)" @click="handleTest(row)">测试</el-button>
          <el-button v-if="row.isDefault !== 1" type="success" link size="small" @click="handleSetDefault(row.id)">设为默认</el-button>
          <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleToggleStatus(row, 0)">禁用</el-button>
          <el-button v-else type="success" link size="small" @click="handleToggleStatus(row, 1)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑配置' : '添加配置'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="API 地址" required>
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" :required="!form.id">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="form.id ? '留空不修改' : ''" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="gpt-4o / glm-4 / qwen-plus" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="最大 Token">
          <el-input-number v-model="form.maxTokens" :min="100" :max="8000" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listConfigs, createConfig, updateConfig, setDefaultConfig, testConnection } from '../api/ai'
import { ElMessage } from 'element-plus'

const configs = ref([])
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const form = ref({})
const testingIds = ref([])

const fetchData = async () => {
  loading.value = true
  try { configs.value = await listConfigs() || [] } catch (e) { ElMessage.error('获取配置失败') }
  loading.value = false
}

const openForm = (row) => {
  if (row) {
    form.value = { ...row, apiKey: '' }
  } else {
    form.value = { configName: '', baseUrl: '', apiKey: '', modelName: '', temperature: 0.7, maxTokens: 2000 }
  }
  formVisible.value = true
}

const handleSave = async () => {
  if (!form.value.configName || !form.value.baseUrl || !form.value.modelName) {
    ElMessage.error('请填写必填项')
    return
  }
  if (!form.value.id && !form.value.apiKey) {
    ElMessage.error('请输入 API Key')
    return
  }
  saving.value = true
  try {
    if (form.value.id) {
      await updateConfig(form.value)
    } else {
      await createConfig(form.value)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
  saving.value = false
}

const handleSetDefault = async (id) => {
  try {
    await setDefaultConfig(id)
    ElMessage.success('已设为默认')
    fetchData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

const handleToggleStatus = async (row, status) => {
  try {
    await updateConfig({ id: row.id, status })
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
    fetchData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

const handleTest = async (row) => {
  if (testingIds.value.includes(row.id)) return
  testingIds.value.push(row.id)
  try {
    const res = await testConnection(row.id)
    if (res && res.startsWith('连接成功')) {
      ElMessage.success(res)
    } else {
      ElMessage.error(res || '连接失败')
    }
  } catch (e) { ElMessage.error(e.message || '测试失败') }
  testingIds.value = testingIds.value.filter(id => id !== row.id)
}

onMounted(() => { fetchData() })
</script>
