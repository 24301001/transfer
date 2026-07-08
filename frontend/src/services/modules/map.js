/**
 * 百度地图服务模块
 *
 * 封装后端 MapController 的所有接口：
 *   - clientConfig   GET /api/v1/maps/client-config   获取百度地图 JS API 配置
 *   - geocode        GET /api/v1/maps/geocode          文字地址 → BD09 坐标
 *   - reverseGeocode GET /api/v1/maps/reverse-geocode  坐标 → 文字地址
 *   - convert        GET /api/v1/maps/convert          坐标转换（WGS84/GCJ02 → BD09）
 */
import request from '../request'

/**
 * 获取百度地图 JS API 配置（浏览器端 AK、script URL）
 * @returns {Promise<{enabled: boolean, browserAk: string, scriptUrl: string, message: string}>}
 */
export async function getMapClientConfig() {
  const res = await request.get('/v1/maps/client-config')
  return res.data
}

/**
 * 文字地址 → 百度坐标（BD09）
 * @param {string} address  地址描述
 * @param {string} [city]   所在城市（可选）
 * @returns {Promise<MapLocationResponse>}
 */
export async function geocode(address, city) {
  const params = { address }
  if (city) params.city = city
  const res = await request.get('/v1/maps/geocode', { params })
  return res.data
}

/**
 * 坐标 → 文字地址（逆地理编码）
 * @param {number} lng           经度
 * @param {number} lat           纬度
 * @param {string} [coordType]   源坐标类型 'WGS84' | 'GCJ02' | 'BD09'，默认 WGS84
 * @returns {Promise<MapLocationResponse>}
 */
export async function reverseGeocode(lng, lat, coordType = 'WGS84') {
  const params = { longitude: lng, latitude: lat, coordinateType: coordType }
  const res = await request.get('/v1/maps/reverse-geocode', { params })
  return res.data
}

/**
 * 坐标转换（WGS84/GCJ02 → BD09）
 * @param {number} lng           经度
 * @param {number} lat           纬度
 * @param {string} [coordType]   源坐标类型 'WGS84' | 'GCJ02'，默认 WGS84
 * @returns {Promise<{longitude: number, latitude: number, coordinateType: string}>}
 */
export async function convertCoordinate(lng, lat, coordType = 'WGS84') {
  const params = { longitude: lng, latitude: lat, coordinateType: coordType }
  const res = await request.get('/v1/maps/convert', { params })
  return res.data
}

/**
 * 获取当前地理位置（使用 HTML5 Geolocation API）
 * @returns {Promise<{lat: number, lng: number}>}
 */
export function getCurrentGPSPosition() {
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
          2: '无法确定位置，请检查 GPS 或网络连接',
          3: '定位请求超时，请稍后重试',
        }
        reject(new Error(messages[error.code] || '定位失败'))
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 30000,
      }
    )
  })
}
