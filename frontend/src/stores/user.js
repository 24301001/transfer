import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  // ====== 状态 ======
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  // ====== 计算属性 ======
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const role = computed(() => userInfo.value?.role || '')
  const roleLabel = computed(() => {
    const map = { POLICE: '现场交警', COMMAND: '指挥中心', RESCUE: '清障救援', ADMIN: '系统管理员' }
    return map[role.value] || ''
  })
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')

  // ====== 方法 ======
  function setUser(tokenValue, userInfoValue) {
    token.value = tokenValue
    userInfo.value = userInfoValue
    localStorage.setItem('token', tokenValue)
    localStorage.setItem('userInfo', JSON.stringify(userInfoValue))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    username,
    role,
    roleLabel,
    nickname,
    setUser,
    logout,
  }
})
