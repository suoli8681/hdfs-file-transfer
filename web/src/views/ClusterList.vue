<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>集群配置</span>
        <el-button type="primary" size="small" @click="$router.push('/clusters/add')">新增集群</el-button>
      </div>
    </template>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="clusterName" label="集群名称" />
      <el-table-column prop="nameNodeRpc" label="NameNode" show-overflow-tooltip />
      <el-table-column prop="nameService" label="Nameservice" />
      <el-table-column prop="hdfsUser" label="HDFS用户" width="100" />
      <el-table-column prop="isEnabled" label="状态" width="80">
        <template #default="{ row }"><el-tag :type="row.isEnabled === 1 ? 'success' : 'danger'" size="small">{{ row.isEnabled === 1 ? '启用' : '停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="$router.push('/clusters/' + row.id + '/edit')">编辑</el-button>
          <el-button type="primary" link size="small" @click="testConnect(row.id)">测试</el-button>
          <el-popconfirm title="确定删除?" @confirm="handleDelete(row.id)">
            <template #reference><el-button type="danger" link size="small">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="prev,pager,next" @current-change="fetchData" style="margin-top:15px;justify-content:center" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getClusterPage, deleteCluster, testClusterConnect } from '../api/cluster'
import { ElMessage } from 'element-plus'

const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getClusterPage({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) { ElMessage.error('获取集群列表失败') }
  loading.value = false
}

const handleDelete = async (id) => {
  try { await deleteCluster(id); ElMessage.success('已删除'); fetchData() } catch (e) { ElMessage.error('删除失败') }
}

const testConnect = async (id) => {
  try { await testClusterConnect(id); ElMessage.success('连接成功') } catch (e) { ElMessage.error(e.message || '连接失败') }
}

onMounted(fetchData)
</script>