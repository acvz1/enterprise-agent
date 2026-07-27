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
  type FormRules
} from 'naive-ui'
import {
  ArrowForwardOutline,
  CheckmarkCircleOutline,
  GitMergeOutline,
  LockClosedOutline,
  PersonOutline,
  ShieldCheckmarkOutline
} from '@vicons/ionicons5'
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
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  try {
    await formRef.value?.validate()
    loading.value = true
    await authStore.login(formValue.username, formValue.password)
    message.success('登录成功')
    router.push('/')
  } catch (error: any) {
    const responseData = error.response?.data
    const errorMessage = typeof responseData === 'string'
      ? responseData
      : responseData?.message
    message.error(errorMessage || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="story-content">
        <div class="login-brand">
          <div class="login-brand-mark"><git-merge-outline /></div>
          <div>
            <strong>NEXUS</strong>
            <span>企业知识中枢</span>
          </div>
        </div>

        <div class="story-copy">
          <span class="story-eyebrow">ENTERPRISE KNOWLEDGE AGENT</span>
          <h1>让每一次企业问答<br />都有依据可循</h1>
          <p>
            融合 Redis 向量检索与 Elasticsearch BM25，
            由 Agent 按需调用知识库，并将回答追溯到具体文档切片。
          </p>
        </div>

        <div class="story-features">
          <div>
            <n-icon><checkmark-circle-outline /></n-icon>
            <span><strong>混合检索</strong>语义与关键词双路召回</span>
          </div>
          <div>
            <n-icon><shield-checkmark-outline /></n-icon>
            <span><strong>权限边界</strong>未经授权无法读取或提问</span>
          </div>
          <div>
            <n-icon><lock-closed-outline /></n-icon>
            <span><strong>证据引用</strong>回答关联权威原文数据</span>
          </div>
        </div>
      </div>

      <div class="story-decoration" aria-hidden="true">
        <i></i><i></i><i></i>
      </div>
      <span class="story-version">RAG PIPELINE · V2.0</span>
    </section>

    <section class="login-form-area">
      <div class="login-card">
        <div class="login-heading">
          <span>SECURE ACCESS</span>
          <h2>登录工作空间</h2>
          <p>使用企业账号访问知识库与智能问答服务</p>
        </div>

        <n-form ref="formRef" :model="formValue" :rules="rules" size="large">
          <n-form-item path="username" label="用户名">
            <n-input
              v-model:value="formValue.username"
              placeholder="请输入用户名"
              @keyup.enter="handleLogin"
            >
              <template #prefix><n-icon><person-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-form-item path="password" label="密码">
            <n-input
              v-model:value="formValue.password"
              type="password"
              show-password-on="mousedown"
              placeholder="请输入密码"
              @keyup.enter="handleLogin"
            >
              <template #prefix><n-icon><lock-closed-outline /></n-icon></template>
            </n-input>
          </n-form-item>

          <n-button
            type="primary"
            block
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            进入知识中枢
            <template #icon><n-icon><arrow-forward-outline /></n-icon></template>
          </n-button>
        </n-form>

        <div class="login-divider"><span>演示环境</span></div>
        <div class="demo-account">
          <div>
            <span>管理员账号</span>
            <strong>admin</strong>
          </div>
          <i></i>
          <div>
            <span>默认密码</span>
            <strong>admin123</strong>
          </div>
        </div>

        <p class="register-link">
          还没有账号？
          <router-link to="/register">申请注册</router-link>
        </p>
      </div>

      <p class="license-note">Enterprise Knowledge Agent · Apache 2.0</p>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(480px, 1.08fr) minmax(460px, 0.92fr);
  width: 100%;
  min-height: 100vh;
  background: #f3f6f4;
}

.login-story {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 100vh;
  padding: 58px clamp(50px, 7vw, 104px);
  overflow: hidden;
  color: #eaf6f2;
  background:
    radial-gradient(circle at 15% 12%, rgba(60, 181, 151, 0.26), transparent 30%),
    radial-gradient(circle at 86% 88%, rgba(52, 140, 121, 0.18), transparent 28%),
    linear-gradient(140deg, #123b36 0%, #0a2221 72%);
}

.story-content {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 620px;
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: clamp(70px, 12vh, 130px);
}

.login-brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #11362f;
  background: #8bddc6;
  border-radius: 13px;
}

.login-brand-mark svg {
  width: 23px;
  height: 23px;
}

.login-brand > div:last-child {
  display: flex;
  flex-direction: column;
}

.login-brand strong {
  color: #fff;
  font-size: 17px;
  letter-spacing: 0.16em;
}

.login-brand span {
  margin-top: 3px;
  color: #8fb2a9;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.story-eyebrow,
.login-heading > span {
  color: #71c9b0;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.story-copy h1 {
  margin: 13px 0 18px;
  color: #fff;
  font-family: Georgia, 'Songti SC', serif;
  font-size: clamp(38px, 4.5vw, 60px);
  font-weight: 700;
  line-height: 1.18;
  letter-spacing: -0.02em;
}

.story-copy p {
  max-width: 560px;
  color: #a5c0b9;
  font-size: 13px;
  line-height: 1.9;
}

.story-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-top: 54px;
}

.story-features > div {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 11px;
  color: #7ccab5;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.065);
  border-radius: 12px;
}

.story-features span {
  color: #8faea6;
  font-size: 9px;
  line-height: 1.55;
}

.story-features strong {
  display: block;
  margin-bottom: 2px;
  color: #dcece7;
  font-size: 10px;
}

.story-decoration i {
  position: absolute;
  border: 1px solid rgba(132, 218, 195, 0.09);
  border-radius: 50%;
}

.story-decoration i:nth-child(1) {
  top: -160px;
  right: -130px;
  width: 440px;
  height: 440px;
}

.story-decoration i:nth-child(2) {
  right: -80px;
  bottom: -200px;
  width: 520px;
  height: 520px;
}

.story-decoration i:nth-child(3) {
  right: 11%;
  bottom: 10%;
  width: 180px;
  height: 180px;
  border-style: dashed;
}

.story-version {
  position: absolute;
  bottom: 24px;
  left: clamp(50px, 7vw, 104px);
  color: #527a71;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.login-form-area {
  position: relative;
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 42px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 36px;
  background: #fff;
  border: 1px solid #dfe8e4;
  border-radius: 20px;
  box-shadow: 0 26px 70px rgba(28, 58, 51, 0.08);
}

.login-heading {
  margin-bottom: 28px;
}

.login-heading h2 {
  margin: 7px 0 5px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 28px;
  font-weight: 700;
}

.login-heading p {
  color: var(--kb-muted);
  font-size: 11px;
}

.login-card :deep(.n-form-item-label) {
  color: #3b544e;
  font-size: 10px;
  font-weight: 700;
}

.login-card :deep(.n-input) {
  background: #f8faf9;
}

.login-button {
  height: 44px;
  margin-top: 4px;
}

.login-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 26px 0 16px;
  color: #a0afab;
  font-size: 8px;
}

.login-divider::before,
.login-divider::after {
  flex: 1;
  height: 1px;
  content: '';
  background: #e8eeeb;
}

.demo-account {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 11px 14px;
  background: #f2f7f5;
  border: 1px solid #e1ece8;
  border-radius: 11px;
}

.demo-account div {
  display: flex;
  flex-direction: column;
}

.demo-account div:last-child {
  align-items: flex-end;
}

.demo-account span {
  color: #80938d;
  font-size: 8px;
}

.demo-account strong {
  margin-top: 2px;
  color: #315950;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.demo-account i {
  width: 1px;
  height: 28px;
  background: #d5e3de;
}

.register-link {
  margin-top: 21px;
  color: #7d908a;
  font-size: 9px;
  text-align: center;
}

.register-link a {
  margin-left: 3px;
  font-weight: 700;
}

.license-note {
  position: absolute;
  bottom: 20px;
  color: #a0afab;
  font-size: 8px;
  letter-spacing: 0.08em;
}

@media (max-width: 920px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-story {
    display: none;
  }

  .login-form-area {
    padding: 24px;
  }
}
</style>
