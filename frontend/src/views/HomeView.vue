<template>
  <div class="home">
    <div class="hero">
      <h1>欢迎使用 Transfer</h1>
      <p>Vue 3 + Spring Boot 前后端分离应用框架</p>
    </div>

    <div class="status-card">
      <div class="status-header">
        <h2>系统状态</h2>
        <span :class="['badge', backendStatus === 'UP' ? 'badge-success' : 'badge-error']">
          {{ backendStatus === 'UP' ? '运行中' : '未连接' }}
        </span>
      </div>
      <p v-if="statusMessage">{{ statusMessage }}</p>
      <button class="btn" @click="checkHealth">检查后端连接</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import api from '../services/api'

const backendStatus = ref('UNKNOWN')
const statusMessage = ref('')

const checkHealth = async () => {
  try {
    const res = await api.get('/health')
    backendStatus.value = res.data.status
    statusMessage.value = res.data.message
  } catch (error) {
    backendStatus.value = 'DOWN'
    statusMessage.value = '无法连接到后端服务，请确保后端已启动'
  }
}
</script>

<style scoped>
.hero {
  text-align: center;
  padding: 60px 20px 40px;
}

.hero h1 {
  font-size: 36px;
  color: #1a73e8;
  margin-bottom: 12px;
}

.hero p {
  font-size: 18px;
  color: #666;
}

.status-card {
  max-width: 500px;
  margin: 0 auto;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.status-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.status-header h2 {
  font-size: 20px;
}

.badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

.badge-success {
  background: #e6f7e6;
  color: #389e0d;
}

.badge-error {
  background: #fff1f0;
  color: #cf1322;
}

.status-card p {
  color: #888;
  margin-bottom: 16px;
}

.btn {
  display: inline-block;
  padding: 10px 24px;
  background: #1a73e8;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
}

.btn:hover {
  background: #1557b0;
}
</style>
