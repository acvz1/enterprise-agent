import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/services/api'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const username = ref<string | null>(localStorage.getItem('username'))
  const email = ref<string | null>(localStorage.getItem('email'))
  
  const isAuthenticated = computed(() => !!accessToken.value)
  
  // 登录
  const login = async (loginUsername: string, password: string) => {
    try {
      // axios 响应拦截器已经返回 response.data，所以这里直接是数据
      const data = await authApi.login(loginUsername, password)
      
      // 保存 Token 和用户信息
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      username.value = data.username
      email.value = data.email
      
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      localStorage.setItem('username', data.username)
      localStorage.setItem('email', data.email)
      
      return data
    } catch (error) {
      console.error('登录失败:', error)
      throw error
    }
  }
  
  // 注册
  const register = async (data: { username: string; password: string; email: string; phone?: string; nickname?: string }) => {
    try {
      // axios 响应拦截器已经返回 response.data
      const response = await authApi.register(data)
      return response
    } catch (error) {
      console.error('注册失败:', error)
      throw error
    }
  }
  
  // 刷新 Token
  const refresh = async () => {
    if (!refreshToken.value) {
      throw new Error('没有刷新令牌')
    }
    
    try {
      // axios 响应拦截器已经返回 response.data
      const data = await authApi.refreshToken(refreshToken.value)
      
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)
      
      return data
    } catch (error) {
      console.error('刷新Token失败:', error)
      logout()
      throw error
    }
  }
  
  // 登出
  const logout = () => {
    accessToken.value = null
    refreshToken.value = null
    username.value = null
    email.value = null
    
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    localStorage.removeItem('email')
  }
  
  return {
    accessToken,
    refreshToken,
    username,
    email,
    isAuthenticated,
    login,
    register,
    refresh,
    logout
  }
})
