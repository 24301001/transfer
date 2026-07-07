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
.risk-badge {
  font-weight: 600;
  border: none;

  .risk-icon {
    margin-right: 3px;
  }
}
</style>
