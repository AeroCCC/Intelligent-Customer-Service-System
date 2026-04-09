<template>
  <div class="chat-container">
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon">🤖</div>
        <h2>您好，我是智能客服</h2>
        <p>有什么可以帮您的吗？</p>
      </div>

      <div v-for="(msg, index) in messages" :key="index" :class="['message', msg.role]">
        <div class="message-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="message-content">
          <div class="message-text" v-html="formatMessage(msg.content)"></div>
          <div v-if="msg.citations && msg.citations.length > 0" class="citations">
            <div class="citations-title">参考来源：</div>
            <div v-for="(citation, cIndex) in msg.citations" :key="cIndex" class="citation-item">
              <span class="citation-source">{{ citation.source }}</span>
              <span class="citation-content">{{ citation.content }}</span>
            </div>
          </div>
          <div class="message-time">{{ formatTime(msg.createdAt) }}</div>
        </div>
      </div>

      <div v-if="isLoading" class="message assistant loading">
        <div class="message-avatar">🤖</div>
        <div class="message-content">
          <div class="loading-dots"><span></span><span></span><span></span></div>
          <div class="loading-text">{{ loadingStatus }}</div>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-wrapper">
        <input
          v-model="inputText"
          @keyup.enter="sendMessage"
          placeholder="请输入您的问题..."
          :disabled="isLoading"
          class="input-field"
        />
        <button @click="sendMessage" :disabled="isLoading || !inputText.trim()" class="send-btn">
          {{ isLoading ? '发送中...' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { getConversations, getMessages, sendChatMessage } from '../services/api.js'

const messages = ref([])
const inputText = ref('')
const isLoading = ref(false)
const loadingStatus = ref('正在连接客服引擎...')
const messagesContainer = ref(null)
const currentConversationId = ref(null)

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const formatMessage = (content) => {
  if (!content) return ''

  const escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

  return escaped
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

const formatTime = (dateStr) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const sendMessage = async () => {
  if (!inputText.value.trim() || isLoading.value) return

  const query = inputText.value.trim()
  inputText.value = ''

  messages.value.push({ role: 'user', content: query, createdAt: new Date().toISOString() })
  scrollToBottom()
  isLoading.value = true
  loadingStatus.value = '正在连接客服引擎...'

  try {
    let fullAnswer = ''
    let citations = []

    await sendChatMessage(
      query,
      currentConversationId.value,
      'default-user',
      (data) => {
        if (data.type === 'status') {
          loadingStatus.value = data.message || '正在处理中...'
          return
        }

        if (data.type === 'message') {
          fullAnswer += data.content
          const lastMsg = messages.value[messages.value.length - 1]
          if (lastMsg && lastMsg.role === 'assistant') {
            lastMsg.content = fullAnswer
          } else {
            messages.value.push({ role: 'assistant', content: fullAnswer, createdAt: new Date().toISOString() })
          }
          scrollToBottom()
          return
        }

        if (data.type === 'end') {
          if (data.conversationId) currentConversationId.value = data.conversationId
          if (data.citations) citations = data.citations
          const lastMsg = messages.value[messages.value.length - 1]
          if (lastMsg && lastMsg.role === 'assistant') {
            lastMsg.citations = citations
          }
        }
      },
      (error) => {
        console.error('Chat error:', error)
        messages.value.push({
          role: 'assistant',
          content: '抱歉，发送过程中出现了问题，请稍后再试。',
          createdAt: new Date().toISOString()
        })
      }
    )
  } catch (error) {
    console.error('Send message error:', error)
    messages.value.push({
      role: 'assistant',
      content: '抱歉，服务暂时不可用，请稍后再试。',
      createdAt: new Date().toISOString()
    })
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

onMounted(async () => {
  try {
    const result = await getConversations('default-user')
    if (result.success && result.data && result.data.length > 0) {
      currentConversationId.value = String(result.data[0].id)
      const history = await getMessages(currentConversationId.value)
      if (history.success && history.data) {
        messages.value = history.data.map((msg) => ({
          role: msg.role,
          content: msg.content,
          createdAt: msg.createdAt,
          citations: msg.citations || []
        }))
        scrollToBottom()
      }
    }
  } catch (error) {
    console.log('No previous conversations')
  }
})
</script>

<style scoped>
.chat-container { display: flex; flex-direction: column; height: 100%; }
.chat-messages { flex: 1; overflow-y: auto; padding: 24px; background: #f8fafc; }
.welcome-message { text-align: center; padding: 60px 20px; color: #64748b; }
.welcome-icon { font-size: 64px; margin-bottom: 16px; }
.welcome-message h2 { font-size: 24px; color: #1e293b; margin-bottom: 8px; }
.message { display: flex; gap: 12px; margin-bottom: 20px; animation: fadeIn 0.3s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.message.user { flex-direction: row-reverse; }
.message-avatar { width: 40px; height: 40px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; }
.message.user .message-avatar { background: #3b82f6; }
.message.assistant .message-avatar { background: #10b981; }
.message-content { max-width: 70%; padding: 12px 16px; border-radius: 12px; background: white; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.message.user .message-content { background: #3b82f6; color: white; }
.message-text { line-height: 1.6; word-break: break-word; }
.citations { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e2e8f0; }
.citations-title { font-size: 12px; color: #64748b; margin-bottom: 8px; }
.citation-item { background: #f8fafc; padding: 8px 12px; border-radius: 6px; margin-bottom: 6px; font-size: 13px; }
.citation-source { color: #3b82f6; font-weight: 500; display: block; margin-bottom: 4px; }
.citation-content { color: #64748b; display: block; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.message-time { font-size: 11px; color: #94a3b8; margin-top: 6px; }
.loading-dots { display: flex; gap: 4px; padding: 4px 0; }
.loading-dots span { width: 8px; height: 8px; background: #94a3b8; border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
.loading-text { margin-top: 8px; color: #64748b; font-size: 13px; }
.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1); } }
.chat-input-area { padding: 16px 24px; background: white; border-top: 1px solid #e2e8f0; }
.input-wrapper { display: flex; gap: 12px; }
.input-field { flex: 1; padding: 12px 16px; border: 2px solid #e2e8f0; border-radius: 8px; font-size: 16px; transition: border-color 0.2s; }
.input-field:focus { outline: none; border-color: #3b82f6; }
.input-field:disabled { background: #f1f5f9; cursor: not-allowed; }
.send-btn { padding: 12px 24px; background: #3b82f6; color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 500; cursor: pointer; transition: all 0.2s; }
.send-btn:hover:not(:disabled) { background: #2563eb; }
.send-btn:disabled { background: #94a3b8; cursor: not-allowed; }
</style>
