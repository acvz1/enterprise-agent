<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  useMessage,
  type FormInst,
  type FormItemRule,
  type FormRules
} from 'naive-ui'
import {
  ArrowBackOutline,
  GitMergeOutline,
  LockClosedOutline,
  MailOutline,
  PersonOutline
} from '@vicons/ionicons5'
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

const validatePasswordSame = (_rule: FormItemRule, value: string): boolean =>
  value === formValue.password

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度必须在 3-50 之间', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度必须在 6-100 之间', trigger: 'blur' }
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
    await authStore.register({
      username: formValue.username,
      password: formValue.password,
      email: formValue.email,
      nickname: formValue.nickname || undefined,
      phone: formValue.phone || undefined
    })
    message.success('注册成功，请登录')
    router.push('/login')
  } catch (error: any) {
    message.error(error.response?.data?.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="register-page">
    <section class="register-card">
      <div class="register-brand">
        <div class="brand-mark"><git-merge-outline /></div>
        <div>
          <strong>NEXUS</strong>
          <span>企业知识中枢</span>
        </div>
      </div>

      <div class="register-heading">
        <span>CREATE WORKSPACE ACCOUNT</span>
        <h1>注册企业成员</h1>
        <p>账号权限由管理员配置，Guest 默认不能读取文档或发起问答。</p>
      </div>

      <n-form ref="formRef" :model="formValue" :rules="rules" size="large">
        <div class="form-grid">
          <n-form-item path="username" label="用户名">
            <n-input v-model:value="formValue.username" placeholder="3-50 个字符">
              <template #prefix><n-icon><person-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-form-item path="email" label="邮箱">
            <n-input v-model:value="formValue.email" placeholder="name@company.com">
              <template #prefix><n-icon><mail-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-form-item path="password" label="密码">
            <n-input
              v-model:value="formValue.password"
              type="password"
              show-password-on="mousedown"
              placeholder="至少 6 个字符"
            >
              <template #prefix><n-icon><lock-closed-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-form-item path="confirmPassword" label="确认密码">
            <n-input
              v-model:value="formValue.confirmPassword"
              type="password"
              show-password-on="mousedown"
              placeholder="再次输入密码"
            >
              <template #prefix><n-icon><lock-closed-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-form-item path="nickname" label="昵称（可选）">
            <n-input v-model:value="formValue.nickname" placeholder="企业内显示名称" />
          </n-form-item>

          <n-form-item path="phone" label="手机号（可选）">
            <n-input v-model:value="formValue.phone" placeholder="联系手机号" />
          </n-form-item>
        </div>

        <n-button type="primary" block class="register-button" :loading="loading" @click="handleRegister">
          创建账号
        </n-button>
      </n-form>

      <button type="button" class="back-link" @click="router.push('/login')">
        <n-icon><arrow-back-outline /></n-icon>
        返回登录
      </button>
    </section>
  </main>
</template>

<style scoped>
.register-page {
  display: grid;
  place-items: center;
  width: 100%;
  min-height: 100vh;
  padding: 36px;
  background:
    radial-gradient(circle at 12% 10%, rgba(35, 148, 123, 0.15), transparent 26%),
    linear-gradient(145deg, #102f2c 0%, #0b2322 46%, #eef4f1 46%, #f5f7f6 100%);
}

.register-card {
  width: 100%;
  max-width: 760px;
  padding: 34px 38px;
  background: #fff;
  border: 1px solid #dce6e2;
  border-radius: 22px;
  box-shadow: 0 28px 80px rgba(14, 45, 39, 0.17);
}

.register-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: #12352f;
  background: #8bddc6;
  border-radius: 11px;
}

.brand-mark svg {
  width: 20px;
  height: 20px;
}

.register-brand > div:last-child {
  display: flex;
  flex-direction: column;
}

.register-brand strong {
  color: var(--kb-ink);
  font-size: 14px;
  letter-spacing: 0.15em;
}

.register-brand span {
  color: var(--kb-muted);
  font-size: 8px;
}

.register-heading {
  margin: 31px 0 24px;
}

.register-heading > span {
  color: var(--kb-primary);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.register-heading h1 {
  margin: 6px 0 5px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 28px;
  font-weight: 700;
}

.register-heading p {
  color: var(--kb-muted);
  font-size: 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
}

.register-card :deep(.n-form-item-label) {
  color: #405a54;
  font-size: 9px;
  font-weight: 700;
}

.register-card :deep(.n-input) {
  background: #f8faf9;
}

.register-button {
  height: 44px;
  margin-top: 4px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 20px auto 0;
  color: var(--kb-muted);
  font-size: 9px;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.back-link:hover {
  color: var(--kb-primary);
}

@media (max-width: 680px) {
  .register-page {
    padding: 18px;
  }

  .register-card {
    padding: 26px 22px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
