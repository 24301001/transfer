import router from './index'
import { useUserStore } from '@/stores/user'
import { getRoleByKey } from '@/utils/role'
import { ElMessage } from 'element-plus'

// 白名单 - 不需要登录的页面
const whiteList = ['Login', 'Register']

router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 道路交通事故风险预估与后果预测平台` : '道路交通事故风险预估与后果预测平台'

  // 用户 store
  const userStore = useUserStore()

  // 白名单页面直接放行
  if (whiteList.includes(to.name)) {
    // 如果已登录且想去登录页，重定向到角色首页
    if (userStore.isLoggedIn && to.name === 'Login') {
      const role = getRoleByKey(userStore.role)
      return next(role ? role.home : '/')
    }
    return next()
  }

  // 未登录 → 重定向到登录页
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return next('/login')
  }

  // 检查角色权限
  const allowedRoles = to.meta.roles
  if (allowedRoles && !allowedRoles.includes(userStore.role)) {
    ElMessage.error('无权限访问该页面')
    const role = getRoleByKey(userStore.role)
    return next(role ? role.home : '/login')
  }

  next()
})

export default router
