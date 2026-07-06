<template>
  <nav class="navbar">
    <div class="navbar-brand">
      <router-link to="/">Transfer</router-link>
    </div>
    <div class="navbar-menu">
      <router-link to="/">首页</router-link>
    </div>
  </nav>
</template>

<script setup>
</script>

<style scoped>
.navbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background-color: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-brand a {
  font-size: 20px;
  font-weight: 700;
  color: #1a73e8;
  text-decoration: none;
}

.navbar-menu a {
  margin-left: 24px;
  color: #555;
  text-decoration: none;
  font-size: 15px;
  transition: color 0.2s;
}

.navbar-menu a:hover,
.navbar-menu a.router-link-exact-active {
  color: #1a73e8;
}
</style>
