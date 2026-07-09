import request from './request'

// RAG问答（阻塞模式）
export function askQuestion(question, promptTemplateId, documentId, sessionId) {
  return request.post('/qa/ask', null, {
    params: { question, promptTemplateId, documentId, sessionId }
  })
}

// RAG流式问答（SSE推送，返回取消函数）
export function askQuestionStream({ question, promptTemplateId, documentId, sessionId, onMeta, onToken, onDone, onError }) {
  const params = new URLSearchParams({ question, promptTemplateId })
  if (documentId) params.append('documentId', documentId)
  if (sessionId) params.append('sessionId', sessionId)

  const controller = new AbortController()

  fetch(`/api/qa/ask/stream?${params.toString()}`, {
    method: 'POST',
    signal: controller.signal,
    headers: { 'Accept': 'text/event-stream' }
  }).then(async response => {
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // 解析 SSE 事件
      const parts = buffer.split('\n\n')
      buffer = parts.pop() || '' // 保留最后一个未完成的数据块

      for (const part of parts) {
        const lines = part.split('\n')
        let eventType = 'message'
        let data = ''
        for (const line of lines) {
          if (line.startsWith('event: ')) {
            eventType = line.slice(7).trim()
          } else if (line.startsWith('data: ')) {
            data = line.slice(6)
          }
        }
        if (!data) continue

        try {
          const parsed = JSON.parse(data)
          if (eventType === 'meta') {
            onMeta && onMeta(parsed)
          } else if (eventType === 'token') {
            onToken && onToken(data) // token 是纯字符串，非 JSON
          } else if (eventType === 'done') {
            onDone && onDone(parsed)
          } else if (eventType === 'error') {
            onError && onError(new Error(parsed || data))
          }
        } catch {
          // data 不是 JSON（token / error 事件中）
          if (eventType === 'token') {
            onToken && onToken(data)
          } else if (eventType === 'error') {
            onError && onError(new Error(data))
          }
        }
      }
    }
  }).catch(err => {
    if (err.name !== 'AbortError') {
      onError && onError(err)
    }
  })

  return () => controller.abort() // 返回取消函数
}

// 查询会话历史
export function getChatHistory(sessionId) {
  return request.get('/qa/chat-history', { params: { sessionId } })
}

// 删除会话历史
export function deleteChatHistory(sessionId) {
  return request.delete('/qa/delete-chat-history', { params: { sessionId } })
}
