<template>
  <div class="login-container">
    <div class="login-right">
      <div class="login-card">
        <h2 class="login-title">HDFS迁移平台</h2>
        <p class="login-subtitle">统一数据迁移 · 监控 · 调度 · 告警管理</p>
        <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" size="large" prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" size="large" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">登录</el-button>
          </el-form-item>
        </el-form>
        <p class="login-copyright">Copyright 2026 Data Platform</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const loading = ref(false)

const form = ref({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch { return }

  loading.value = true
  try {
    const res = await login(form.value.username, form.value.password)
    localStorage.setItem('token', res.token)
    localStorage.setItem('username', res.username)
    localStorage.setItem('realName', res.realName || '')
    localStorage.setItem('role', res.role || 'user')
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  }
  loading.value = false
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  width: 100vw;
  margin: 0;
  padding: 0;
  background: url('/login-bg.png') no-repeat center center fixed;
  background-size: cover;
  overflow: hidden;
}

.login-right {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 600px;
  margin-left: 1050px;
}

.login-card {
  width: 450px;
  padding: 120px 50px 120px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.login-title {
  text-align: center;
  font-size: 24px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px 0;
}

.login-subtitle {
  text-align: center;
  font-size: 13px;
  color: #8c8c8c;
  margin: 0 0 30px 0;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}

.login-copyright {
  text-align: center;
  font-size: 12px;
  color: #bfbfbf;
  margin: 20px 0 0 0;
}
</style>
