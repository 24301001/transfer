<template>
  <router-view />
</template>

<script setup>
// 根组件 - 由路由决定显示 AuthLayout 或 MainLayout
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}
</style>
