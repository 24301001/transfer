<template>
  <div class="map-card" :style="{ height }" @click="handleClick">
    <div class="map-placeholder">
      <div class="map-grid">
        <div v-for="i in 9" :key="i" class="grid-cell"></div>
      </div>
      <div class="map-overlay">
        <div v-if="markers && markers.length" class="map-markers">
          <div
            v-for="(m, idx) in markers"
            :key="idx"
            class="map-marker"
            :style="{ left: m.x + '%', top: m.y + '%' }"
            :title="m.label"
          >
            <el-badge :value="m.count || ''" :hidden="!m.count" :type="m.type || 'danger'">
              <el-icon :size="m.size || 28" :color="m.color || '#ef4444'">
                <LocationFilled />
              </el-icon>
            </el-badge>
          </div>
        </div>
        <div v-else class="map-empty">
          <el-icon :size="48" color="#93c5fd"><MapLocation /></el-icon>
          <p>{{ title || '地图区域' }}</p>
          <span class="map-hint">{{ hint || '地图接口预留·占位展示' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { LocationFilled, MapLocation } from '@element-plus/icons-vue'

defineProps({
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  height: { type: String, default: '300px' },
  markers: { type: Array, default: null },
})

const emit = defineEmits(['click'])

function handleClick() {
  emit('click')
}
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.map-card {
  width: 100%;
  border-radius: $radius-md;
  overflow: hidden;
  cursor: pointer;
  position: relative;
}

.map-placeholder {
  width: 100%;
  height: 100%;
  position: relative;
  background: #e8f0fe;
  border: 2px dashed #93c5fd;
  border-radius: $radius-md;
}

.map-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  width: 100%;
  height: 100%;
  position: absolute;
  inset: 0;
  opacity: 0.3;

  .grid-cell {
    border: 1px solid #93c5fd;
  }
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.map-empty {
  text-align: center;
  p {
    margin-top: 8px;
    font-size: 14px;
    color: $text-secondary;
    font-weight: 500;
  }
  .map-hint {
    font-size: 11px;
    color: $text-light;
  }
}

.map-markers {
  position: absolute;
  inset: 0;
}

.map-marker {
  position: absolute;
  transform: translate(-50%, -100%);
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: translate(-50%, -100%) scale(1.15);
  }
}
</style>
