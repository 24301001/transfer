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

      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码" show-password />
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
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { register } from '@/services/modules/user'
import { ROLE_OPTIONS, getRoleByKey } from '@/utils/role'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  role: '',
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
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validatePass2, trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
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
      password: form.password,
      role: form.role,
    })
    if (res.code === 200) {
      userStore.setUser(res.data.token, res.data.userInfo)
      ElMessage.success('注册成功！')
      const role = getRoleByKey(res.data.userInfo.role)
      router.push(role ? role.home : '/')
    }
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.register-page {
  animation: page-enter 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes page-enter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

// ===== Title =====
.form-title {
  text-align: center;
  margin-bottom: 32px;

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

// ===== Form =====
.auth-form {
  :deep(.el-form-item) {
    margin-bottom: 18px;
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

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(135deg, rgba(255,255,255,0.1), transparent);
    opacity: 0;
    transition: opacity 0.3s;
  }

  &:hover::before {
    opacity: 1;
  }

  &:active {
    transform: scale(0.98);
  }
}

// ===== Role option =====
.option-desc {
  float: right;
  color: rgba(255, 255, 255, 0.3);
  font-size: 12px;
}

// ===== Footer =====
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
    position: relative;

    &::after {
      content: '';
      position: absolute;
      bottom: -1px;
      left: 0;
      width: 0;
      height: 1px;
      background: #3b82f6;
      transition: width 0.3s;
    }

    &:hover {
      color: #6366f1;
      &::after { width: 100%; }
    }
  }
}
</style>
