<template>
  <div class="main-layout">
    <!-- 顶部导航 -->
    <header class="top-header" id="mainHeader">
      <div class="header-inner">
        <div class="header-left">
          <div class="logo">
            <div class="logo-icon">
              <svg viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                <circle cx="14" cy="14" r="13" stroke="#3b82f6" stroke-width="1.8"/>
                <rect x="8.5" y="8.5" width="11" height="11" rx="2.5" stroke="#3b82f6" stroke-width="1.8"/>
                <path d="M14 8.5v11M8.5 14h11" stroke="#3b82f6" stroke-width="1.8" stroke-linecap="round"/>
              </svg>
            </div>
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
              <el-avatar :size="32" :icon="UserFilled" class="user-avatar" />
              <span class="username">{{ userStore.nickname }}</span>
              <el-tag size="small" :type="tagType" effect="light" class="role-tag">
                {{ userStore.roleLabel }}
              </el-tag>
              <el-icon class="dropdown-arrow"><ArrowDown /></el-icon>
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
      </div>
    </header>

    <!-- 内容区域 -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- 全局悬浮球 AI 助手 -->
    <FloatingBall v-if="showFloatingBall" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
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
  Search,
} from '@element-plus/icons-vue'
import FloatingBall from '@/components/FloatingBall.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menuConfig = {
  [ROLES.POLICE.key]: [
    { path: '/police/report', label: '事故上报', icon: EditPen },
  ],
  [ROLES.COMMAND.key]: [
    { path: '/command/dashboard', label: '指挥大屏', icon: Monitor },
    { path: '/command/dispatch', label: '调度处理', icon: Van },
    { path: '/command/accident-query', label: '事故查询', icon: Search },
  ],
  [ROLES.RESCUE.key]: [
    { path: '/rescue/tasks', label: '清障任务', icon: Tickets },
  ],
  [ROLES.ADMIN.key]: [
    { path: '/admin/users', label: '用户管理', icon: User },
    { path: '/admin/logs', label: '操作日志', icon: List },
    { path: '/admin/health', label: '系统健康', icon: Monitor },
  ],
}

const menuItems = computed(() => menuConfig[userStore.role] || [])

const activeMenu = computed(() => route.path)

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

// 滚动给 header 加阴影
function onScroll() {
  const header = document.getElementById('mainHeader')
  if (header) {
    header.classList.toggle('is-scrolled', window.scrollY > 8)
  }
}

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
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
  background: $bg;
}

.top-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(240, 245, 255, 0.88);
  backdrop-filter: blur(16px) saturate(1.2);
  -webkit-backdrop-filter: blur(16px) saturate(1.2);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.3s ease;

  &.is-scrolled {
    box-shadow: 0 1px 12px rgba(0, 0, 0, 0.05);
  }
}

.header-inner {
  max-width: 1440px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  height: $header-height;
  padding: 0 28px;
}

.header-left {
  flex-shrink: 0;
  margin-right: 32px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  color: $text-primary;

  .logo-icon {
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .logo-text {
    font-family: $font-sans;
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
    letter-spacing: -0.01em;
    color: $text-primary;
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
    color: $text-secondary !important;
    border-bottom: none !important;
    margin: 0 2px;
    height: $header-height;
    line-height: $header-height;
    border-radius: 8px;
    transition: all 0.2s ease-out;
    font-weight: 450;

    .el-icon {
      margin-right: 5px;
      font-size: 15px;
    }

    &:hover {
      background: rgba($accent, 0.06) !important;
      color: $accent !important;
    }

    &.is-active {
      color: $accent !important;
      background: rgba($accent, 0.08) !important;
      font-weight: 500;
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
  color: $text-primary;
  cursor: pointer;
  padding: 4px 10px 4px 6px;
  border-radius: 10px;
  transition: all 0.2s ease-out;

  &:hover {
    background: rgba($accent, 0.06);
  }

  .user-avatar {
    border: 2px solid rgba($accent, 0.2);
    background: rgba($accent, 0.08);
    color: $accent;
  }

  .username {
    font-size: 14px;
    font-weight: 500;
    max-width: 80px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .role-tag {
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.05em;
    border: none;
    border-radius: 4px;
  }

  .dropdown-arrow {
    color: $text-light;
    font-size: 14px;
    transition: transform 0.2s;
  }

  &:hover .dropdown-arrow {
    transform: rotate(180deg);
  }
}

.main-content {
  flex: 1;
  padding: 24px 32px;
  max-width: 1440px;
  width: 100%;
  margin: 0 auto;
}
</style>
