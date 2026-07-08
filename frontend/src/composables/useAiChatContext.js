import { reactive, onUnmounted } from 'vue'

/**
 * AI 聊天上下文 — 页面组件可注入当前事故/表单信息，
 * 供全局悬浮球读取，实现上下文感知的 AI 问答。
 */
const chatContext = reactive({
  incidentId: null,
  locationName: '',
  description: '',
  accidentType: '',
})

export function useAiChatContext() {
  function setContext(ctx) {
    Object.assign(chatContext, ctx)
  }

  function resetContext() {
    chatContext.incidentId = null
    chatContext.locationName = ''
    chatContext.description = ''
    chatContext.accidentType = ''
  }

  return { chatContext, setContext, resetContext }
}
