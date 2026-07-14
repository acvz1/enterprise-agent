<template>
  <div class="login-container">
    <n-card class="login-card" title="用户登录">
      <n-form
        ref="formRef"
        :model="formValue"
        :rules="rules"
        size="large"
      >
        <n-form-item path="username" label="用户名">
          <n-input
            v-model:value="formValue.username"
            placeholder="请输入用户名"
            @keyup.enter="handleLogin"
          />
        </n-form-item>
        
        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="formValue.password"
            type="password"
            show-password-on="mousedown"
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          />
        </n-form-item>
        
        <n-form-item>
          <n-button
            type="primary"
            block
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </n-button>
        </n-form-item>
        
        <n-form-item>
          <n-space justify="space-between" style="width: 100%">
            <router-link to="/register">
              <n-button text>注册账号</n-button>
            </router-link>
            <n-button text disabled>忘记密码？</n-button>
          </n-space>
        </n-form-item>
      </n-form>
      
      <template #footer>
        <n-alert type="info" title="默认账户" closable>
          管理员账户：admin / admin123
        </n-alert>
      </template>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, NAlert, type FormInst, type FormRules } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const formValue = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  try {
    await formRef.value?.validate()
    
    loading.value = true
    await authStore.login(formValue.username, formValue.password)
    
    message.success('登录成功')
    router.push('/')
  } catch (error: any) {
    console.error('登录失败:', error)
    message.error(error.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  max-width: 90%;
}
</style>
