import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' }
})

export const sendChatMessage = (query, conversationId, userId, onMessage, onError) => {
  return new Promise((resolve, reject) => {
    const url = `${API_BASE_URL}/chat/send`

    const processDataLine = (line) => {
      if (!line.startsWith('data:')) return false

      const dataStr = line.slice(5).trimStart()
      if (dataStr === '[DONE]') {
        resolve({ type: 'complete' })
        return true
      }

      try {
        const data = JSON.parse(dataStr)
        if (onMessage) onMessage(data)
        if (data.type === 'end') {
          resolve(data)
          return true
        }
      } catch (e) {
        console.warn('Failed to parse SSE chunk:', dataStr, e)
      }

      return false
    }
    
    fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        query,
        conversationId: conversationId || '',
        userId: userId || 'default-user',
        files: []
      })
    })
    .then(response => {
      if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
      if (!response.body) throw new Error('ReadableStream is not available in the current browser')

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      
      const readStream = () => {
        reader.read().then(({ done, value }) => {
          if (done) {
            buffer += decoder.decode()
            const finalLine = buffer.trim()
            if (finalLine) processDataLine(finalLine)
            resolve({ type: 'complete' })
            return
          }

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (processDataLine(line.trim())) {
              return
            }
          }

          readStream()
        }).catch(error => { if (onError) onError(error); reject(error) })
      }

      readStream()
    })
    .catch(error => { if (onError) onError(error); reject(error) })
  })
}

export const getConversations = async (userId) => {
  try {
    const response = await apiClient.get('/chat/conversations', { params: { userId } })
    return response.data
  } catch (error) {
    console.error('Get conversations error:', error)
    return { success: false, error: error.message }
  }
}

export const getMessages = async (conversationId) => {
  try {
    const response = await apiClient.get(`/chat/history/${conversationId}`)
    return response.data
  } catch (error) {
    console.error('Get messages error:', error)
    return { success: false, error: error.message }
  }
}

export const createConversation = async (userId, title) => {
  try {
    const response = await apiClient.post('/chat/new', { userId, title })
    return response.data
  } catch (error) {
    console.error('Create conversation error:', error)
    return { success: false, error: error.message }
  }
}

export const checkHealth = async () => {
  try {
    const response = await apiClient.get('/chat/health')
    return response.data
  } catch (error) {
    console.error('Health check error:', error)
    return { status: 'error', error: error.message }
  }
}

export default { sendChatMessage, getConversations, getMessages, createConversation, checkHealth }
