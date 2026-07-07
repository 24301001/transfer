<template>
  <div class="auth-layout">
    <div class="auth-bg"></div>
    <div class="auth-container">
      <div class="auth-brand">
        <div class="auth-brand-icon">
          <el-icon :size="48"><WarningFilled /></el-icon>
        </div>
        <h1>道路交通事故风险预估<br />与后果预测平台</h1>
        <p>Traffic Accident Risk Assessment & Consequence Prediction Platform</p>
      </div>
      <div class="auth-card">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { WarningFilled } from '@element-plus/icons-vue'
</script>

<style lang="scss" scoped>
@use '@/assets/styles/variables' as *;

.auth-layout {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.auth-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1a56db 0%, #1e40af 25%, #3b82f6 50%, #1e3a8a 75%, #1a56db 100%);
  background-size: 200% 200%;
  animation: breathe 15s ease-in-out infinite;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      radial-gradient(ellipse at 20% 50%, rgba(147, 197, 253, 0.15) 0%, transparent 60%),
      radial-gradient(ellipse at 80% 20%, rgba(59, 130, 246, 0.1) 0%, transparent 50%),
      radial-gradient(ellipse at 50% 80%, rgba(30, 64, 175, 0.2) 0%, transparent 50%);
  }
}

.auth-container {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 60px;
  max-width: 960px;
  width: 90%;
}

.auth-brand {
  flex: 1;
  color: #fff;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);

  .auth-brand-icon {
    width: 80px;
    height: 80px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.15);
    backdrop-filter: blur(10px);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24px;
  }

  h1 {
    font-size: 28px;
    font-weight: 700;
    line-height: 1.4;
    margin-bottom: 12px;
  }

  p {
    font-size: 14px;
    opacity: 0.8;
    letter-spacing: 0.5px;
  }
}

.auth-card {
  width: 400px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

@media (max-width: 768px) {
  .auth-container {
    flex-direction: column;
    gap: 30px;
  }
  .auth-brand {
    text-align: center;
    .auth-brand-icon {
      margin: 0 auto 16px;
    }
    h1 {
      font-size: 22px;
    }
  }
  .auth-card {
    width: 100%;
    max-width: 400px;
  }
}
</style>
