import { createRouter, createWebHistory } from 'vue-router'
import { ROLES } from '@/utils/role'
import AuthLayout from '@/layouts/AuthLayout.vue'
import MainLayout from '@/layouts/MainLayout.vue'

const routes = [
  {
    path: '/login',
    component: AuthLayout,
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('@/views/login/Login.vue'),
        meta: { title: '登录' },
      },
      {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/login/Register.vue'),
        meta: { title: '注册' },
      },
    ],
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/login',
    children: [
      // ====== 现场交警 ======
      {
        path: 'police/report',
        name: 'ReportAccident',
        component: () => import('@/views/police/ReportAccident.vue'),
        meta: { title: '事故上报', roles: [ROLES.POLICE.key] },
      },
      // ====== 指挥中心 ======
      {
        path: 'command/dashboard',
        name: 'CommandDashboard',
        component: () => import('@/views/command/Dashboard.vue'),
        meta: { title: '指挥大屏', roles: [ROLES.COMMAND.key] },
      },
      {
        path: 'command/accident/:id',
        name: 'AccidentDetail',
        component: () => import('@/views/command/AccidentDetail.vue'),
        meta: { title: '事故详情', roles: [ROLES.COMMAND.key, ROLES.RESCUE.key] },
      },
      {
        path: 'command/dispatch',
        name: 'DispatchTask',
        component: () => import('@/views/command/DispatchTask.vue'),
        meta: { title: '调度处理', roles: [ROLES.COMMAND.key] },
      },
      {
        path: 'command/accident-query',
        name: 'AccidentQuery',
        component: () => import('@/views/command/AccidentQuery.vue'),
        meta: { title: '事故查询', roles: [ROLES.COMMAND.key] },
      },
      // ====== 清障救援 ======
      {
        path: 'rescue/tasks',
        name: 'RescueTaskList',
        component: () => import('@/views/rescue/TaskList.vue'),
        meta: { title: '清障任务', roles: [ROLES.RESCUE.key] },
      },
      {
        path: 'rescue/task/:id',
        name: 'RescueTaskDetail',
        component: () => import('@/views/rescue/TaskDetail.vue'),
        meta: { title: '任务详情', roles: [ROLES.RESCUE.key] },
      },
      // ====== 系统管理员 ======
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理', roles: [ROLES.ADMIN.key] },
      },
      {
        path: 'admin/logs',
        name: 'AdminLogs',
        component: () => import('@/views/admin/OperationLog.vue'),
        meta: { title: '操作日志', roles: [ROLES.ADMIN.key] },
      },
      {
        path: 'admin/health',
        name: 'AdminHealth',
        component: () => import('@/views/admin/SystemHealth.vue'),
        meta: { title: '系统健康', roles: [ROLES.ADMIN.key] },
      },
      // ====== 个人中心（所有角色） ======
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', roles: [ROLES.POLICE.key, ROLES.COMMAND.key, ROLES.RESCUE.key, ROLES.ADMIN.key] },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
