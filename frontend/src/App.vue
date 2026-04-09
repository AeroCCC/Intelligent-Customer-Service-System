<template>
  <div class="app-container">
    <header class="app-header">
      <h1>智能客服</h1>
    </header>
    <main class="app-main">
      <div class="chat-wrapper">
        <ChatWindow />
      </div>
    </main>
  </div>
</template>

<script setup>
import ChatWindow from './components/ChatWindow.vue'
</script>

<style scoped>
.app-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.app-header {
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
}

.app-header h1 {
  color: white;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}

.app-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
}

.chat-wrapper {
  width: 100%;
  max-width: 900px;
  height: calc(100vh - 120px);
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}
</style>
