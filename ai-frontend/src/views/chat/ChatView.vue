<template>
  <div class="chat-container">
    <!-- 左侧配置面板 -->
    <div class="chat-sidebar">
      <div class="sidebar-section">
        <div class="section-title">会话配置</div>
        <el-select v-model="selectedPromptId" placeholder="选择提示词模板" style="width: 100%" @change="onConfigChange">
          <el-option
            v-for="tpl in promptTemplates"
            :key="tpl.id"
            :label="`${tpl.scene} (v${tpl.version})`"
            :value="tpl.id"
          >
            <span>{{ tpl.scene }}</span>
            <el-tag size="small" style="margin-left: 8px" :type="tpl.status === 'ACTIVE' ? 'success' : 'info'">
              v{{ tpl.version }}
            </el-tag>
          </el-option>
        </el-select>
        <div style="margin-top: 12px">
          <span class="label-text">限定文档（可选）</span>
          <el-select v-model="selectedDocId" placeholder="全局搜索（不限文档）" style="width: 100%" clearable @change="onConfigChange">
            <el-option
              v-for="doc in documents"
              :key="doc.id"
              :label="doc.fileName"
              :value="doc.id"
            >
              <span>{{ doc.fileName }}</span>
              <el-tag size="small" style="margin-left: 8px" :type="doc.status === 'COMPLETED' ? 'success' : 'warning'">
                {{ doc.status === 'COMPLETED' ? '就绪' : '处理中' }}
              </el-tag>
            </el-option>
          </el-select>
        </div>
      </div>

      <div class="sidebar-section">
        <div class="section-title">会话</div>
        <el-button style="width: 100%" @click="newSession">
            <el-icon><Plus /></el-icon> 新建会话
          </el-button>
        <div class="session-list" v-if="sessions.length > 0">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === sessionId }"
            @click="switchSession(s.id)"
          >
            <div class="session-info">
              <div class="session-name">{{ s.name }}</div>
              <div class="session-time">{{ s.time }}</div>
            </div>
            <el-icon class="session-delete" :size="14" @click.stop="handleDeleteSession(s.id)">
              <Delete />
            </el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧对话区 -->
    <div class="chat-main">
      <!-- 对话消息区 -->
      <div class="chat-messages" ref="messagesContainer">
        <el-empty v-if="messages.length === 0" description="选择提示词模板后即可开始对话" :image-size="120" />

        <div v-for="(msg, idx) in messages" :key="idx" class="message-row" :class="msg.role">
          <div class="message-avatar">
            <el-avatar v-if="msg.role === 'user'" :size="36" :icon="UserFilled" />
            <el-avatar v-else :size="36" style="background: #409eff">
              <el-icon :size="20"><Cpu /></el-icon>
            </el-avatar>
          </div>
          <div class="message-body">
            <div class="message-meta">
              <span class="message-sender">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</span>
              <span class="message-time" v-if="msg.time">{{ msg.time }}</span>
              <span class="message-latency" v-if="msg.latencyMs">耗时 {{ msg.latencyMs }}ms</span>
            </div>
            <div class="message-text">{{ msg.content }}</div>

            <!-- 来源引用（仅AI回复） -->
            <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length > 0" class="message-sources">
              <el-collapse>
                <el-collapse-item :title="`引用来源 (${msg.sources.length})`">
                  <div v-for="(src, si) in msg.sources" :key="si" class="source-item">
                    <el-tag size="small" type="info">来源 {{ si + 1 }}</el-tag>
                    <p>{{ src }}</p>
                  </div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>
        </div>

        <!-- 思考中状态：thinking 为 true 且最后一条消息没有实质内容时显示 -->
        <div v-if="thinking && (!messages.length || messages[messages.length-1]?.role === 'user' || !messages[messages.length-1]?.content)" class="message-row assistant">
          <div class="message-avatar">
            <el-avatar :size="36" style="background: #409eff">
              <el-icon :size="20"><Cpu /></el-icon>
            </el-avatar>
          </div>
          <div class="message-body">
            <div class="thinking-dots">
              <span></span><span></span><span></span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="输入问题，按 Enter 发送，Shift+Enter 换行"
          :disabled="!canSend"
          @keydown.enter.exact="handleSend"
          resize="none"
        />
        <div class="input-actions">
          <span class="input-hint" v-if="!selectedPromptId">请先选择提示词模板</span>
          <span class="input-hint" v-else-if="thinking">AI 正在思考中...</span>
          <el-button
            type="primary"
            :disabled="!canSend || !inputText.trim()"
            :loading="thinking"
            @click="handleSend"
          >
            <el-icon><Promotion /></el-icon> 发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { Delete, Plus, Promotion, UserFilled } from '@element-plus/icons-vue'
import { askQuestionStream, deleteChatHistory, getChatHistory } from '../../api/qa'
import { pagePromptTemplates } from '../../api/promptTemplate'
import { pageDocuments } from '../../api/document'

// ---- 配置数据 ----
const promptTemplates = ref([])
const documents = ref([])
const selectedPromptId = ref(null)
const selectedDocId = ref(null)

// ---- 会话数据 ----
const sessionId = ref(initSessionId())
const messages = ref([])
const inputText = ref('')
const thinking = ref(false)
const cancelStream = ref(null)
const messagesContainer = ref(null)

// ---- 会话列表（内存管理，存储于 localStorage） ----
const sessions = ref(loadSessions())

const canSend = computed(() => selectedPromptId.value && !thinking.value)

function initSessionId() {
  const stored = localStorage.getItem('qa_current_session')
  return stored || generateUUID()
}

function generateUUID() {
  return 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 10)
}

function loadSessions() {
  try {
    return JSON.parse(localStorage.getItem('qa_sessions') || '[]')
  } catch {
    return []
  }
}

function saveSessions() {
  localStorage.setItem('qa_sessions', JSON.stringify(sessions.value))
}

function persistSessionId() {
  localStorage.setItem('qa_current_session', sessionId.value)
}

// ---- 生命周期 ----
onMounted(async () => {
  await loadConfigData()
  await loadChatHistory()
})

// ---- 配置加载 ----
async function loadConfigData() {
  try {
    const [tplRes, docRes] = await Promise.all([
      pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' }),
      pageDocuments({ page: 1, size: 100 })
    ])
    promptTemplates.value = tplRes.records || []
    documents.value = (docRes.records || []).filter(d => d.status === 'COMPLETED')
  } catch {
    // 静默处理
  }
}

// ---- 对话历史 ----
async function loadChatHistory() {
  if (!sessionId.value) return
  try {
    const logs = await getChatHistory(sessionId.value)
    messages.value = (logs || []).flatMap(log => [
      {
        role: 'user',
        content: log.userQuestion,
        time: log.createTime
      },
      {
        role: 'assistant',
        content: log.modelResponse,
        time: log.createTime,
        latencyMs: log.latencyMs,
        sources: parseSources(log.retrievedChunks)
      }
    ])
    await scrollToBottom()
  } catch {
    messages.value = []
  }
}

function parseSources(chunks) {
  if (!chunks) return []
  try {
    const arr = JSON.parse(chunks)
    return Array.isArray(arr) ? arr : [chunks]
  } catch {
    return [chunks]
  }
}

// ---- 发送消息（流式） ----
async function handleSend() {
  const text = inputText.value.trim()
  if (!text || !canSend.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: text, time: formatTime(new Date()) })
  inputText.value = ''
  await scrollToBottom()

  // 添加占位的 AI 消息（流式填充）
  const assistantMsg = { role: 'assistant', content: '', time: formatTime(new Date()), latencyMs: 0, sources: [] }
  messages.value.push(assistantMsg)

  thinking.value = true
  cancelStream.value = askQuestionStream({
    question: text,
    promptTemplateId: selectedPromptId.value,
    documentId: selectedDocId.value || null,
    sessionId: sessionId.value,
    onMeta(meta) {
      assistantMsg.sessionId = meta.sessionId
      assistantMsg.sources = meta.sources || []
      assistantMsg.retrievalMs = meta.retrievalMs
    },
    onToken(token) {
      assistantMsg.content += token
      scrollToBottom()
    },
    onDone(done) {
      assistantMsg.latencyMs = done.latencyMs
      thinking.value = false
      cancelStream.value = null
      updateSessionMeta(text)
      scrollToBottom()
    },
    onError(err) {
      assistantMsg.content = assistantMsg.content || '抱歉，请求失败：' + (err.message || '网络错误')
      assistantMsg.isError = true
      thinking.value = false
      cancelStream.value = null
      scrollToBottom()
    }
  })
}

// ---- 会话管理 ----
function newSession() {
  sessionId.value = generateUUID()
  messages.value = []
  persistSessionId()
  saveSessions()
}

function switchSession(id) {
  sessionId.value = id
  persistSessionId()
  loadChatHistory()
}

async function handleDeleteSession(id) {
  try {
    await deleteChatHistory(id)
  } catch {
    // 即使服务端删除失败，仍清理本地记录
  }
  sessions.value = sessions.value.filter(s => s.id !== id)
  saveSessions()
  if (sessionId.value === id) {
    newSession()
  }
}

function updateSessionMeta(lastQuestion) {
  const existing = sessions.value.find(s => s.id === sessionId.value)
  const name = lastQuestion.length > 20 ? lastQuestion.substring(0, 20) + '...' : lastQuestion
  if (existing) {
    existing.name = name
    existing.time = formatTime(new Date())
  } else {
    sessions.value.unshift({
      id: sessionId.value,
      name,
      time: formatTime(new Date())
    })
  }
  saveSessions()
}

function onConfigChange() {
  // 配置更改时记录，不影响当前对话
}

// ---- 工具函数 ----
function scrollToBottom() {
  return nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function formatTime(date) {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: calc(100vh - 120px);
  gap: 0;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

/* ---- 左侧栏 ---- */
.chat-sidebar {
  width: 260px;
  background: #fafafa;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-section {
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.label-text {
  font-size: 12px;
  color: #909399;
  display: block;
  margin-bottom: 4px;
}

.session-list {
  margin-top: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.session-item {
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 4px;
  transition: background 0.2s;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-item:hover {
  background: #ecf5ff;
}

.session-item.active {
  background: #409eff;
}

.session-item.active .session-name,
.session-item.active .session-time {
  color: #fff;
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

.session-delete {
  color: #c0c4cc;
  flex-shrink: 0;
  margin-left: 6px;
  opacity: 0;
  transition: opacity 0.2s, color 0.2s;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.session-delete:hover {
  color: #f56c6c;
}

.session-item.active .session-delete {
  color: rgba(255, 255, 255, 0.7);
}

.session-item.active .session-delete:hover {
  color: #fff;
}

/* ---- 对话区 ---- */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

/* ---- 消息气泡 ---- */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-body {
  max-width: 70%;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.message-row.user .message-meta {
  flex-direction: row-reverse;
}

.message-sender {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.message-time,
.message-latency {
  font-size: 11px;
  color: #c0c4cc;
}

.message-text {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.user .message-text {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.message-row.assistant .message-text {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 2px;
}

/* 来源引用 */
.message-sources {
  margin-top: 8px;
  max-width: 90%;
}

.source-item {
  margin-bottom: 8px;
}

.source-item p {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  max-height: 200px;
  overflow-y: auto;
}

/* ---- 思考动画 ---- */
.thinking-dots {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
}

.thinking-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  animation: dot-pulse 1.4s infinite ease-in-out both;
}

.thinking-dots span:nth-child(1) { animation-delay: -0.32s; }
.thinking-dots span:nth-child(2) { animation-delay: -0.16s; }
.thinking-dots span:nth-child(3) { animation-delay: 0s; }

@keyframes dot-pulse {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* ---- 输入区 ---- */
.chat-input-area {
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #ebeef5;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}

.input-hint {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
