<template>
  <div class="register-page">
    <h2 class="form-title">
      <span class="title-en">CREATE ACCOUNT</span>
      <span class="title-cn">用户注册</span>
    </h2>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      @submit.prevent="handleRegister"
      class="auth-form"
    >
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="用户名" />
      </el-form-item>

      <el-form-item prop="nickname">
        <el-input v-model="form.nickname" placeholder="姓名/昵称" />
      </el-form-item>

      <el-form-item prop="email">
        <el-input v-model="form.email" placeholder="邮箱（必填，用于身份验证）" />
      </el-form-item>

      <el-form-item prop="phone">
        <el-input v-model="form.phone" placeholder="手机号（选填）" />
      </el-form-item>

      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码（至少 8 位）" show-password />
      </el-form-item>

      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password />
      </el-form-item>

      <el-form-item prop="role">
        <el-select v-model="form.role" placeholder="选择角色" class="full-width">
          <el-option
            v-for="opt in ROLE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          >
            <span>{{ opt.label }}</span>
            <span class="option-desc">{{ opt.desc }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 图形验证码 -->
      <el-form-item prop="captchaCode">
        <div class="captcha-row">
          <el-input v-model="form.captchaCode" placeholder="图形验证码" maxlength="5" class="captcha-input" />
          <div class="captcha-image" @click="loadCaptcha" v-if="captchaImage">
            <img :src="captchaImage" alt="验证码" />
            <span class="captcha-refresh">刷新</span>
          </div>
        </div>
      </el-form-item>

      <!-- 邮箱验证码 -->
      <el-form-item prop="emailCode">
        <div class="email-code-row">
          <el-input v-model="form.emailCode" placeholder="邮箱验证码" maxlength="6" class="email-input" />
          <el-button
            class="send-code-btn"
            size="small"
            :loading="emailSending"
            :disabled="emailCountdown > 0 || !form.captchaCode || !form.email"
            @click="handleSendEmailCode"
          >
            {{ emailCountdown > 0 ? `${emailCountdown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" class="submit-btn">
          <span v-if="!loading">注 册</span>
          <span v-else>注册中...</span>
        </el-button>
      </el-form-item>
    </el-form>

    <div class="form-footer">
      <span>已有账号？</span>
      <router-link to="/login">去登录</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { register, getCaptcha, sendEmailCode as apiSendEmailCode } from '@/services/modules/user'
import { ROLE_OPTIONS, getRoleByKey } from '@/utils/role'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const emailSending = ref(false)
const emailCountdown = ref(0)
const captchaId = ref('')
const captchaImage = ref('')
let countdownTimer = null

const form = reactive({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  role: '',
  captchaCode: '',
  emailCode: '',
})

const validatePass2 = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
  ],
  nickname: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码长度不少于 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePass2, trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
  emailCode: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }],
}

async function loadCaptcha() {
  try {
    const data = await getCaptcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.imageBase64
    form.captchaCode = ''
  } catch {
    // 忽略
  }
}

async function handleSendEmailCode() {
  if (!form.email || !form.captchaCode || !captchaId.value) {
    ElMessage.warning('请先填写邮箱和图形验证码')
    return
  }
  emailSending.value = true
  try {
    const res = await apiSendEmailCode({
      purpose: 'REGISTER',
      email: form.email,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode,
    })
    ElMessage.success(res.message || '验证码已发送')
    if (res.devCode) {
      ElMessage.info(`开发模式验证码：${res.devCode}`)
    }
    startCountdown(res.expireSeconds || 300)
  } catch {
    // 错误由拦截器处理
  } finally {
    emailSending.value = false
  }
}

function startCountdown(seconds) {
  emailCountdown.value = Math.min(seconds, 120)
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    emailCountdown.value--
    if (emailCountdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await register({
      username: form.username,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone || undefined,
      password: form.password,
      role: form.role,
      emailCode: form.emailCode,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode,
    })
    if (res.code === 200) {
      userStore.setUser(res.data.token, res.data.userInfo)
      ElMessage.success('注册成功！')
      const role = getRoleByKey(res.data.userInfo.role)
      router.push(role ? role.home : '/')
    }
  } catch {
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(loadCaptcha)
onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<style lang="scss" scoped>
.register-page {
  animation: page-enter 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes page-enter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.form-title {
  text-align: center;
  margin-bottom: 28px;

  .title-en {
    display: block;
    font-family: 'Inter', system-ui, sans-serif;
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.2em;
    color: rgba(255, 255, 255, 0.3);
    margin-bottom: 8px;
    text-transform: uppercase;
  }

  .title-cn {
    display: block;
    font-family: 'Instrument Serif', Georgia, serif;
    font-size: 26px;
    font-weight: 400;
    color: rgba(255, 255, 255, 0.9);
    letter-spacing: -0.01em;
  }
}

.auth-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-input__wrapper) {
    transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);
    height: 48px;
  }

  :deep(.el-input__inner) {
    font-size: 14px;
  }

  :deep(.el-select) {
    width: 100%;
  }
}

.captcha-row {
  display: flex;
  gap: 10px;

  .captcha-input { flex: 1; }
}

.captcha-image {
  flex-shrink: 0;
  width: 130px;
  height: 48px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: #fff;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  .captcha-refresh {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    color: #fff;
    background: rgba(0, 0, 0, 0.45);
    opacity: 0;
    transition: opacity 0.2s;
    letter-spacing: 0.05em;
  }

  &:hover .captcha-refresh { opacity: 1; }
}

.email-code-row {
  display: flex;
  gap: 8px;

  .email-input { flex: 1; }

  .send-code-btn {
    flex-shrink: 0;
    min-width: 105px;
    height: 48px;
    border-radius: 10px;
    font-size: 12px;
  }
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  border-radius: 12px;
  letter-spacing: 0.08em;
  margin-top: 4px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.23, 1, 0.32, 1);

  &:hover { transform: translateY(-1px); }
  &:active { transform: scale(0.98); }
}

.option-desc {
  float: right;
  color: rgba(255, 255, 255, 0.3);
  font-size: 12px;
}

.form-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;

  span { color: rgba(255, 255, 255, 0.4); }

  a {
    color: #3b82f6;
    font-weight: 500;
    text-decoration: none;
    transition: all 0.3s;

    &:hover { color: #6366f1; }
  }
}
</style>
