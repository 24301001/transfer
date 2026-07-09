<template>
  <div
    class="baidu-map-card"
    :style="{ height, width }"
    :class="{ 'is-picker': pickerMode, 'is-loaded': mapLoaded }"
  >
    <!-- 地图容器 -->
    <div ref="mapContainer" class="map-container"></div>

    <!-- 加载中 / 占位 -->
    <div v-if="!mapLoaded && !loadError" class="map-overlay">
      <div class="map-placeholder">
        <el-icon :size="48" color="#93c5fd"><MapLocation /></el-icon>
        <p>{{ title || '地图加载中…' }}</p>
        <span class="map-hint">{{ hint || '正在加载百度地图…' }}</span>
      </div>
    </div>

    <!-- 加载失败 -->
    <div v-if="loadError" class="map-overlay map-error-overlay">
      <div class="map-placeholder">
        <el-icon :size="48" color="#ef4444"><WarningFilled /></el-icon>
        <p>地图加载失败</p>
        <span class="map-hint">{{ loadErrorMessage }}</span>
        <el-button size="small" style="margin-top:8px" @click="initMap">重新加载</el-button>
      </div>
    </div>

    <!-- 位置已选择标签（picker 模式） -->
    <div v-if="pickerMode && selectedAddress && mapLoaded" class="picker-location-tag">
      <el-icon><LocationFilled /></el-icon>
      <span class="tag-text">{{ selectedAddress }}</span>
      <el-button
        v-if="selectedLng || selectedLat"
        size="small"
        text
        type="primary"
        @click="confirmLocation"
      >确认</el-button>
    </div>

    <!-- 地图控件（非 picker 模式） -->
    <div v-if="!pickerMode && mapLoaded" class="map-controls">
      <el-button-group>
        <el-button size="small" @click="zoomIn">
          <el-icon><Plus /></el-icon>
        </el-button>
        <el-button size="small" @click="zoomOut">
          <el-icon><Minus /></el-icon>
        </el-button>
      </el-button-group>
    </div>

    <!-- 遮罩提示（展示模式，提示可点击） -->
    <div v-if="!pickerMode && !loadError" class="map-click-hint" @click="handleCardClick">
      <span class="hint-text">{{ hint || '点击查看地图' }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { loadBaiduMap } from '@/utils/baiduMapLoader'
import { convertCoordinate, reverseGeocode } from '@/services/modules/map'
import { ElMessage } from 'element-plus'
import { MapLocation, LocationFilled, WarningFilled, Plus, Minus } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  height: { type: String, default: '300px' },
  width: { type: String, default: '100%' },
  /** 标记点数组。
   *  新格式（真实坐标）：[{ lng, lat, label?, color?, type?, count? }]
   *  兼容旧格式（百分比占位，不在地图上显示）：[{ x, y, label, ... }]
   */
  markers: { type: Array, default: null },
  /** 地图中心点（BD09 坐标系） */
  center: { type: Object, default: null },
  /** 缩放级别 3-21，默认 14 */
  zoom: { type: Number, default: 14 },
  /** 是否开启地点选取模式（点击地图选取位置，带拖拽标记） */
  pickerMode: { type: Boolean, default: false },
  /** 百度地图 AK，不传则使用默认配置 */
  ak: { type: String, default: '' },
  /** 标记物坐标系类型，用于将标记坐标转换为 BD09 显示 */
  markerCoordType: { type: String, default: 'WGS84' },
})

const emit = defineEmits([
  'click',
  'update:center',
  /** picker 模式下选中位置时触发 { lng, lat, address, formattedAddress } */
  'location-select',
  /** 已确认位置 */
  'location-confirm',
  /** 地图加载完成 */
  'map-ready',
])

// ====== 地图实例 ======
const mapContainer = ref(null)
let mapInstance = null
let markerInstance = null
let overlayMarkers = [] // 批量标记

// ====== 状态 ======
const mapLoaded = ref(false)
const loadError = ref(false)
const loadErrorMessage = ref('')
const selectedLng = ref(null)
const selectedLat = ref(null)
const selectedAddress = ref('')

/** 标记是否已首次渲染（等 tilesloaded） */
let markersRendered = false
/** 是否等待 tilesloaded 后刷新标记 */
let pendingMarkerRender = false

/** 地图瓦片加载完成回调 — 首次渲染标记或排期刷新 */
function onTilesLoaded() {
  if (!mapInstance) return
  if (!markersRendered) {
    markersRendered = true
    renderMarkers()
  } else if (pendingMarkerRender && props.markers?.length) {
    pendingMarkerRender = false
    renderMarkers()
  }
  // 首次渲染后不移除监听，后续 tilesloaded 也会触发排期刷新
}

// ====== 初始化地图 ======
async function initMap() {
  if (!mapContainer.value) return

  loadError.value = false
  loadErrorMessage.value = ''
  mapLoaded.value = false

  try {
    const BMapGL = await loadBaiduMap(props.ak || undefined)
    await nextTick()
    if (!mapContainer.value) return // 组件已卸载

    // 创建地图
    mapInstance = new BMapGL.Map(mapContainer.value)

    // 启用鼠标滚轮缩放
    mapInstance.enableScrollWheelZoom(true)

    // 默认中心（上海市中心）
    const defaultCenter = new BMapGL.Point(121.4737, 31.2304)
    let centerLng = 121.4737
    let centerLat = 31.2304

    // 如果有 markers 且包含真实坐标，自动适配到第一个标记
    if (props.markers && props.markers.length > 0) {
      const realMarker = props.markers.find((m) => m.lng !== undefined && m.lat !== undefined)
      if (realMarker) {
        centerLng = realMarker.lng
        centerLat = realMarker.lat
      }
    }

    // center prop 覆盖
    if (props.center && props.center.lng !== undefined) {
      centerLng = props.center.lng
      centerLat = props.center.lat
    }

    const centerPoint = new BMapGL.Point(centerLng, centerLat)
    mapInstance.centerAndZoom(centerPoint, props.zoom)

    // 控件
    mapInstance.addControl(new BMapGL.ScaleControl())
    mapInstance.addControl(new BMapGL.ZoomControl())

    // 设置点击事件（picker 模式）
    if (props.pickerMode) {
      mapInstance.addEventListener('click', onMapClick)
    }

    mapLoaded.value = true
    emit('map-ready', mapInstance)

    // 等地图瓦片加载完成后渲染标记（WebGL 引擎需要 tiles 就绪才能正确渲染覆盖物）
    mapInstance.addEventListener('tilesloaded', onTilesLoaded)

  } catch (err) {
    console.error('[MapCard] 地图初始化失败:', err)
    loadError.value = true
    loadErrorMessage.value = err.message || '请检查网络连接或 API Key'
  }
}

// ====== 地图点击（picker 模式） ======
let reverseGeocodeTimer = null

async function onMapClick(e) {
  if (!mapInstance || !props.pickerMode) return

  const point = e.latlng || e.point
  if (!point) return

  const lng = point.lng
  const lat = point.lat

  // 更新标记
  setPickerMarker(lng, lat)

  // 逆地理编码（防抖）
  if (reverseGeocodeTimer) clearTimeout(reverseGeocodeTimer)
  reverseGeocodeTimer = setTimeout(async () => {
    try {
      // BD09 → 后端逆地理
      const loc = await reverseGeocode(lng, lat, 'BD09')
      selectedAddress.value = loc.formattedAddress || `${lat.toFixed(4)}, ${lng.toFixed(4)}`
      emit('location-select', {
        lng,
        lat,
        address: selectedAddress.value,
        formattedAddress: loc.formattedAddress,
        semanticDescription: loc.semanticDescription,
        province: loc.province,
        city: loc.city,
        district: loc.district,
        street: loc.street,
      })
    } catch {
      selectedAddress.value = `${lat.toFixed(4)}, ${lng.toFixed(4)}`
      emit('location-select', { lng, lat, address: selectedAddress.value })
    }
  }, 300)
}

/** 设置 picker 标记 */
function setPickerMarker(lng, lat) {
  if (!mapInstance) return
  const BMapGL = window.BMapGL
  if (!BMapGL) return

  // 移除旧标记
  if (markerInstance) {
    mapInstance.removeOverlay(markerInstance)
  }

  const point = new BMapGL.Point(lng, lat)
  markerInstance = new BMapGL.Marker(point, { enableDragging: true })
  mapInstance.addOverlay(markerInstance)

  // 拖拽事件
  markerInstance.addEventListener('dragend', (e) => {
    const pt = e.point || e.latlng
    if (pt) {
      selectedLng.value = pt.lng
      selectedLat.value = pt.lat
      // 触发逆地理
      onMapClick({ latlng: pt })
    }
  })

  selectedLng.value = lng
  selectedLat.value = lat

  // 动画移动
  mapInstance.panTo(point)
}

function confirmLocation() {
  if (selectedLng.value && selectedLat.value) {
    emit('location-confirm', {
      lng: selectedLng.value,
      lat: selectedLat.value,
      address: selectedAddress.value,
    })
  }
}

// ====== 渲染标记 ======
function renderMarkers() {
  if (!mapInstance || !props.markers) return
  const BMapGL = window.BMapGL
  if (!BMapGL) return

  // 如果瓦片尚未加载完成，排期到 tilesloaded 再渲染
  if (!markersRendered) {
    pendingMarkerRender = true
    return
  }

  // 清除旧标记
  clearOverlays()

  const realMarkers = props.markers.filter((m) => m.lng !== undefined && m.lat !== undefined)
  if (realMarkers.length === 0) return

  console.log('[MapCard] 渲染 ' + realMarkers.length + ' 个标记:', JSON.stringify(realMarkers.map(m => ({lng: m.lng, lat: m.lat, label: m.label}))))

  // 过滤无效坐标
  const validMarkers = realMarkers.filter((m) => {
    return isFinite(m.lng) && isFinite(m.lat) && !isNaN(m.lng) && !isNaN(m.lat)
  })
  if (validMarkers.length === 0) {
    console.warn('[MapCard] 所有标记坐标无效，跳过渲染')
    return
  }
  if (validMarkers.length < realMarkers.length) {
    console.warn('[MapCard] 已过滤 ' + (realMarkers.length - validMarkers.length) + ' 个无效坐标标记')
  }

  // 添加新标记（逐个保护，任一失败不影响其他）
  validMarkers.forEach((m, index) => {
    try {
      const point = new BMapGL.Point(m.lng, m.lat)
      const marker = new BMapGL.Marker(point)
      mapInstance.addOverlay(marker)
      overlayMarkers.push(marker)

      // 信息窗口
      if (m.label) {
        const label = new BMapGL.Label(m.label, {
          position: point,
          offset: new BMapGL.Size(10, -25),
        })
        label.setStyle({
          color: '#333',
          fontSize: '12px',
          padding: '4px 8px',
          border: '1px solid #d1d5db',
          borderRadius: '4px',
          background: '#fff',
          whiteSpace: 'nowrap',
        })
        mapInstance.addOverlay(label)
        overlayMarkers.push(label)
      }

      if (m.onClick) {
        marker.addEventListener('click', m.onClick)
      }
    } catch (e) {
      console.warn('[MapCard] 标记 #' + index + ' 渲染失败:', e)
    }
  })

  // 适配到所有标记（异常保护）
  if (validMarkers.length > 0 && !props.center) {
    try {
      const points = validMarkers.map((m) => new BMapGL.Point(m.lng, m.lat))
      const viewport = mapInstance.getViewport(points)
      if (viewport && viewport.center && isFinite(viewport.zoom)) {
        mapInstance.centerAndZoom(viewport.center, viewport.zoom)
      }
    } catch (e) {
      console.warn('[MapCard] getViewport 自适应失败:', e)
    }
  }
}

function clearOverlays() {
  if (!mapInstance) return
  overlayMarkers.forEach((m) => mapInstance.removeOverlay(m))
  overlayMarkers = []
}

// ====== 缩放 ======
function zoomIn() {
  if (mapInstance) mapInstance.zoomIn()
}
function zoomOut() {
  if (mapInstance) mapInstance.zoomOut()
}

// ====== 卡片点击 ======
function handleCardClick() {
  emit('click')
}

// ====== 生命周期 ======
onMounted(() => {
  initMap()
})

onBeforeUnmount(() => {
  if (markerInstance && mapInstance) {
    mapInstance.removeOverlay(markerInstance)
  }
  clearOverlays()
  if (mapInstance) {
    mapInstance.removeEventListener('click', onMapClick)
    mapInstance.removeEventListener('tilesloaded', onTilesLoaded)
    mapInstance.destroy()
    mapInstance = null
  }
})

// ====== 监听 markers 变化 ======
watch(
  () => props.markers,
  (newMarkers) => {
    if (mapLoaded.value) {
      if (markersRendered) {
        renderMarkers()
      } else {
        // 首次标记数据已到但 tiles 未就绪 → 标记排期，tilesloaded 时会渲染
        pendingMarkerRender = true
      }
    }
  },
  { deep: true }
)

watch(
  () => props.center,
  (newCenter) => {
    if (mapLoaded.value && newCenter && newCenter.lng !== undefined) {
      const BMapGL = window.BMapGL
      if (BMapGL) {
        mapInstance.panTo(new BMapGL.Point(newCenter.lng, newCenter.lat))
      }
    }
  },
  { deep: true }
)
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.baidu-map-card {
  position: relative;
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  background: linear-gradient(135deg, rgba($accent, 0.04), rgba($accent-secondary, 0.02));
  border: 1px solid $border;

  &.is-picker {
    cursor: crosshair;
  }

  &.is-loaded {
    border-color: transparent;
  }
}

.map-container {
  width: 100%;
  height: 100%;
  min-height: inherit;
}

// 加载 / 错误 遮罩
.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  background: linear-gradient(135deg, rgba($accent, 0.04), rgba($accent-secondary, 0.02));
  border: 2px dashed rgba($accent, 0.2);
  border-radius: 10px;
}

.map-placeholder {
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

.map-error-overlay {
  background: linear-gradient(135deg, rgba($danger, 0.04), rgba($danger, 0.02));
  border-color: rgba($danger, 0.2);
}

// 位置标签
.picker-location-tag {
  position: absolute;
  bottom: 12px;
  left: 12px;
  right: 12px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-radius: 10px;
  box-shadow: $shadow-md;
  font-size: 13px;
  color: $accent;

  .tag-text {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// 缩放控件
.map-controls {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 100;

  .el-button-group .el-button {
    border-radius: 8px;
    &:first-child { border-radius: 8px 8px 0 0; }
    &:last-child { border-radius: 0 0 8px 8px; }
  }
}

// 点击提示
.map-click-hint {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 6px 12px;
  background: linear-gradient(transparent, rgba($text-primary, 0.7));
  color: #fff;
  font-size: 11px;
  text-align: center;
  cursor: pointer;
  z-index: 50;
  transition: opacity 0.2s;

  &:hover {
    opacity: 0.85;
  }

  .hint-text {
    opacity: 0.9;
    font-weight: 500;
  }
}
</style>
