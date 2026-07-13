import request from '../request'

/**
 * 事故上报页悬浮球 AI 问答
 * POST /api/v1/report-ai/chat
 * @param {object} params
 * @param {number} [params.incidentId] - 关联的事故 ID（可选）
 * @param {string} params.question - 用户问题
 * @param {string} [params.locationName] - 地点名称
 * @param {string} [params.description] - 事故描述
 */
export async function aiChat(params) {
  const res = await request.post('/v1/report-ai/chat', params)
  return res.data
}

/**
 * 获取已上报事故的即时安全提示
 * GET /api/v1/report-ai/incidents/{incidentId}/instant-advice
 * @param {number} incidentId
 */
export async function getInstantAdvice(incidentId) {
  const res = await request.get(`/v1/report-ai/incidents/${incidentId}/instant-advice`)
  return res.data
}
