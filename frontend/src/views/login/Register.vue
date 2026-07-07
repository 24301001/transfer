<template>
  <div class="register-page">
    <h2 class="form-title">用户注册</h2>
    <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
      <el-form-item prop="username">
        <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
      </el-form-item>
      <el-form-item prop="nickname">
        <el-input v-model="form.nickname" placeholder="姓名/昵称" :prefix-icon="Avatar" />
      </el-form-item>
      <el-form-item prop="password">
        <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" show-password :prefix-icon="Lock" />
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
            <span style="float: right; color: #9ca3af; font-size: 12px;">{{ opt.desc }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" class="submit-btn">
          {{ loading ? '注册中...' : '注 册' }}
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
import { User, Avatar, Lock } from '@element-plus/icons-vue'

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
  .form-title {
    text-align: center;
    font-size: 24px;
    font-weight: 600;
    color: #1f2937;
    margin-bottom: 28px;
  }

  .full-width {
    width: 100%;
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
}
</style>
