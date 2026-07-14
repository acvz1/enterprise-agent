<template>
  <div class="register-container">
    <n-card class="register-card" title="用户注册">
      <n-form
        ref="formRef"
        :model="formValue"
        :rules="rules"
        size="large"
      >
        <n-form-item path="username" label="用户名">
          <n-input
            v-model:value="formValue.username"
            placeholder="请输入用户名（3-50个字符）"
          />
        </n-form-item>
        
        <n-form-item path="email" label="邮箱">
          <n-input
            v-model:value="formValue.email"
            placeholder="请输入邮箱"
          />
        </n-form-item>
        
        <n-form-item path="password" label="密码">
          <n-input
            v-model:value="formValue.password"
            type="password"
            show-password-on="mousedown"
            placeholder="请输入密码（6-100个字符）"
          />
        </n-form-item>
        
        <n-form-item path="confirmPassword" label="确认密码">
          <n-input
            v-model:value="formValue.confirmPassword"
            type="password"
            show-password-on="mousedown"
            placeholder="请再次输入密码"
          />
        </n-form-item>
        
        <n-form-item path="nickname" label="昵称（可选）">
          <n-input
            v-model:value="formValue.nickname"
            placeholder="请输入昵称"
          />
        </n-form-item>
        
        <n-form-item path="phone" label="手机号（可选）">
          <n-input
            v-model:value="formValue.phone"
            placeholder="请输入手机号"
          />
        </n-form-item>
        
        <n-form-item>
          <n-button
            type="primary"
            block
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </n-button>
        </n-form-item>
        
        <n-form-item>
          <n-space justify="center" style="width: 100%">
            <router-link to="/login">
              <n-button text>已有账号？立即登录</n-button>
            </router-link>
          </n-space>
        </n-form-item>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, type FormInst, type FormRules, type FormItemRule } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()

const formRef = ref<FormInst | null>(null)
const loading = ref(false)

const formValue = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  phone: ''
})

const validatePasswordSame = (rule: FormItemRule, value: string): boolean => {
  return value === formValue.password
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度必须在3-50之间', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度必须在6-100之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validatePasswordSame, message: '两次密码输入不一致', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  try {
    await formRef.value?.validate()
    
    loading.value = true
    
    const registerData = {
      username: formValue.username,
      password: formValue.password,
      email: formValue.email,
      nickname: formValue.nickname || undefined,
      phone: formValue.phone || undefined
    }
    
    await authStore.register(registerData)
    
    message.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    console.error('注册失败:', error)
    message.error(error.response?.data?.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 450px;
  max-width: 90%;
}
</style>
