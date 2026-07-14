import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api'

const userInfo = ref(null)
const token = ref(localStorage.getItem('token'))
const router = useRouter()

// Axios interceptor for JWT setup
request.interceptors.request.use(config => {
  if (token.value && !config.skipAuth) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token.value}`
  }
  return config
})

// Response interceptor for token refresh
request.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401 && error.response.data?.message === 'Token expired') {
      // Try to refresh token
      try {
        const refreshResponse = await request.post('/api/auth/refresh', {}, { skipAuth: true })
        token.value = refreshResponse.data.token
        localStorage.setItem('token', token.value)
        // Retry original request
        error.config.headers.Authorization = `Bearer ${token.value}`
        return request(error.config)
      } catch (refreshError) {
        // Refresh failed, redirect to login
        localStorage.removeItem('token')
        token.value = null
        router.push('/login')
        return Promise.reject(refreshError)
      }
    }
    return Promise.reject(error)
  }
)

const login = async (username, password) => {
  try {
    const response = await request.post('/api/auth/login', { username, password }, { skipAuth: true })
    token.value = response.data.token
    userInfo.value = {
      username: response.data.username,
      realName: response.data.realName
    }
    localStorage.setItem('token', token.value)
    return true
  } catch (error) {
    ElMessage.error('登录失败: ' + (error.response?.data?.message || error.message))
    return false
  }
}

const logout = () => {
  token.value = null
  userInfo.value = null
  localStorage.removeItem('token')
  router.push('/login')
}

const isAuthenticated = computed(() => !!token.value && !!userInfo.value)

const authGuard = (to, from, next) => {
  if (!isAuthenticated.value) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
}

const publicPaths = ['/login', '/register', '/']

const routerGuard = (to, from, next) => {
  const isPublicPath = publicPaths.some(path => to.path.startsWith(path))
  if (!isPublicPath && !isAuthenticated.value) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
}

export {
  userInfo,
  token,
  login,
  logout,
  isAuthenticated,
  authGuard,
  routerGuard
}
