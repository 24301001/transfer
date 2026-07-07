import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAccidentStore = defineStore('accident', () => {
  // ====== 事故全局数据池 ======
  const accidentList = ref([])
  const loading = ref(false)

  // ====== 获取所有事故 ======
  function setAccidents(list) {
    accidentList.value = list
  }

  // ====== 添加新事故（交警上报后调用） ======
  function addAccident(accident) {
    accidentList.value.unshift(accident)
  }

  // ====== 根据 ID 获取事故 ======
  function getAccidentById(id) {
    return accidentList.value.find((a) => a.id === id)
  }

  // ====== 更新事故信息（如识别结果完成时） ======
  function updateAccident(id, data) {
    const idx = accidentList.value.findIndex((a) => a.id === id)
    if (idx !== -1) {
      accidentList.value[idx] = { ...accidentList.value[idx], ...data }
    }
  }

  // ====== 筛选事故 ======
  function filterAccidents({ dateRange, area, status, riskLevel } = {}) {
    let list = [...accidentList.value]
    if (dateRange && dateRange.length === 2) {
      const start = new Date(dateRange[0]).getTime()
      const end = new Date(dateRange[1]).getTime()
      list = list.filter((a) => {
        const t = new Date(a.reportTime).getTime()
        return t >= start && t <= end
      })
    }
    if (area) {
      list = list.filter((a) => a.area?.includes(area))
    }
    if (status) {
      list = list.filter((a) => a.status === status)
    }
    if (riskLevel) {
      list = list.filter((a) => a.riskLevel === riskLevel)
    }
    return list
  }

  return {
    accidentList,
    loading,
    setAccidents,
    addAccident,
    getAccidentById,
    updateAccident,
    filterAccidents,
  }
})
