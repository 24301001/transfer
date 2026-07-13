<template>
  <div class="floating-ball-wrapper">
    <!-- 快捷菜单（展开时显示） -->
    <Transition name="menu-slide">
      <div v-if="expanded" class="quick-menu">
        <button class="menu-item advice-item" title="安全提示" @click="handleAdvice">
          <el-icon :size="20"><WarningFilled /></el-icon>
          <span>安全提示</span>
        </button>
        <button class="menu-item top-item" title="回到顶部" @click="scrollToTop">
          <el-icon :size="20"><ArrowUp /></el-icon>
          <span>顶部</span>
        </button>
      </div>
    </Transition>

    <!-- 主悬浮球 -->
    <button
      class="floating-ball"
      :class="{ expanded }"
      @click="handleMainClick"
      @mouseenter="onEnter"
      @mouseleave="onLeave"
      title="AI 助手"
    >
      <el-icon :size="24">
        <ChatLineSquare v-if="!expanded" />
        <Close v-else />
      </el-icon>
    </button>

    <!-- AI 聊天对话框 -->
    <AiChatDialog
      :visible="chatVisible"
      :incident-id="chatContext.incidentId"
      :location-name="chatContext.locationName"
      :description="chatContext.description"
      @close="chatVisible = false"
    />

    <!-- 安全提示弹窗 -->
    <el-dialog
      v-model="adviceVisible"
      title="即时安全提示"
      width="520px"
      top="20vh"
      destroy-on-close
    >
      <div v-if="adviceLoading" class="advice-loading">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="adviceData" class="advice-content">
        <el-alert
          v-if="adviceData.casualtyDetected"
          title="检测到人员受伤风险"
          type="error"
          :closable="false"
          show-icon
          class="advice-casualty"
        />
        <div class="advice-section">
          <h4>立即行动</h4>
          <p class="advice-text">{{ adviceData.immediateAdvice }}</p>
        </div>
        <div v-if="adviceData.actionItems?.length" class="advice-section">
          <h4>处置步骤</h4>
          <ul class="advice-list">
            <li v-for="(item, i) in adviceData.actionItems" :key="i">{{ item }}</li>
          </ul>
        </div>
        <div v-if="adviceData.call120Required" class="advice-call120">
          <el-icon :size="20"><PhoneFilled /></el-icon>
          <span>请立即拨打 {{ adviceData.emergencyPhone || '120' }}</span>
        </div>
      </div>
      <div v-else class="advice-empty">
        <p>请先提交事故，或从事故详情页获取安全提示。</p>
      </div>
      <template #footer>
        <el-button @click="adviceVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useAiChatContext } from '@/composables/useAiChatContext'
import { getInstantAdvice } from '@/services/modules/reportAi'
import { ElMessage } from 'element-plus'
import {
  ChatLineSquare,
  Close,
  WarningFilled,
  ArrowUp,
  PhoneFilled,
} from '@element-plus/icons-vue'
import AiChatDialog from './AiChatDialog.vue'

const userStore = useUserStore()
const { chatContext } = useAiChatContext()

// ====== 悬浮球状态 ======
const expanded = ref(false)
const chatVisible = ref(false)

// ====== 安全提示状态 ======
const adviceVisible = ref(false)
const adviceLoading = ref(false)
const adviceData = ref(null)

// 鼠标悬停展开菜单
let hoverTimer = null
const MENU_DELAY = 300

function onEnter() {
  clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => {
    expanded.value = true
  }, MENU_DELAY)
}

function onLeave() {
  clearTimeout(hoverTimer)
  // 延迟收起，避免移入菜单项时收起
  hoverTimer = setTimeout(() => {
    expanded.value = false
  }, 200)
}

// ====== 主按钮点击 ======
function handleMainClick() {
  clearTimeout(hoverTimer)
  if (expanded.value) {
    // 已展开 → 收起
    expanded.value = false
  } else {
    // 未展开 → 打开聊天
    chatVisible.value = !chatVisible.value
  }
}

// ====== 菜单操作 ======
async function handleAdvice() {
  expanded.value = false
  const incidentId = chatContext.incidentId
  if (!incidentId) {
    ElMessage.info('请先提交事故，即可获取安全提示')
    return
  }
  adviceVisible.value = true
  adviceLoading.value = true
  adviceData.value = null
  try {
    const res = await getInstantAdvice(incidentId)
    adviceData.value = res
  } catch {
    adviceData.value = null
    ElMessage.error('获取安全提示失败')
  } finally {
    adviceLoading.value = false
  }
}

function scrollToTop() {
  expanded.value = false
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 聊天关闭时收起菜单
watch(chatVisible, (val) => {
  if (!val) {
    expanded.value = false
  }
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.floating-ball-wrapper {
  position: fixed;
  right: 28px;
  bottom: 32px;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

// ====== 快捷菜单 ======
.quick-menu {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 20px;
  background: $bg-white;
  color: $text-primary;
  font-size: 13px;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: all 0.2s ease-out;
  white-space: nowrap;

  &:hover {
    background: $gradient-accent;
    color: #fff;
    transform: translateX(-4px);
    box-shadow: $shadow-accent;
  }

  .el-icon { font-size: 16px; }

  span {
    font-size: 12px;
    font-weight: 500;
  }
}

.menu-slide-enter-active,
.menu-slide-leave-active {
  transition: all 0.25s ease;
}

.menu-slide-enter-from,
.menu-slide-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

// ====== 主悬浮球 ======
.floating-ball {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: $gradient-accent;
  color: #fff;
  cursor: pointer;
  box-shadow: $shadow-accent-lg;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
  position: relative;

  &:hover {
    transform: scale(1.08);
    box-shadow: $shadow-accent-lg, 0 0 0 3px rgba($accent, 0.15);
  }

  &:active {
    transform: scale(0.95);
  }

  &.expanded {
    background: $text-secondary;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);

    &:hover {
      background: $danger;
    }
  }
}

// ====== 安全提示弹窗 ======
.advice-loading {
  padding: 20px;
}

.advice-content {
  .advice-casualty {
    margin-bottom: 16px;
  }

  .advice-section {
    margin-bottom: 16px;

    h4 {
      font-family: $font-sans;
      font-size: 14px;
      font-weight: 600;
      color: $text-primary;
      margin-bottom: 8px;
    }
  }

  .advice-text {
    font-size: 14px;
    line-height: 1.6;
    color: $text-primary;
    padding: 14px;
    background: linear-gradient(135deg, rgba($accent, 0.05), rgba($accent-secondary, 0.02));
    border-radius: 10px;
    border-left: 3px solid $accent;
    border: 1px solid rgba($accent, 0.08);
    border-left-width: 3px;
  }

  .advice-list {
    padding-left: 20px;
    margin: 0;

    li {
      font-size: 13px;
      line-height: 1.8;
      color: $text-secondary;
    }
  }

  .advice-call120 {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    background: linear-gradient(135deg, rgba($danger, 0.06), rgba($danger, 0.02));
    border: 1px solid rgba($danger, 0.15);
    border-radius: 10px;
    color: $danger;
    font-weight: 600;
    font-size: 14px;
  }
}

.advice-empty {
  padding: 32px;
  text-align: center;
  color: $text-light;
  font-size: 14px;
}
</style>
