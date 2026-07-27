<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center">
        <span>用户管理</span>
        <el-button type="primary" size="small" @click="openAdd">添加用户</el-button>
      </div>
    </template>
    <div style="margin-bottom:15px">
      <el-input v-model="keyword" placeholder="搜索用户名/姓名/手机号" style="width:240px" size="small" clearable @keyup.enter="fetchData" />
      <el-button type="primary" size="small" @click="fetchData" style="margin-left:10px">查询</el-button>
    </div>
    <el-table :data="list" stripe v-loading="loading" style="width:100%">
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '冻结' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleFreeze(row)">冻结</el-button>
          <el-button v-else type="success" link size="small" @click="handleEnable(row)">启用</el-button>
          <el-button type="primary" link size="small" @click="openReset(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total > 0" v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total,sizes,prev,pager,next" @current-change="fetchData" @size-change="fetchData" style="margin-top:15px;justify-content:flex-end" />

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑用户' : '添加用户'" width="500px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
          <div style="font-size:12px;color:#909399;margin-top:4px">必须字母+数字组合，长度6-8位</div>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="450px" destroy-on-close>
      <el-form :model="resetForm" :rules="resetRules" ref="resetFormRef" label-width="100px">
        <el-form-item label="用户名">
          <span style="font-weight:bold">{{ resetForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" show-password />
          <div style="font-size:12px;color:#909399;margin-top:4px">必须字母+数字组合，长度6-8位</div>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="handleReset">确认重置</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUserPage, createUser, updateUser, updateUserStatus, resetPassword } from '../api/user'
import { ElMessage } from 'element-plus'

const passwordValidator = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 6 || value.length > 8) {
    callback(new Error('密码长度必须为6-8位'))
  } else if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
    callback(new Error('密码必须包含字母和数字'))
  } else {
    callback()
  }
}

const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')

const formVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ username: '', password: '', realName: '', email: '', phone: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, validator: passwordValidator, trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }]
}

const resetVisible = ref(false)
const resetting = ref(false)
const resetFormRef = ref(null)
const resetForm = ref({ id: null, username: '', newPassword: '', confirmPassword: '' })
const resetRules = {
  newPassword: [{ required: true, validator: passwordValidator, trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, {
    validator: (rule, value, callback) => {
      if (value !== resetForm.value.newPassword) callback(new Error('两次密码不一致'))
      else callback()
    },
    trigger: 'blur'
  }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getUserPage({ pageNum: pageNum.value, pageSize: pageSize.value, keyword: keyword.value || undefined })
    list.value = res.records || []
    total.value = res.total || 0
  } catch (e) { ElMessage.error('获取用户列表失败') }
  loading.value = false
}

const openAdd = () => {
  isEdit.value = false
  form.value = { username: '', password: '', realName: '', email: '', phone: '' }
  formVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  form.value = { id: row.id, username: row.username, realName: row.realName, email: row.email, phone: row.phone }
  formVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.value)
      ElMessage.success('保存成功')
    } else {
      await createUser(form.value)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
  submitting.value = false
}

const handleFreeze = async (row) => {
  try {
    await updateUserStatus(row.id, 0)
    ElMessage.success('已冻结')
    fetchData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

const handleEnable = async (row) => {
  try {
    await updateUserStatus(row.id, 1)
    ElMessage.success('已启用')
    fetchData()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

const openReset = (row) => {
  resetForm.value = { id: row.id, username: row.username, newPassword: '', confirmPassword: '' }
  resetVisible.value = true
}

const handleReset = async () => {
  if (!resetFormRef.value) return
  try { await resetFormRef.value.validate() } catch { return }
  resetting.value = true
  try {
    await resetPassword(resetForm.value.id, resetForm.value.newPassword)
    ElMessage.success('密码重置成功')
    resetVisible.value = false
  } catch (e) { ElMessage.error(e.message || '重置失败') }
  resetting.value = false
}

onMounted(() => { fetchData() })
</script>
