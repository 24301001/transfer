// ========== 角色常量与工具函数 ==========

/**
 * 角色定义
 * key: 角色标识
 * label: 显示名称
 * home: 默认首页路由
 * desc: 角色描述
 */
export const ROLES = {
  POLICE: {
    key: 'POLICE',
    label: '现场交警',
    home: '/police/report',
    desc: '事故现场上报与处置',
  },
  COMMAND: {
    key: 'COMMAND',
    label: '指挥中心',
    home: '/command/dashboard',
    desc: '指挥调度与监控',
  },
  RESCUE: {
    key: 'RESCUE',
    label: '清障救援',
    home: '/rescue/tasks',
    desc: '清障与救援任务执行',
  },
  ADMIN: {
    key: 'ADMIN',
    label: '系统管理员',
    home: '/admin/users',
    desc: '系统维护与管理',
  },
}

/** 角色列表（用于注册选择） */
export const ROLE_OPTIONS = Object.values(ROLES).map((r) => ({
  value: r.key,
  label: r.label,
  desc: r.desc,
}))

/**
 * 根据角色 key 获取角色信息
 * @param {string} key
 * @returns {object|undefined}
 */
export function getRoleByKey(key) {
  return Object.values(ROLES).find((r) => r.key === key)
}

/**
 * 获取角色的默认首页
 * @param {string} key
 * @returns {string}
 */
export function getRoleHome(key) {
  const role = getRoleByKey(key)
  return role ? role.home : '/login'
}
