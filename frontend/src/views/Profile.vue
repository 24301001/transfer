<template>
  <div class="admin-page">
    <div class="page-header">
      <h2>个人中心</h2>
      <p>查看和修改个人资料</p>
    </div>

    <!-- 个人信息卡片 -->
    <div class="page-card">
      <div class="profile-header">
        <div class="avatar-area">
          <div class="avatar-circle">
            <span class="avatar-text">{{ displayInitial }}</span>
          </div>
          <div class="avatar-role">
            <el-tag :type="roleTagType" size="small" effect="dark">{{ userStore.roleLabel }}</el-tag>
          </div>
        </div>
        <div class="profile-summary">
          <h3>{{ profile?.nickname || '-' }}</h3>
          <p class="profile-username">@{{ profile?.username }}</p>
          <p class="profile-status" v-if="profile">
            <span class="status-dot" :class="profile.status === '启用' ? 'enabled' : 'disabled'"></span>
            {{ profile.status }}
          </p>
        </div>
      </div>

      <el-divider />

      <el-descriptions :column="2" border size="small" class="profile-descriptions">
        <el-descriptions-item label="姓名">{{ profile?.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ profile?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profile?.email || '未绑定' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile?.phone || '未填写' }}</el-descriptions-item>
      </el-descriptions>

      <div class="profile-actions">
        <el-button type="primary" @click="editNameVisible = true">
          <el-icon><EditPen /></el-icon>修改姓名
        </el-button>
        <el-button @click="changePasswordVisible = true">
          <el-icon><Lock /></el-icon>修改密码
        </el-button>
      </div>
    </div>

    <!-- 修改姓名对话框 -->
    <el-dialog v-model="editNameVisible" title="修改姓名" width="420px" destroy-on-close>
      <el-form ref="nameFormRef" :model="nameForm" :rules="nameRules" label-width="80px">
        <el-form-item label="新姓名" prop="fullName">
          <el-input v-model="nameForm.fullName" placeholder="请输入新姓名" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editNameVisible = false">取消</el-button>
        <el-button type="primary" :loading="nameUpdating" @click="handleUpdateName">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="changePasswordVisible" title="修改密码" width="460px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入原密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="至少 8 位" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmNew">
          <el-input v-model="passwordForm.confirmNew" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>

        <el-divider />

        <!-- 图形验证码 -->
        <el-form-item label="图形验证码" prop="captchaCode">
          <div class="pwd-captcha-row">
            <el-input v-model="passwordForm.captchaCode" placeholder="验证码" maxlength="5" class="pwd-captcha-input" />
            <div class="captcha-image" @click="loadPwdCaptcha" v-if="pwdCaptchaImage">
              <img :src="pwdCaptchaImage" alt="验证码" />
              <span class="captcha-refresh">刷新</span>
            </div>
          </div>
        </el-form-item>

        <!-- 邮箱验证码 -->
        <el-form-item label="邮箱验证码" prop="emailCode">
          <div class="pwd-email-row">
            <el-input v-model="passwordForm.emailCode" placeholder="验证码" maxlength="6" class="pwd-email-input" />
            <el-button
              size="small"
              :loading="pwdEmailSending"
              :disabled="pwdEmailCountdown > 0 || !passwordForm.captchaCode"
              @click="handleSendPwdEmailCode"
              class="pwd-send-btn"
            >
              {{ pwdEmailCountdown > 0 ? `${pwdEmailCountdown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changePasswordVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdUpdating" @click="handleChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/stores/user'
import { getProfile, updateProfileName, sendProfilePasswordEmailCode, changeProfilePassword, getCaptcha } from '@/services/modules/user'
import { ElMessage } from 'element-plus'
import { EditPen, Lock } from '@element-plus/icons-vue'

const userStore = useUserStore()
const profile = ref(null)

// ====== 头像 ======
const displayInitial = computed(() => {
  return profile.value?.nickname?.[0] || profile.value?.username?.[0] || '?'
})

const roleTagType = computed(() => {
  const map = { POLICE: 'warning', COMMAND: 'danger', RESCUE: 'success', ADMIN: 'info' }
  return map[userStore.role] || 'info'
})

// ====== 加载个人资料 ======
async function loadProfile() {
  try {
    const res = await getProfile()
    if (res.code === 200) {
      profile.value = res.data
    }
  } catch {
    // 忽略
  }
}

onMounted(loadProfile)

// ====== 修改姓名 ======
const editNameVisible = ref(false)
const nameUpdating = ref(false)
const nameFormRef = ref(null)
const nameForm = ref({ fullName: '' })
const nameRules = {
  fullName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
}

async function handleUpdateName() {
  const valid = await nameFormRef.value?.validate().catch(() => false)
  if (!valid) return

  nameUpdating.value = true
  try {
    const res = await updateProfileName({ fullName: nameForm.value.fullName })
    if (res.code === 200) {
      profile.value = res.data
      // 同步更新 store 中的 nickname
      userStore.userInfo = { ...userStore.userInfo, nickname: res.data.nickname }
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
      ElMessage.success('姓名修改成功')
      editNameVisible.value = false
    }
  } catch {
    // 错误由拦截器处理
  } finally {
    nameUpdating.value = false
  }
}

// ====== 修改密码 ======
const changePasswordVisible = ref(false)
const pwdUpdating = ref(false)
const pwdEmailSending = ref(false)
const pwdEmailCountdown = ref(0)
const pwdCaptchaId = ref('')
const pwdCaptchaImage = ref('')
let pwdCountdownTimer = null

const passwordFormRef = ref(null)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmNew: '',
  captchaCode: '',
  emailCode: '',
})

const validateConfirmNew = (rule, value, callback) => {
  if (value !== passwordForm.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不少于 8 位', trigger: 'blur' },
  ],
  confirmNew: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmNew, trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }],
  emailCode: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }],
}

async function loadPwdCaptcha() {
  try {
    const data = await getCaptcha()
    pwdCaptchaId.value = data.captchaId
    pwdCaptchaImage.value = data.imageBase64
    passwordForm.value.captchaCode = ''
  } catch {
    // 忽略
  }
}

async function handleSendPwdEmailCode() {
  if (!passwordForm.value.captchaCode || !pwdCaptchaId.value) {
    ElMessage.warning('请先填写图形验证码')
    return
  }
  pwdEmailSending.value = true
  try {
    const res = await sendProfilePasswordEmailCode({
      captchaId: pwdCaptchaId.value,
      captchaCode: passwordForm.value.captchaCode,
    })
    ElMessage.success(res.message || '验证码已发送')
    if (res.devCode) {
      ElMessage.info(`开发模式验证码：${res.devCode}`)
    }
    startPwdCountdown(res.expireSeconds || 300)
  } catch {
    // 错误由拦截器处理
  } finally {
    pwdEmailSending.value = false
  }
}

function startPwdCountdown(seconds) {
  pwdEmailCountdown.value = Math.min(seconds, 120)
  clearInterval(pwdCountdownTimer)
  pwdCountdownTimer = setInterval(() => {
    pwdEmailCountdown.value--
    if (pwdEmailCountdown.value <= 0) {
      clearInterval(pwdCountdownTimer)
      pwdCountdownTimer = null
    }
  }, 1000)
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  pwdUpdating.value = true
  try {
    const res = await changeProfilePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
      emailCode: passwordForm.value.emailCode,
      captchaId: pwdCaptchaId.value,
      captchaCode: passwordForm.value.captchaCode,
    })
    if (res.code === 200) {
      ElMessage.success(res.data?.message || '密码修改成功')
      changePasswordVisible.value = false
      // 清空表单
      passwordForm.value = { oldPassword: '', newPassword: '', confirmNew: '', captchaCode: '', emailCode: '' }
      pwdCaptchaId.value = ''
      pwdCaptchaImage.value = ''
    }
  } catch {
    // 错误由拦截器处理
  } finally {
    pwdUpdating.value = false
  }
}

// 打开密码对话框时加载验证码
const unwatchPwd = computed(() => changePasswordVisible.value)

watch(unwatchPwd, (val) => {
  if (val) {
    loadPwdCaptcha()
  } else {
    clearInterval(pwdCountdownTimer)
    pwdCountdownTimer = null
  }
})

onBeforeUnmount(() => clearInterval(pwdCountdownTimer))
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 0;

  .avatar-area {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }

  .avatar-circle {
    width: 72px;
    height: 72px;
    border-radius: 50%;
    background: linear-gradient(135deg, $accent, $accent-secondary);
    display: flex;
    align-items: center;
    justify-content: center;

    .avatar-text {
      font-size: 28px;
      font-weight: 700;
      color: #fff;
      font-family: $font-sans;
    }
  }

  .profile-summary {
    flex: 1;

    h3 {
      font-family: $font-sans;
      font-size: 20px;
      font-weight: 600;
      color: $text-primary;
      margin: 0 0 4px;
    }

    .profile-username {
      font-size: 13px;
      color: $text-light;
      margin: 0 0 6px;
      font-family: $font-mono;
    }

    .profile-status {
      font-size: 12px;
      color: $text-secondary;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 6px;

      .status-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        display: inline-block;

        &.enabled { background: $success; }
        &.disabled { background: $danger; }
      }
    }
  }
}

.profile-descriptions {
  margin-bottom: 16px;
}

.profile-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

// 密码弹窗中的验证码
.pwd-captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;

  .pwd-captcha-input { flex: 1; }
}

.pwd-email-row {
  display: flex;
  gap: 8px;
  width: 100%;

  .pwd-email-input { flex: 1; }
  .pwd-send-btn { flex-shrink: 0; min-width: 105px; }
}

.captcha-image {
  flex-shrink: 0;
  width: 120px;
  height: 36px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  position: relative;
  border: 1px solid $border-light;
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
  }

  &:hover .captcha-refresh { opacity: 1; }
}
</style>
