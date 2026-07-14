import request from '../request'

/**
 * 根据事故地点坐标获取实时天气。
 *
 * @param {number} longitude 经度
 * @param {number} latitude 纬度
 * @param {'WGS84'|'GCJ02'|'BD09'} coordinateType 坐标类型
 *
 * @returns {Promise<{
 *   source: string,
 *   longitude: number,
 *   latitude: number,
 *   coordinateType: string,
 *   country: string,
 *   province: string,
 *   city: string,
 *   district: string,
 *   districtId: string,
 *   text: string,
 *   temperatureC: number,
 *   feelsLikeC: number,
 *   humidityPercent: number,
 *   windDirection: string,
 *   windClass: string,
 *   precipitation1hMm: number,
 *   cloudPercent: number,
 *   visibilityMeters: number,
 *   aqi: number,
 *   updateTime: string
 * }>}
 */
export async function getCurrentWeather(
  longitude,
  latitude,
  coordinateType = 'WGS84'
) {
  const lng = Number(longitude)
  const lat = Number(latitude)

  if (
    !Number.isFinite(lng) ||
    !Number.isFinite(lat)
  ) {
    throw new Error(
      '天气查询失败：事故地点经纬度无效'
    )
  }

  if (
    lng < -180 ||
    lng > 180 ||
    lat < -90 ||
    lat > 90
  ) {
    throw new Error(
      '天气查询失败：事故地点经纬度超出有效范围'
    )
  }

  const res = await request.get(
    '/v1/weather/current',
    {
      params: {
        longitude: lng,
        latitude: lat,
        coordinateType,
      },
    }
  )

  return res.data
}
