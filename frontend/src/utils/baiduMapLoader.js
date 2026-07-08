/**
 * 百度地图 JavaScript API 加载器（WebGL 版）
 *
 * 单例模式，全局只加载一次。
 * AK 解析顺序：传参 AK > 后端 /api/v1/maps/client-config > 报错
 *
 * 用法：
 *   import { loadBaiduMap } from '@/utils/baiduMapLoader'
 *   const BMapGL = await loadBaiduMap()         // 自动从后端获取 AK
 *   const BMapGL = await loadBaiduMap(ak)        // 使用指定 AK
 *   // 然后使用 BMapGL.Map、BMapGL.Marker……
 */

import request from '@/services/request'

const BAIDU_MAP_API_URL = 'https://api.map.baidu.com/api'

let loadPromise = null

/** 从后端获取浏览器端 AK */
async function fetchAkFromBackend() {
  try {
    const res = await request.get('/v1/maps/client-config')
    const config = res?.data
    if (config && config.enabled && config.browserAk) {
      return config.browserAk
    }
  } catch {
    // 静默失败，由调用方处理
  }
  return null
}

/**
 * 加载百度地图 WebGL API。
 *
 * @param {string} [ak]  浏览器端 AK。不传则自动从后端 /api/v1/maps/client-config 获取。
 * @returns {Promise<typeof BMapGL>}
 */
export async function loadBaiduMap(ak) {
  // 如果已有加载中的 Promise，复用
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    // 如果全局对象已存在（之前已加载完成），直接返回
    if (window.BMapGL && typeof window.BMapGL.Map === 'function') {
      return window.BMapGL
    }

    // ----- 解析 AK -----
    let resolvedAk = ak
    if (!resolvedAk) {
      resolvedAk = await fetchAkFromBackend()
    }
    if (!resolvedAk) {
      loadPromise = null
      throw new Error(
        '百度地图 AK 未提供。请配置后端 baidu.map.browser-ak，' +
        '或调用 loadBaiduMap(ak) 时传入 AK 参数。'
      )
    }

    // ----- 加载百度地图脚本 -----
    return new Promise((resolve, reject) => {
      const scriptUrl = `${BAIDU_MAP_API_URL}?v=1.0&type=webgl&ak=${resolvedAk}`

      const script = document.createElement('script')
      script.src = scriptUrl
      script.type = 'text/javascript'
      script.async = true
      script.onerror = () => {
        loadPromise = null
        reject(new Error('百度地图脚本加载失败，请检查网络或 AK 是否正确'))
      }

      // 轮询等待 BMapGL 可用
      const checkInterval = setInterval(() => {
        if (window.BMapGL && typeof window.BMapGL.Map === 'function') {
          clearInterval(checkInterval)
          clearTimeout(timeoutId)
          resolve(window.BMapGL)
        }
      }, 100)

      // 30 秒超时
      const timeoutId = setTimeout(() => {
        clearInterval(checkInterval)
        loadPromise = null
        reject(new Error('百度地图 API 加载超时'))
      }, 30000)

      document.head.appendChild(script)
    })
  })()

  return loadPromise
}

/**
 * 重置加载器（用于测试或重新加载）
 */
export function resetBaiduMapLoader() {
  loadPromise = null
}
