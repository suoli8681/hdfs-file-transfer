<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>校验结果</span>
        <div>
          <el-input v-model="taskName" placeholder="实例名称" style="width:150px;margin-right:10px" size="small" clearable />
          <el-button type="primary" size="small" @click="fetchData">查询</el-button>
        </div>
      </div>
    </template>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="taskName" label="实例名称" width="220" />
      <el-table-column prop="verifyStatus" label="校验状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.verifyStatus === 'match' ? 'success' : row.verifyStatus === 'mismatch' ? 'danger' : 'info'" size="small">
            {{ {match:'一致',mismatch:'不一致',error:'异常',pending:'待校验'}[row.verifyStatus] || row.verifyStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceFileCount" label="源端文件数" width="120" />
      <el-table-column prop="targetFileCount" label="目标端文件数" width="120" />
      <el-table-column prop="sourceTotalSize" label="源端数据量" width="120">
        <template #default="{ row }">{{ $formatSize(row.sourceTotalSize) }}</template>
      </el-table-column>
      <el-table-column prop="targetTotalSize" label="目标端数据量" width="120">
        <template #default="{ row }">{{ $formatSize(row.targetTotalSize) }}</template>
      </el-table-column>
      <el-table-column prop="diffFileList" label="差异文件" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip v-if="row.diffFileList" :content="row.diffFileList" placement="top">
            <el-button type="danger" link size="small">查看差异</el-button>
          </el-tooltip>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="校验时间" width="160" />
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total,sizes,prev,pager,next" @current-change="fetchData" @size-change="fetchData" style="margin-top:15px;justify-content:flex-end" />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getVerifyPage } from '../api/verify'

const list = ref([])
const loading = ref(false)
const taskName = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getVerifyPage({ pageNum: pageNum.value, pageSize: pageSize.value, taskName: taskName.value || undefined })
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('获取校验结果失败', e)
  }
  loading.value = false
}

onMounted(fetchData)
</script>