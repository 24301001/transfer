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
.status-timeline {
  .timeline-content {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;

    .operator {
      font-size: 13px;
      color: #6b7280;
    }

    .detail {
      font-size: 12px;
      color: #9ca3af;
      width: 100%;
      margin-top: 2px;
    }
  }
}
</style>
