<template>
  <div class="login-page" ref="pageRef">
    <!-- 标题 -->
    <div class="lh-title" ref="titleRef">
      <span class="lh-label">SIGN IN</span>
      <h2 class="lh-heading">登录</h2>
    </div>

    <!-- 表单 -->
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      @submit.prevent="handleLogin"
      class="lf-form"
    >
      <el-form-item prop="username" class="lf-item" ref="userItemRef">
        <el-input v-model="form.username" placeholder="用户名">
          <template #prefix><el-icon><User /></el-icon></template>
        </el-input>
      </el-form-item>

      <el-form-item prop="password" class="lf-item" ref="passItemRef">
        <el-input v-model="form.password" type="password" placeholder="密码" show-password>
          <template #prefix><el-icon><Lock /></el-icon></template>
        </el-input>
      </el-form-item>

      <!-- 滑块验证码 -->
      <el-form-item class="lf-item lf-captcha-item">
        <SliderCaptcha ref="sliderRef" @verified="onSliderVerified" @error="onSliderError" />
      </el-form-item>

      <el-form-item class="lf-item lf-btn-wrap">
        <el-button type="primary" :loading="loading" native-type="submit" class="lf-submit">
          <span v-if="!loading">登 录</span>
          <span v-else>登录中...</span>
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 底部链接 -->
    <div class="lf-footer">
      <span>还没有账号？</span>
      <router-link to="/register" class="lf-link">立即注册</router-link>
    </div>

    <!-- 快捷体验 -->
    <div class="lf-quick">
      <div class="quick-divider"><span>快捷体验</span></div>
      <div class="quick-grid">
        <button
          v-for="item in quickAccounts"
          :key="item.key"
          class="quick-chip"
          @click="quickLogin(item.key)"
          :style="{ '--dot-color': item.color }"
        >
          <span class="qc-dot"></span>
          {{ item.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login } from '@/services/modules/user'
import { getRoleByKey } from '@/utils/role'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'

const router = useRouter()
const userStore = useUserStore()
const pageRef = ref(null)
const formRef = ref(null)
const sliderRef = ref(null)
const loading = ref(false)
const sliderVerified = ref(false)
const sliderToken = ref('')

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

function onSliderVerified(token) {
  sliderVerified.value = true
  sliderToken.value = token
}

function onSliderError(msg) {
  ElMessage.warning(msg || '验证码加载失败，请刷新重试')
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!sliderVerified.value) {
    ElMessage.warning('请先完成滑块验证')
    return
  }

  loading.value = true
  try {
    const res = await login({
      username: form.username,
      password: form.password,
      sliderToken: sliderToken.value,
    })
    if (res.code === 200) {
      userStore.setUser(res.data.token, res.data.userInfo)
      ElMessage.success('登录成功！')
      const role = getRoleByKey(res.data.userInfo.role)
      router.push(role ? role.home : '/')
    }
  } catch {
    // 登录失败时重置滑块
    sliderVerified.value = false
    sliderToken.value = ''
    sliderRef.value?.reset()
  } finally {
    loading.value = false
  }
}

function quickLogin(username) {
  form.username = username
  form.password = '123456'
  // 快捷登录跳过滑块验证
  sliderVerified.value = true
  handleLogin()
}

const quickAccounts = [
  { key: 'police1', label: '交警', color: '#3b82f6' },
  { key: 'command1', label: '指挥中心', color: '#8b5cf6' },
  { key: 'rescue1', label: '清障救援', color: '#06b6d4' },
  { key: 'admin1', label: '管理员', color: '#f59e0b' },
]

onMounted(() => {
  nextTick(() => {
    if (pageRef.value) {
      const items = pageRef.value.querySelectorAll('.lf-item')
      items.forEach((el, i) => el.style.setProperty('--i', i))
    }
  })
})
</script>

<style lang="scss" scoped>
.login-page {
  position: relative;
}

.lh-title {
  text-align: center;
  margin-bottom: 28px;
  animation: title-in 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes title-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.lh-label {
  display: block;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.25em;
  color: rgba(59, 130, 246, 0.4);
  margin-bottom: 8px;
  text-transform: uppercase;
}

.lh-heading {
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: 28px;
  font-weight: 400;
  color: #1e293b;
  letter-spacing: -0.01em;
  margin: 0;
}

.lf-form {
  .lf-item {
    margin-bottom: 16px;
    animation: item-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
    animation-delay: calc(0.1s + var(--i, 0) * 0.06s);
    transition: transform 0.4s cubic-bezier(0.23, 1, 0.32, 1);
  }
}

@keyframes item-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

:deep(.el-input__wrapper) {
  height: 46px;
  border-radius: 10px !important;
  background: rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.08) inset !important;
  transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);

  &:hover { box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2) inset !important; }
  &.is-focus {
    box-shadow: 0 0 0 1.5px rgba(59, 130, 246, 0.35) inset !important;
    background: rgba(255, 255, 255, 0.85) !important;
  }
}

:deep(.el-input__inner) {
  font-size: 14px;
  color: #1e293b;
  &::placeholder { color: #94a3b8; }
}

:deep(.el-input__prefix) { color: #94a3b8; transition: color 0.3s; }
:deep(.el-input__wrapper.is-focus) .el-input__prefix { color: #3b82f6 !important; }

.lf-btn-wrap {
  margin-top: 4px;
  margin-bottom: 0 !important;
}

.lf-captcha-item {
  margin-bottom: 12px !important;

  :deep(.el-form-item__content) {
    width: 100%;
  }
}

.lf-submit {
  width: 100%;
  height: 46px;
  font-size: 14px;
  letter-spacing: 0.08em;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #6366f1) !important;
  border: none !important;
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.2);
  transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);

  &:hover {
    box-shadow: 0 4px 20px rgba(59, 130, 246, 0.3) !important;
    transform: translateY(-1px);
  }

  &:active { transform: scale(0.98); }
}

.lf-footer {
  text-align: center;
  margin-top: 18px;
  font-size: 13px;
  animation: footer-in 0.5s 0.35s cubic-bezier(0.16, 1, 0.3, 1) both;

  span { color: #94a3b8; }

  .lf-link {
    color: rgba(59, 130, 246, 0.8);
    font-weight: 500;
    text-decoration: none;
    transition: color 0.3s;

    &:hover { color: #3b82f6; }
  }
}

@keyframes footer-in {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.lf-quick {
  margin-top: 18px;
  animation: footer-in 0.5s 0.45s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.quick-divider {
  position: relative;
  text-align: center;
  margin-bottom: 12px;

  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 0;
    right: 0;
    height: 1px;
    background: rgba(59, 130, 246, 0.08);
  }

  span {
    position: relative;
    background: rgba(255, 255, 255, 0.82);
    padding: 0 12px;
    font-size: 10px;
    letter-spacing: 0.15em;
    color: #94a3b8;
    text-transform: uppercase;
  }
}

.quick-grid {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 20px;
  border: 1px solid rgba(59, 130, 246, 0.08);
  background: rgba(59, 130, 246, 0.04);
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);
  outline: none;

  &:hover {
    background: rgba(59, 130, 246, 0.08);
    border-color: rgba(59, 130, 246, 0.15);
    color: #3b82f6;
    transform: translateY(-1px);
  }

  &:active { transform: scale(0.96); }
}

.qc-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--dot-color, #3b82f6);
  animation: dot-pulse 2.4s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% { opacity: 0.4; transform: scale(1); }
  50% { opacity: 0.9; transform: scale(1.4); }
}

@media (max-width: 820px) {
  .lf-form .lf-item { margin-bottom: 14px; }
}
</style>
