import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDispatchStore = defineStore('dispatch', () => {
  // ====== 调度任务全局数据池 ======
  const taskList = ref([])
  const loading = ref(false)

  // ====== 设置任务列表 ======
  function setTasks(list) {
    taskList.value = list
  }

  // ====== 添加新任务 ======
  function addTask(task) {
    taskList.value.unshift(task)
  }

  // ====== 获取单个任务 ======
  function getTaskById(id) {
    return taskList.value.find((t) => t.id === id)
  }

  // ====== 更新任务状态 ======
  function updateTaskStatus(id, status, feedback) {
    const task = taskList.value.find((t) => t.id === id)
    if (task) {
      task.status = status
      if (feedback) task.feedback = feedback
      task.updateTime = new Date().toLocaleString('zh-CN')
    }
  }

  // ====== 获取待处理任务（状态不是"已完成"的） ======
  function getPendingTasks() {
    return taskList.value.filter((t) => t.status !== '已完成' && t.status !== '已关闭')
  }

  return {
    taskList,
    loading,
    setTasks,
    addTask,
    getTaskById,
    updateTaskStatus,
    getPendingTasks,
  }
})
