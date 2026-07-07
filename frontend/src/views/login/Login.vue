<template>
  <div class="login-page">
    <h2 class="form-title">用户登录</h2>
    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User">
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码" show-password>
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" class="submit-btn">
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form-item>
    </el-form>
    <div class="form-footer">
      <span>还没有账号？</span>
      <router-link to="/register">立即注册</router-link>
    </div>

    <!-- 快捷登录提示 -->
    <div class="quick-login">
      <el-divider><span style="color: #9ca3af; font-size: 12px;">快捷体验账号</span></el-divider>
      <div class="quick-list">
        <el-button size="small" @click="quickLogin('police1')">交警</el-button>
        <el-button size="small" @click="quickLogin('command1')">指挥中心</el-button>
        <el-button size="small" @click="quickLogin('rescue1')">清障救援</el-button>
        <el-button size="small" @click="quickLogin('admin1')">管理员</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login } from '@/services/modules/user'
import { getRoleByKey } from '@/utils/role'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login({ username: form.username, password: form.password })
    if (res.code === 200) {
      userStore.setUser(res.data.token, res.data.userInfo)
      ElMessage.success('登录成功！')
      const role = getRoleByKey(res.data.userInfo.role)
      router.push(role ? role.home : '/')
    }
  } catch (e) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function quickLogin(username) {
  form.username = username
  form.password = '123456'
  handleLogin()
}
</script>

<style lang="scss" scoped>
.login-page {
  .form-title {
    text-align: center;
    font-size: 24px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 28px;
  }

  .submit-btn {
    width: 100%;
    height: 44px;
    font-size: 16px;
    border-radius: 10px;
  }

  .form-footer {
    text-align: center;
    margin-top: 16px;
    color: #6b7280;
    font-size: 14px;

    a {
      color: #1a56db;
      font-weight: 500;
    }
  }

  .quick-login {
    margin-top: 16px;

    .quick-list {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      justify-content: center;
    }
  }
}
</style>
