<template>
  <el-tag :type="tagType" :size="size" effect="dark" class="risk-badge">
    <el-icon v-if="showIcon" :size="iconSize" class="risk-icon">
      <WarningFilled v-if="level === '严重'" />
      <CircleCloseFilled v-else-if="level === '高'" />
      <WarningFilled v-else-if="level === '中'" />
      <CircleCheckFilled v-else />
    </el-icon>
    {{ level || '-' }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled, CircleCloseFilled, CircleCheckFilled } from '@element-plus/icons-vue'

const props = defineProps({
  level: { type: String, default: '-' },
  size: { type: String, default: 'default' },
  showIcon: { type: Boolean, default: true },
})

const tagType = computed(() => {
  const map = { 低: 'success', 中: 'warning', 高: 'warning', 严重: 'danger' }
  return map[props.level] || 'info'
})

const iconSize = computed(() => {
  const map = { small: 12, default: 14, large: 16 }
  return map[props.size] || 14
})
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.risk-badge {
  font-weight: 600;
  border: none;
  letter-spacing: 0.02em;
  padding: 0 10px;

  .risk-icon {
    margin-right: 3px;
  }

  // 叠加彩色标签底色微调
  &.el-tag--danger { background: linear-gradient(135deg, #ef4444, #dc2626); }
  &.el-tag--warning { background: linear-gradient(135deg, #f59e0b, #d97706); }
  &.el-tag--success { background: linear-gradient(135deg, #10b981, #059669); }
}
</style>
