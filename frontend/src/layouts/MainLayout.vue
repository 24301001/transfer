<template>
  <div class="main-layout">
    <!-- 顶部导航 -->
    <header class="top-header">
      <div class="header-left">
        <div class="logo">
          <el-icon :size="28"><WarningFilled /></el-icon>
          <span class="logo-text">交通事故风险预估平台</span>
        </div>
      </div>
      <div class="header-center">
        <el-menu
          :default-active="activeMenu"
          mode="horizontal"
          :ellipsis="false"
          class="nav-menu"
          router
        >
          <template v-for="item in menuItems" :key="item.path">
            <el-menu-item :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="32" :icon="UserFilled" />
            <span class="username">{{ userStore.nickname }}</span>
            <el-tag size="small" :type="tagType" effect="dark" class="role-tag">
              {{ userStore.roleLabel }}
            </el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 内容区域 -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- 全局悬浮球 AI 助手（仅现场人员可见） -->
    <FloatingBall v-if="showFloatingBall" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ROLES } from '@/utils/role'
import { ElMessageBox } from 'element-plus'
import {
  WarningFilled,
  UserFilled,
  ArrowDown,
  SwitchButton,
  User,
  EditPen,
  Monitor,
  Van,
  Tickets,
  List,
} from '@element-plus/icons-vue'
import FloatingBall from '@/components/FloatingBall.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 菜单配置（按角色）
const menuConfig = {
  [ROLES.POLICE.key]: [
    { path: '/police/report', label: '事故上报', icon: EditPen },
  ],
  [ROLES.COMMAND.key]: [
    { path: '/command/dashboard', label: '指挥大屏', icon: Monitor },
    { path: '/command/dispatch', label: '调度处理', icon: Van },
  ],
  [ROLES.RESCUE.key]: [
    { path: '/rescue/tasks', label: '清障任务', icon: Tickets },
  ],
  [ROLES.ADMIN.key]: [
    { path: '/admin/users', label: '用户管理', icon: User },
    { path: '/admin/logs', label: '操作日志', icon: List },
  ],
}

const menuItems = computed(() => menuConfig[userStore.role] || [])

const activeMenu = computed(() => route.path)

// 仅现场交警角色显示悬浮球
const showFloatingBall = computed(() => userStore.role === ROLES.POLICE.key)

const tagType = computed(() => {
  const map = {
    [ROLES.POLICE.key]: 'warning',
    [ROLES.COMMAND.key]: 'danger',
    [ROLES.RESCUE.key]: 'success',
    [ROLES.ADMIN.key]: 'info',
  }
  return map[userStore.role] || 'info'
})

function handleCommand(command) {
  if (command === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
      .then(() => {
        userStore.logout()
        router.push('/login')
      })
      .catch(() => {})
  }
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.main-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.top-header {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  height: $header-height;
  padding: 0 24px;
  background: linear-gradient(135deg, $primary-darker, $primary-dark);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.header-left {
  flex-shrink: 0;
  margin-right: 32px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;

  .logo-text {
    font-size: 16px;
    font-weight: 600;
    white-space: nowrap;
  }
}

.header-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.nav-menu {
  background: transparent !important;
  border-bottom: none !important;

  .el-menu-item {
    color: rgba(255, 255, 255, 0.75) !important;
    border-bottom: 2px solid transparent !important;
    margin: 0 2px;
    height: $header-height;
    line-height: $header-height;

    &:hover {
      background: rgba(255, 255, 255, 0.1) !important;
      color: #fff !important;
    }

    &.is-active {
      color: #fff !important;
      border-bottom-color: #fff !important;
      background: rgba(255, 255, 255, 0.08) !important;
    }

    .el-icon {
      margin-right: 6px;
    }
  }
}

.header-right {
  flex-shrink: 0;
  margin-left: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background 0.2s;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
  }

  .username {
    font-size: 14px;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .role-tag {
    font-size: 11px;
  }
}

.main-content {
  flex: 1;
  padding: 24px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}
</style>
