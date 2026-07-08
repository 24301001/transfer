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
const CALLBACK_NAME = '__onBMapGLReady'

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
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    // 如果全局对象已存在，直接返回
    if (window.BMapGL && typeof window.BMapGL.Map === 'function') {
      resolve(window.BMapGL)
      return
    }

    let resolved = false

    // 注册全局 callback — 百度脚本加载完成后会调用此函数
    window[CALLBACK_NAME] = () => {
      resolved = true
      if (window.BMapGL) {
        resolve(window.BMapGL)
      } else {
        reject(new Error('百度地图 API 加载完成但 BMapGL 对象不存在'))
      }
    }

    // 异步解析 AK，然后注入 script 标签
    ;(async () => {
      try {
        // ----- 解析 AK -----
        let resolvedAk = ak
        if (!resolvedAk) {
          resolvedAk = await fetchAkFromBackend()
        }
        if (!resolvedAk) {
          loadPromise = null
          reject(new Error(
            '百度地图 AK 未提供。请配置后端 baidu.map.browser-ak，' +
            '或调用 loadBaiduMap(ak) 时传入 AK 参数。'
          ))
          return
        }

        // ----- 加载百度地图脚本 -----
        // 注意：不设置 script.async = true。
        // 百度地图内部用 document.write 加载依赖模块，
        // 异步脚本中 document.write 会被浏览器禁止。
        const scriptUrl = `${BAIDU_MAP_API_URL}?v=1.0&type=webgl&ak=${resolvedAk}&callback=${CALLBACK_NAME}`

        const script = document.createElement('script')
        script.src = scriptUrl
        script.type = 'text/javascript'
        script.onerror = () => {
          if (!resolved) {
            loadPromise = null
            reject(new Error('百度地图脚本加载失败，请检查网络或 AK 是否正确'))
          }
        }

        // 30 秒超时保护
        const timeoutId = setTimeout(() => {
          if (!resolved) {
            loadPromise = null
            reject(new Error('百度地图 API 加载超时'))
          }
        }, 30000)

        document.head.appendChild(script)
      } catch (err) {
        loadPromise = null
        reject(err)
      }
    })()
  })

  return loadPromise
}

/**
 * 重置加载器（用于测试或重新加载）
 */
export function resetBaiduMapLoader() {
  loadPromise = null
  delete window[CALLBACK_NAME]
}
