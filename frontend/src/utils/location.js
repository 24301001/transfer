// ========== 定位工具 ==========

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
 * 预设路段的真实坐标（WGS84）
 * 对应 PRESET_LOCATIONS 中的 id
 */
export const PRESET_COORDS = {
  1: { lat: 31.2365, lng: 121.7123 },
  2: { lat: 31.2987, lng: 121.3876 },
  3: { lat: 31.1245, lng: 121.4231 },
  4: { lat: 31.2678, lng: 121.4567 },
  5: { lat: 31.2345, lng: 121.4789 },
  6: { lat: 31.2123, lng: 121.3890 },
  7: { lat: 31.2012, lng: 121.4678 },
  8: { lat: 31.1567, lng: 121.2890 },
  9: { lat: 31.0890, lng: 121.3456 },
  10: { lat: 31.2567, lng: 121.7234 },
}

// ========== 坐标转换（BD09 ↔ WGS84） ==========
// Baidu Maps 使用 BD09，HTML5 Geolocation 返回 WGS84
const PI = Math.PI
const X_PI = (PI * 3000) / 180

function transformLat(x, y) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320.0 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}

function transformLng(x, y) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}

/** GCJ02 → WGS84 */
function gcj02ToWgs84(lng, lat) {
  const dlng = transformLng(lng - 105.0, lat - 35.0)
  const dlat = transformLat(lng - 105.0, lat - 35.0)
  const radlat = (lat / 180.0) * PI
  let magic = Math.sin(radlat)
  magic = 1 - 0.006693421622965943 * magic * magic
  const sqrtmagic = Math.sqrt(magic)
  const dlngAdj = (dlng * 180.0) / ((6378245.0 / sqrtmagic) * Math.cos(radlat) * PI)
  const dlatAdj = (dlat * 180.0) / (((1 - 0.006693421622965943) * 6378245.0) / (magic * sqrtmagic) * PI)
  return { lng: lng - dlngAdj, lat: lat - dlatAdj }
}

/** BD09 → GCJ02 */
function bd09ToGcj02(lng, lat) {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return { lng: z * Math.cos(theta), lat: z * Math.sin(theta) }
}

/**
 * BD09 → WGS84（百度坐标转 GPS 原始坐标）
 * 用于把用户在百度地图上点击的坐标转为后端需要的 WGS84 格式
 * @param {number} lng  BD09 经度
 * @param {number} lat  BD09 纬度
 * @returns {{lng: number, lat: number}} WGS84 坐标
 */
export function bd09ToWgs84(lng, lat) {
  const gcj02 = bd09ToGcj02(lng, lat)
  return gcj02ToWgs84(gcj02.lng, gcj02.lat)
}

// ====== WGS84 → BD09 转换 ======

/** WGS84 → GCJ02 */
function wgs84ToGcj02(lng, lat) {
  const dlng = transformLng(lng - 105.0, lat - 35.0)
  const dlat = transformLat(lng - 105.0, lat - 35.0)
  const radlat = (lat / 180.0) * PI
  let magic = Math.sin(radlat)
  magic = 1 - 0.006693421622965943 * magic * magic
  const sqrtmagic = Math.sqrt(magic)
  const dlngAdj = (dlng * 180.0) / ((6378245.0 / sqrtmagic) * Math.cos(radlat) * PI)
  const dlatAdj = (dlat * 180.0) / (((1 - 0.006693421622965943) * 6378245.0) / (magic * sqrtmagic) * PI)
  return { lng: lng + dlngAdj, lat: lat + dlatAdj }
}

/** GCJ02 → BD09 */
function gcj02ToBd09(lng, lat) {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI)
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI)
  return {
    lng: z * Math.cos(theta) + 0.0065,
    lat: z * Math.sin(theta) + 0.006,
  }
}

/**
 * WGS84 → BD09（GPS 原始坐标转百度坐标）
 * 用于在百度地图上显示 GPS 获取的坐标位置
 * @param {number} lng  WGS84 经度
 * @param {number} lat  WGS84 纬度
 * @returns {{lng: number, lat: number}} BD09 坐标
 */
export function wgs84ToBd09(lng, lat) {
  const gcj02 = wgs84ToGcj02(lng, lat)
  return gcj02ToBd09(gcj02.lng, gcj02.lat)
}

// ========== 定位函数 ==========

/**
 * 模拟自动定位
 * @returns {Promise<{lat: number, lng: number, address: string}>}
 */
export function mockGetCurrentLocation() {
  return new Promise((resolve) => {
    const delay = 800 + Math.random() * 700
    setTimeout(() => {
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

/**
 * 真实定位 — 使用 HTML5 Geolocation API 获取当前位置（WGS84）
 * @returns {Promise<{lat: number, lng: number}>}
 */
export function getRealCurrentPosition() {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('浏览器不支持地理定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        })
      },
      (error) => {
        const messages = {
          1: '位置获取被拒绝，请在浏览器设置中允许定位权限',
          2: '无法确定位置，请检查 GPS 或网络',
          3: '定位请求超时，请稍后重试',
        }
        reject(new Error(messages[error.code] || '定位失败'))
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 }
    )
  })
}

/**
 * 获取预设路段的坐标
 * @param {number} id  预设路段 id
 * @returns {{lat: number, lng: number}|null}
 */
export function getPresetCoords(id) {
  return PRESET_COORDS[id] || null
}

/**
 * 百度地图 IP 定位降级 — 使用 BMapGL.Geolocation 获取位置（BD09 → WGS84）
 *
 * 无需用户授予 GPS 权限，基于 IP 粗略定位。
 * 需要 BMapGL 已加载（页面中 MapCard 加载后 window.BMapGL 可用）。
 * @returns {Promise<{lat: number, lng: number}>} WGS84 坐标
 */
export function getBaiduIPLocation() {
  return new Promise((resolve, reject) => {
    const BMapGL = window.BMapGL
    if (!BMapGL || typeof BMapGL.Geolocation !== 'function') {
      reject(new Error('百度地图未加载，无法使用 IP 定位'))
      return
    }

    try {
      const geolocation = new BMapGL.Geolocation()

      geolocation.getCurrentPosition(
        function (result) {
          // this 指向 geolocation 实例
          const status = this.getStatus()
          // BMAP_STATUS_SUCCESS === 0
          if (status === 0 && result) {
            // result.point（BMapGL.Point）或 result 直接含 lng/lat
            const bd09Lng = result.point?.lng ?? result.lng
            const bd09Lat = result.point?.lat ?? result.lat

            if (bd09Lng != null && bd09Lat != null) {
              // BD09 → WGS84
              const wgs84 = bd09ToWgs84(bd09Lng, bd09Lat)
              resolve(wgs84)
              return
            }
          }
          reject(new Error('百度 IP 定位未返回有效坐标'))
        },
        { enableHighAccuracy: false, timeout: 5000 }
      )
    } catch (err) {
      reject(new Error('百度地图定位异常: ' + err.message))
    }
  })
}
