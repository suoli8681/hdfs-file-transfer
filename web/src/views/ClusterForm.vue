<template>
  <el-card>
    <template #header><span>{{ isEdit ? '编辑集群' : '新增集群' }}</span></template>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" style="max-width:600px">
      <el-form-item label="集群名称" prop="clusterName">
        <el-input v-model="form.clusterName" placeholder="输入集群名称" />
      </el-form-item>
      <el-form-item label="集群类型" prop="clusterType">
        <el-select v-model="form.clusterType" style="width:100%">
          <el-option label="Hadoop" value="hadoop" />
          <el-option label="HDFS" value="hdfs" />
        </el-select>
      </el-form-item>
      <el-form-item label="NameNode" prop="nameNodeRpc">
        <el-input v-model="form.nameNodeRpc" placeholder="如: 192.168.1.131:8020" />
      </el-form-item>
      <el-form-item label="Nameservice">
        <el-input v-model="form.nameService" placeholder="HA模式时填写" />
      </el-form-item>
      <el-form-item label="HDFS用户" prop="hdfsUser">
        <el-input v-model="form.hdfsUser" placeholder="hdfs" />
      </el-form-item>
      <el-form-item label="配置目录">
        <el-input v-model="form.confDir" placeholder="如: /etc/hadoop/conf" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" />
      </el-form-item>
      <el-form-item label="启用">
        <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
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
import { getCluster, createCluster, updateCluster } from '../api/cluster'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const submitting = ref(false)

const formRef = ref(null)
const rules = {
  clusterName: [{ required: true, message: '请输入集群名称', trigger: 'blur' }],
  clusterType: [{ required: true, message: '请选择集群类型', trigger: 'change' }],
  nameNodeRpc: [{ required: true, message: '请输入NameNode RPC地址', trigger: 'blur' }],
  hdfsUser: [{ required: true, message: '请输入HDFS用户', trigger: 'blur' }]
}

const form = ref({
  clusterName: '', clusterType: 'hadoop', nameNodeRpc: '', nameNodeHttp: '',
  nameService: '', hdfsUser: 'hdfs', confDir: '', description: '', isEnabled: 1
})

onMounted(async () => {
  if (isEdit.value) {
    const data = await getCluster(route.params.id)
    if (data) Object.assign(form.value, data)
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
    if (isEdit.value) { await updateCluster(form.value) } else { await createCluster(form.value) }
    ElMessage.success(isEdit.value ? '保存成功' : '创建成功')
    router.push('/clusters')
  } catch (e) { ElMessage.error('操作失败') }
  submitting.value = false
}
</script>
