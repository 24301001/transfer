<template>
  <div class="status-timeline">
    <el-timeline>
      <el-timeline-item
        v-for="(record, idx) in records"
        :key="idx"
        :timestamp="record.time"
        :type="getItemType(record.action)"
        :size="idx === 0 ? 'large' : 'default'"
        :hollow="idx !== 0"
      >
        <div class="timeline-content">
          <span class="action-tag">
            <el-tag :type="getItemType(record.action)" size="small" effect="plain">
              {{ record.action }}
            </el-tag>
          </span>
          <span class="operator" v-if="record.operator">{{ record.operator }}</span>
          <span class="detail" v-if="record.detail">{{ record.detail }}</span>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
defineProps({
  records: {
    type: Array,
    default: () => [],
  },
})

function getItemType(action) {
  if (action.includes('完成') || action.includes('到达')) return 'success'
  if (action.includes('上报') || action.includes('提交')) return 'primary'
  if (action.includes('出发')) return 'warning'
  if (action.includes('处理中')) return 'primary'
  return 'info'
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.status-timeline {
  .timeline-content {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    .action-tag {
      .el-tag {
        border-radius: 6px;
        font-weight: 500;
      }
    }

    .operator {
      font-size: 13px;
      color: $text-secondary;
      font-weight: 500;
    }

    .detail {
      font-size: 12px;
      color: $text-light;
      width: 100%;
      margin-top: 2px;
    }
  }

  :deep(.el-timeline-item__tail) {
    border-left-color: rgba($accent, 0.2);
  }

  :deep(.el-timeline-item__node--primary) {
    background-color: $accent;
    border-color: $accent;
  }
}
</style>
