<template>
  <div class="semi-gauge-chart">
    <div ref="chartEl" class="chart-canvas"></div>
    <div v-if="!data.length" class="chart-empty">暂无统计数据</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  selectedName: { type: String, default: '' },
  totalLabel: { type: String, default: '事故总量' },
})

const emit = defineEmits(['select'])
const chartEl = ref(null)
let chart = null
let resizeObserver = null

const palette = [
  ['#00e5ff', '#1687ff'],
  ['#7c5cff', '#b46cff'],
  ['#00f5a0', '#00b8d9'],
  ['#ffb547', '#ff7a45'],
  ['#ff5f7e', '#ff2f68'],
  ['#4fd1ff', '#667eea'],
  ['#f6d365', '#fda085'],
  ['#43e97b', '#38f9d7'],
]

function buildOption() {
  const source = Array.isArray(props.data) ? props.data.filter((item) => Number(item.value) > 0) : []
  const total = source.reduce((sum, item) => sum + Number(item.value || 0), 0)
  const selected = props.selectedName
  const actualData = source.map((item, index) => {
    const colors = palette[index % palette.length]
    const isDimmed = selected && item.name !== selected
    return {
      name: item.name,
      value: Number(item.value || 0),
      selected: item.name === selected,
      itemStyle: {
        opacity: isDimmed ? 0.22 : 1,
        borderColor: 'rgba(2, 16, 35, 0.92)',
        borderWidth: 3,
        shadowBlur: isDimmed ? 0 : 14,
        shadowColor: colors[0] + '80',
        color: new echarts.graphic.LinearGradient(0, 0, 1, 1, [
          { offset: 0, color: colors[0] },
          { offset: 1, color: colors[1] },
        ]),
      },
    }
  })

  return {
    animationDuration: 900,
    animationDurationUpdate: 450,
    animationEasing: 'cubicOut',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(4, 18, 38, 0.96)',
      borderColor: 'rgba(0, 229, 255, 0.45)',
      borderWidth: 1,
      textStyle: { color: '#e9fbff', fontSize: 12 },
      formatter: (params) => {
        if (params.name === '__placeholder__') return ''
        return `${params.marker}${params.name}<br/><b>${params.value}</b> 起 · ${params.percent}%`
      },
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      left: 'center',
      width: '94%',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 12,
      icon: 'circle',
      textStyle: { color: '#9fc4d4', fontSize: 11 },
      pageIconColor: '#00d9ff',
      pageIconInactiveColor: '#36576b',
      pageTextStyle: { color: '#7897a8', fontSize: 10 },
      data: source.map((item) => item.name),
      formatter: (name) => {
        const item = source.find((entry) => entry.name === name)
        return `${name}  ${item?.value ?? 0}`
      },
    },
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '43%',
        style: {
          text: String(total),
          fill: '#f4fdff',
          fontSize: 30,
          fontWeight: 700,
          fontFamily: 'DIN Alternate, JetBrains Mono, monospace',
          textAlign: 'center',
          shadowBlur: 14,
          shadowColor: 'rgba(0, 229, 255, 0.45)',
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '57%',
        style: {
          text: props.totalLabel,
          fill: '#6f9daf',
          fontSize: 11,
          fontWeight: 500,
          textAlign: 'center',
          letterSpacing: 2,
        },
      },
      {
        type: 'text',
        left: 'center',
        top: '65%',
        style: {
          text: selected ? `已联动：${selected}` : '点击扇区联动 · 再次点击取消',
          fill: selected ? '#00e5ff' : '#4f7587',
          fontSize: 10,
          textAlign: 'center',
        },
      },
    ],
    series: [
      {
        type: 'pie',
        name: props.totalLabel,
        radius: ['58%', '80%'],
        center: ['50%', '66%'],
        startAngle: 180,
        clockwise: true,
        selectedMode: 'single',
        selectedOffset: 7,
        minAngle: 3,
        avoidLabelOverlap: true,
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          itemStyle: { shadowBlur: 22 },
        },
        data: [
          ...actualData,
          {
            name: '__placeholder__',
            value: total || 1,
            silent: true,
            itemStyle: { color: 'transparent', borderColor: 'transparent', opacity: 0 },
            tooltip: { show: false },
            emphasis: { disabled: true },
          },
        ],
      },
    ],
  }
}

function renderChart() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value, null, { renderer: 'canvas' })
    chart.on('click', (params) => {
      if (params.name && params.name !== '__placeholder__') {
        emit('select', params.name)
      }
    })
  }
  chart.setOption(buildOption(), true)
}

watch(
  () => [props.data, props.selectedName, props.totalLabel],
  () => nextTick(renderChart),
  { deep: true }
)

onMounted(() => {
  renderChart()
  if (typeof ResizeObserver !== 'undefined' && chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(chartEl.value)
  } else {
    window.addEventListener('resize', () => chart?.resize())
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style lang="scss" scoped>
.semi-gauge-chart {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 220px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #527486;
  font-size: 12px;
  letter-spacing: 0.08em;
}
</style>
