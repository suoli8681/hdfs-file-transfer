<template>
  <el-card>
    <template #header>
      <span>登录日志</span>
    </template>
    <div style="margin-bottom:15px">
      <el-input v-model="keyword" placeholder="搜索用户名" style="width:240px" size="small" clearable @keyup.enter="fetchData" />
      <el-button type="primary" size="small" @click="fetchData" style="margin-left:10px">查询</el-button>
    </div>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="username" label="用户名" width="200" />
      <el-table-column prop="loginIp" label="登录IP" min-width="180" />
      <el-table-column prop="createTime" label="登录时间" width="200" />
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" :page-size="pageSize" :total="total" layout="prev,pager,next" @current-change="fetchData" style="margin-top:15px;justify-content:center" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getLoginLogPage } from '../api/loginlog'
import { ElMessage } from 'element-plus'

const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getLoginLogPage({ pageNum: pageNum.value, pageSize: pageSize.value, username: keyword.value || undefined })
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) { ElMessage.error('获取登录日志失败') }
  loading.value = false
}

onMounted(() => { fetchData() })
</script>
