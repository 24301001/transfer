<template>
  <Transition name="dialog-slide">
    <div v-if="visible" class="chat-dialog-overlay" @click.self="$emit('close')">
      <div class="chat-dialog" ref="dialogRef">
        <!-- 头部 -->
        <div class="chat-header">
          <div class="header-left">
            <el-icon :size="18"><ChatLineSquare /></el-icon>
            <span>AI 事故助手</span>
          </div>
          <button class="header-close" @click="$emit('close')">
            <el-icon :size="18"><Close /></el-icon>
          </button>
        </div>

        <!-- 消息列表 -->
        <div class="chat-body" ref="bodyRef">
          <div v-if="messages.length === 0" class="welcome">
            <div class="welcome-icon">
              <el-icon :size="40"><ChatLineSquare /></el-icon>
            </div>
            <p class="welcome-title">有什么可以帮你的？</p>
            <p class="welcome-desc">
              你可以问我关于事故上报、现场安全、照片视频上传、报警求助等问题。
            </p>
            <div class="suggestions">
              <button
                v-for="(q, i) in suggestions"
                :key="i"
                class="suggestion-btn"
                @click="sendMessage(q)"
              >
                {{ q }}
              </button>
            </div>
          </div>

          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="message-row"
            :class="msg.role === 'user' ? 'user-row' : 'ai-row'"
          >
            <div v-if="msg.role === 'ai'" class="avatar ai-avatar">
              <el-icon :size="16"><ChatLineSquare /></el-icon>
            </div>
            <div class="message-bubble" :class="msg.role">
              <!-- 普通文本 -->
              <p v-if="!msg.casualtyDetected && !msg.isTyping">{{ msg.content }}</p>

              <!-- 打字中动画 -->
              <p v-if="msg.isTyping" class="typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </p>

              <!-- 伤亡警示 -->
              <div v-if="msg.casualtyDetected" class="casualty-warning">
                <el-icon :size="16"><WarningFilled /></el-icon>
                <span>检测到人员受伤风险</span>
              </div>

              <!-- 120 报警按钮 -->
              <div v-if="msg.call120Required" class="call120-row">
                <el-icon :size="16"><PhoneFilled /></el-icon>
                <span>请立即拨打</span>
                <a :href="`tel:${msg.emergencyPhone || '120'}`" class="call120-btn">
                  {{ msg.emergencyPhone || '120' }}
                </a>
              </div>

              <!-- 越界提示 -->
              <div v-if="msg.outOfScope" class="scope-hint">
                <el-icon :size="14"><InfoFilled /></el-icon>
                <span>我只能回答事故上报相关问题哦</span>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="avatar user-avatar">
              <el-icon :size="16"><UserFilled /></el-icon>
            </div>
          </div>

          <!-- 底部锚点 -->
          <div ref="anchorRef"></div>
        </div>

        <!-- 输入区 -->
        <div class="chat-footer">
          <div class="input-wrapper">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="1"
              :autosize="{ minRows: 1, maxRows: 3 }"
              placeholder="输入你的问题..."
              :disabled="sending"
              @keydown.enter.prevent="handleSend"
            />
          </div>
          <button class="send-btn" :disabled="!inputText.trim() || sending" @click="handleSend">
            <el-icon :size="20" v-if="!sending"><Promotion /></el-icon>
            <el-icon :size="20" v-else class="is-loading"><Loading /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { aiChat } from '@/services/modules/reportAi'
import {
  ChatLineSquare,
  Close,
  UserFilled,
  WarningFilled,
  PhoneFilled,
  InfoFilled,
  Promotion,
  Loading,
} from '@element-plus/icons-vue'

const props = defineProps({
  visible: Boolean,
  incidentId: { type: Number, default: null },
  locationName: { type: String, default: '' },
  description: { type: String, default: '' },
})

const emit = defineEmits(['close'])

const bodyRef = ref(null)
const anchorRef = ref(null)
const dialogRef = ref(null)

// ====== 消息状态 ======
const inputText = ref('')
const sending = ref(false)
const messages = ref([])

// ====== 快捷建议问题 ======
const suggestions = [
  '现场应该怎么处理？',
  '照片要怎么拍？',
  '有人受伤了怎么办？',
  '怎么定位事故地点？',
]

// ====== 发送消息 ======
async function sendMessage(text) {
  if (!text.trim() || sending.value) return

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: text.trim(),
  })

  // 添加 AI 占位（打字中）
  const aiIndex = messages.value.length
  messages.value.push({
    role: 'ai',
    content: '',
    isTyping: true,
  })
  scrollToBottom()
  inputText.value = ''
  sending.value = true

  try {
    const res = await aiChat({
      incidentId: props.incidentId,
      question: text.trim(),
      locationName: props.locationName,
      description: props.description,
    })

    // 替换占位为实际回复
    messages.value[aiIndex] = {
      role: 'ai',
      content: res.reply || '',
      casualtyDetected: res.casualtyDetected || false,
      call120Required: res.call120Required || false,
      emergencyPhone: res.emergencyPhone || null,
      outOfScope: res.outOfScope || false,
      isTyping: false,
    }
  } catch {
    // 出错时替换为错误提示
    messages.value[aiIndex] = {
      role: 'ai',
      content: '抱歉，我暂时无法回复，请稍后再试。',
      isTyping: false,
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function handleSend() {
  sendMessage(inputText.value)
}

function scrollToBottom() {
  nextTick(() => {
    anchorRef.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

// ====== 打开时自动聚焦 ======
watch(() => props.visible, (val) => {
  if (val) {
    nextTick(() => {
      dialogRef.value?.querySelector('textarea')?.focus()
    })
  }
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.chat-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 998;
  background: rgba(0, 0, 0, 0.15);
}

.chat-dialog {
  position: fixed;
  right: 28px;
  bottom: 96px;
  width: 380px;
  height: 560px;
  max-height: calc(100vh - 140px);
  background: $bg-white;
  border-radius: $radius-lg;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 999;
}

// ====== 头部 ======
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, $primary, $primary-dark);
  color: #fff;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
  }

  .header-close {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: none;
    background: rgba(255, 255, 255, 0.15);
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.2s;

    &:hover {
      background: rgba(255, 255, 255, 0.3);
    }
  }
}

// ====== 消息列表 ======
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: $border-light;
}

.welcome {
  text-align: center;
  padding: 32px 16px;

  .welcome-icon {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba($primary, 0.1), rgba($primary-light, 0.1));
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 16px;
    color: $primary;
  }

  .welcome-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 8px;
  }

  .welcome-desc {
    font-size: 13px;
    color: $text-light;
    line-height: 1.5;
    margin-bottom: 20px;
  }

  .suggestions {
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: center;
  }

  .suggestion-btn {
    width: 240px;
    padding: 10px 16px;
    border: 1px solid $border;
    border-radius: 20px;
    background: $bg-white;
    color: $text-secondary;
    font-size: 13px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: $accent;
      color: $accent;
      background: rgba($accent, 0.06);
    }
  }
}

.message-row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: flex-start;

  &.user-row {
    justify-content: flex-end;
  }
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-avatar {
  background: linear-gradient(135deg, $primary, $primary-dark);
  color: #fff;
}

.user-avatar {
  background: $border-light;
  color: $text-secondary;
}

.message-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;

  p {
    margin: 0;
  }

  &.user {
    background: $primary;
    color: #fff;
    border-bottom-right-radius: 4px;
  }

  &.ai {
    background: $bg-white;
    color: $text-primary;
    border: 1px solid $border-light;
    border-bottom-left-radius: 4px;
  }
}

// ====== 打字动画 ======
.typing {
  display: flex;
  gap: 4px;
  padding: 4px 0;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $text-light;
    animation: typingDot 1.4s infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

@keyframes typingDot {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}

// ====== 警示样式 ======
.casualty-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  color: $danger;
  font-size: 12px;
  font-weight: 600;
}

.call120-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 13px;
  color: $danger;

  .call120-btn {
    display: inline-block;
    padding: 4px 14px;
    background: $danger;
    color: #fff;
    border-radius: 14px;
    text-decoration: none;
    font-weight: 700;
    font-size: 15px;
    transition: background 0.2s;

    &:hover {
      background: #dc2626;
    }
  }
}

.scope-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: $text-light;
}

// ====== 底部输入 ======
.chat-footer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid $border-light;
  background: $bg-white;
}

.input-wrapper {
  flex: 1;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: $primary;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;

  &:hover:not(:disabled) {
    background: $primary-dark;
    transform: scale(1.05);
  }

  &:disabled {
    background: #d1d5db;
    cursor: not-allowed;
  }
}

// ====== 过渡动画 ======
.dialog-slide-enter-active,
.dialog-slide-leave-active {
  transition: all 0.3s ease;

  .chat-dialog {
    transition: all 0.3s ease;
  }
}

.dialog-slide-enter-from,
.dialog-slide-leave-to {
  opacity: 0;

  .chat-dialog {
    transform: translateY(20px);
    opacity: 0;
  }
}
</style>
