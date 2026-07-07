// ========== 模拟定位工具 ==========

/**
 * 预设路段/点位数据
 * 用于下拉选择事故地点
 */
export const PRESET_LOCATIONS = [
  { id: 1, name: 'G15 沈海高速 K1200+500 段（北向）', road: 'G15 沈海高速', area: '浦东新区' },
  { id: 2, name: 'G2 京沪高速 K980+200 段（南向）', road: 'G2 京沪高速', area: '嘉定区' },
  { id: 3, name: 'S20 外环高速 K85+300 段（内圈）', road: 'S20 外环高速', area: '闵行区' },
  { id: 4, name: '中环路 汶水路段（西向）', road: '中环路', area: '普陀区' },
  { id: 5, name: '南北高架 北京路段（南向）', road: '南北高架', area: '黄浦区' },
  { id: 6, name: '延安高架 虹桥枢纽入口（东向）', road: '延安高架', area: '长宁区' },
  { id: 7, name: '内环高架 中山南二路段（外圈）', road: '内环高架', area: '徐汇区' },
  { id: 8, name: 'G50 沪渝高速 K30+800 段（西向）', road: 'G50 沪渝高速', area: '青浦区' },
  { id: 9, name: 'G60 沪昆高速 K45+600 段（南向）', road: 'G60 沪昆高速', area: '松江区' },
  { id: 10, name: '华夏高架 川沙路段（东向）', road: '华夏高架', area: '浦东新区' },
]

/**
 * 模拟自动定位
 * 返回一个 Promise，模拟 GPS 定位延迟
 * @returns {Promise<{lat: number, lng: number, address: string}>}
 */
export function mockGetCurrentLocation() {
  return new Promise((resolve) => {
    // 模拟定位延迟 800-1500ms
    const delay = 800 + Math.random() * 700
    setTimeout(() => {
      // 模拟上海市区范围内的随机坐标
      const lat = 31.2 + Math.random() * 0.3
      const lng = 121.4 + Math.random() * 0.3
      const roads = [
        '浦东大道近民生路',
        '延安西路近江苏路',
        '沪闵路近桂林路',
        '共和新路近中山北路',
        '杨高南路近龙阳路',
        '漕溪北路近南丹路',
      ]
      const address = roads[Math.floor(Math.random() * roads.length)]
      resolve({ lat: +lat.toFixed(4), lng: +lng.toFixed(4), address })
    }, delay)
  })
}
