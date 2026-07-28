<template>
  <el-container style="height: 100vh">
    <el-aside width="220px" style="background: #304156">
      <div style="height: 60px; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 18px; font-weight: bold; border-bottom: 1px solid rgba(255,255,255,0.1)">
        HDFS迁移平台
      </div>
      <el-menu
        :default-active="activeMenu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
        style="border-right: none"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>监控大盘</span>
        </el-menu-item>
        <el-menu-item index="/clusters">
          <el-icon><Connection /></el-icon>
          <span>集群管理</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon>
          <span>迁移任务</span>
        </el-menu-item>
        <el-menu-item index="/task-instances">
          <el-icon><List /></el-icon>
          <span>任务实例</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <el-icon><Monitor /></el-icon>
          <span>Agent管理</span>
        </el-menu-item>
        <el-menu-item index="/verify">
          <el-icon><CircleCheck /></el-icon>
          <span>校验结果</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/login-logs">
          <el-icon><Document /></el-icon>
          <span>登录日志</span>
        </el-menu-item>
        <el-menu-item v-if="isAdmin" index="/alert-config">
          <el-icon><Bell /></el-icon>
          <span>告警配置</span>
        </el-menu-item>
        <el-menu-item index="/ai-chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 助手</span>
        </el-menu-item>
        <el-menu-item index="/ai-config">
          <el-icon><Setting /></el-icon>
          <span>AI 模型配置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 50px">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div style="display:flex;align-items:center;gap:15px">
          <span style="font-size:14px;color:#606266">{{ realName || username }}</span>
          <el-dropdown @command="handleCommand">
            <el-avatar size="small" style="cursor:pointer">{{ (realName || username || '?').charAt(0) }}</el-avatar>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main style="background: #f0f2f5; padding: 20px">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 个人中心弹窗 -->
  <el-dialog v-model="profileVisible" title="个人中心" width="500px" destroy-on-close>
    <el-form :model="profileForm" label-width="80px" v-loading="profileLoading">
      <el-form-item label="用户名">
        <el-input v-model="profileForm.username" />
      </el-form-item>
      <el-form-item label="真实姓名">
        <el-input v-model="profileForm.realName" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="profileForm.email" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="profileForm.phone" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="profileVisible = false">取消</el-button>
      <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
    </template>
  </el-dialog>

  <!-- 修改密码弹窗 -->
  <el-dialog v-model="passwordVisible" title="修改密码" width="450px" destroy-on-close>
    <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
      <el-form-item label="原密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSaving" @click="savePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo, updateProfile, changePassword } from '../api/auth'
import request from '../api/index'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title)
const username = ref(localStorage.getItem('username') || '')
const realName = ref(localStorage.getItem('realName') || '')
const isAdmin = ref(localStorage.getItem('role') === 'admin')

onMounted(async () => {
  try {
    const res = await request.get('/users/current')
    if (res.role === 'admin') {
      isAdmin.value = true
      localStorage.setItem('role', 'admin')
    } else {
      isAdmin.value = false
      localStorage.setItem('role', 'user')
    }
  } catch (e) {}
})

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('role')
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (cmd === 'profile') {
    openProfile()
  } else if (cmd === 'password') {
    openPassword()
  }
}

// Profile
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileSaving = ref(false)
const profileForm = ref({ username: '', realName: '', email: '', phone: '' })

const openProfile = async () => {
  profileVisible.value = true
  profileLoading.value = true
  try {
    const res = await getUserInfo()
    profileForm.value = {
      username: res.username || '',
      realName: res.realName || '',
      email: res.email || '',
      phone: res.phone || ''
    }
  } catch (e) {
    ElMessage.error('获取用户信息失败')
  }
  profileLoading.value = false
}

const saveProfile = async () => {
  profileSaving.value = true
  try {
    const res = await updateProfile(profileForm.value)
    // Update localStorage if username/realName changed
    if (res.token) {
      localStorage.setItem('token', res.token)
    }
    if (res.username) {
      localStorage.setItem('username', res.username)
      username.value = res.username
    }
    if (res.realName !== undefined) {
      localStorage.setItem('realName', res.realName)
      realName.value = res.realName
    }
    ElMessage.success('保存成功')
    profileVisible.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
  profileSaving.value = false
}

// Password
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref(null)
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const openPassword = () => {
  passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  passwordVisible.value = true
}

const savePassword = async () => {
  if (!passwordFormRef.value) return
  try {
    await passwordFormRef.value.validate()
  } catch { return }
  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordVisible.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('realName')
    localStorage.removeItem('role')
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.message || '修改失败')
  }
  passwordSaving.value = false
}
</script>