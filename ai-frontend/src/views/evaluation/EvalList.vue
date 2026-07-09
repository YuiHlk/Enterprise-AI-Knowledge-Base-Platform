<template>
  <div class="eval-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ========== Tab 1: 评测问题集管理 ========== -->
      <el-tab-pane label="评测问题集" name="testSets">
        <div class="tab-content">
          <div class="toolbar">
            <el-button type="primary" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon> 创建问题集
            </el-button>
            <el-button @click="loadTestSets">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="testSets" v-loading="setsLoading" border stripe>
            <el-table-column prop="setName" label="评测集名称" min-width="200" />
            <el-table-column label="问题数量" width="100" align="center">
              <template #default="{ row }">{{ row.questionCount }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="viewSetDetail(row.setName)">查看问题</el-button>
                <el-button size="small" type="primary" @click="useForEval(row.setName)">评测</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!setsLoading && testSets.length === 0"
            description="暂无评测集，请创建标准评测问题" />

          <!-- 评测集详情弹窗 -->
          <el-dialog v-model="showDetailDialog" :title="'评测集: ' + currentSetName" width="750px">
            <el-table :data="currentQuestions" border stripe max-height="400">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="category" label="分类" width="100" />
              <el-table-column label="难度" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="difficultyTag(row.difficulty)" size="small">{{ row.difficulty }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ row }">
                  <el-popconfirm title="确认删除？" @confirm="handleDeleteQuestion(row.id)">
                    <template #reference>
                      <el-button size="small" type="danger" :icon="Delete" circle />
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-dialog>

          <!-- 创建问题集弹窗 -->
          <el-dialog v-model="showCreateDialog" title="创建评测问题集" width="680px">
            <el-form :model="createForm" label-width="100px">
              <el-form-item label="评测集名称" required>
                <el-input v-model="createForm.setName" placeholder="如：客服FAQ评测集" />
              </el-form-item>
              <el-form-item label="问题列表">
                <div v-for="(q, idx) in createForm.questions" :key="idx" class="question-row">
                  <el-input v-model="q.question" placeholder="问题" style="flex: 2" />
                  <el-input v-model="q.referenceAnswer" placeholder="参考答案（可选）" style="flex: 2; margin: 0 4px" />
                  <el-input v-model="q.category" placeholder="分类" style="width: 90px" />
                  <el-select v-model="q.difficulty" style="width: 90px; margin: 0 4px">
                    <el-option label="简单" value="EASY" />
                    <el-option label="中等" value="MEDIUM" />
                    <el-option label="困难" value="HARD" />
                  </el-select>
                  <el-button :icon="Delete" circle size="small" @click="removeQuestion(idx)" />
                </div>
                <el-button type="primary" text @click="addQuestionRow">
                  <el-icon><Plus /></el-icon> 添加问题
                </el-button>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showCreateDialog = false">取消</el-button>
              <el-button type="primary" @click="handleCreateSet" :loading="creating"
                :disabled="!createForm.setName || createForm.questions.length === 0">
                创建 ({{ createForm.questions.length }}题)
              </el-button>
            </template>
          </el-dialog>
        </div>
      </el-tab-pane>

      <!-- ========== Tab 2: 评测任务 ========== -->
      <el-tab-pane label="评测任务" name="tasks">
        <div class="tab-content">
          <el-card shadow="never" class="run-card">
            <template #header><span>启动新评测</span></template>
            <el-form :inline="true" :model="runForm">
              <el-form-item label="评测集">
                <el-select v-model="runForm.setName" placeholder="选择评测集" style="width: 200px">
                  <el-option v-for="s in testSetNames" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="提示词模板">
                <el-select v-model="runForm.promptTemplateId" placeholder="选择模板" style="width: 220px">
                  <el-option
                    v-for="tpl in promptTemplates"
                    :key="tpl.id"
                    :label="`${tpl.scene} (v${tpl.version})`"
                    :value="tpl.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="TopK">
                <el-input-number v-model="runForm.topK" :min="1" :max="20" style="width: 90px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleRunEval" :loading="running"
                  :disabled="!runForm.setName || !runForm.promptTemplateId">
                  启动评测
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <el-table :data="tasks" v-loading="tasksLoading" border stripe style="margin-top: 16px">
            <el-table-column prop="taskId" label="任务ID" width="120" />
            <el-table-column label="进度" width="130">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.total > 0 ? Math.round((row.completed + row.failed) / row.total * 100) : 0"
                  :status="row.pending === 0 ? 'success' : ''"
                  :stroke-width="16"
                />
              </template>
            </el-table-column>
            <el-table-column label="完成/失败/总数" width="160">
              <template #default="{ row }">
                <span style="color:#67c23a">{{ row.completed }}</span>
                <span> / </span>
                <span style="color:#f56c6c">{{ row.failed }}</span>
                <span> / </span>
                <span>{{ row.total }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="latestTime" label="最新时间" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="viewTaskDetail(row.taskId)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!tasksLoading && tasks.length === 0"
            description="暂无评测任务" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 任务详情弹窗 ========== -->
    <el-dialog v-model="showTaskDetail" title="评测任务详情" width="950px" top="5vh">
      <template v-if="taskDetail">
        <el-row :gutter="12" style="margin-bottom: 16px">
          <el-col :span="4" v-for="m in taskMetrics" :key="m.label">
            <el-card shadow="hover" class="metric-card">
              <div class="metric-value" :style="{ color: m.color }">{{ m.value }}</div>
              <div class="metric-label">{{ m.label }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-table :data="taskDetail.records" border stripe max-height="400">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="精准度" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.retrievalPrecision) }">
                {{ row.retrievalPrecision != null ? row.retrievalPrecision.toFixed(2) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="召回率" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.contextRecall) }">
                {{ row.contextRecall != null ? row.contextRecall.toFixed(2) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="相关性" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.answerRelevance, 5) }">
                {{ row.answerRelevance != null ? row.answerRelevance.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="忠实度" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.contextFaithfulness, 5) }">
                {{ row.contextFaithfulness != null ? row.contextFaithfulness.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="幻觉分" width="85" align="center">
            <template #default="{ row }">
              <span :style="{ color: hallucinationColor(row.hallucinationScore) }">
                {{ row.hallucinationScore != null ? row.hallucinationScore.toFixed(1) : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="latencyMs" label="耗时" width="80" align="center">
            <template #default="{ row }">{{ row.latencyMs }}ms</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" size="small">
                {{ row.status === 'COMPLETED' ? '完成' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回答" min-width="250" show-overflow-tooltip>
            <template #default="{ row }">{{ row.modelResponse }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-popconfirm title="确认删除？" @confirm="handleDeleteRecord(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" :icon="Delete" circle />
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import {
  listTestSets, getQuestions, createQuestions, deleteQuestion,
  runEvaluation, listTasks, getTaskDetail, deleteRecord
} from '../../api/evaluation'
import { pagePromptTemplates } from '../../api/promptTemplate'

const activeTab = ref('testSets')

// ---- 评测集 ----
const setsLoading = ref(false)
const testSets = ref([])
const testSetNames = ref([])
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const currentSetName = ref('')
const currentQuestions = ref([])
const creating = ref(false)

const createForm = reactive({
  setName: '',
  questions: [makeEmptyQuestion()]
})

function makeEmptyQuestion() {
  return { question: '', referenceAnswer: '', category: '', difficulty: 'MEDIUM' }
}

function addQuestionRow() { createForm.questions.push(makeEmptyQuestion()) }
function removeQuestion(idx) { if (createForm.questions.length > 1) createForm.questions.splice(idx, 1) }

// ---- 评测任务 ----
const tasksLoading = ref(false)
const tasks = ref([])
const running = ref(false)
const promptTemplates = ref([])
const showTaskDetail = ref(false)
const taskDetail = ref(null)
const currentTaskId = ref('')

const runForm = reactive({ setName: '', promptTemplateId: null, topK: 5 })

onMounted(async () => {
  await loadTestSets()
  await loadTasks()
  await loadPromptTemplates()
})

async function loadTestSets() {
  setsLoading.value = true
  try {
    const names = await listTestSets()
    testSetNames.value = names || []
    const sets = []
    for (const name of (names || [])) {
      const questions = await getQuestions(name)
      sets.push({ setName: name, questionCount: (questions || []).length })
    }
    testSets.value = sets
  } catch { testSets.value = [] } finally { setsLoading.value = false }
}

async function loadTasks() {
  tasksLoading.value = true
  try { tasks.value = await listTasks() || [] } catch { tasks.value = [] } finally { tasksLoading.value = false }
}

async function loadPromptTemplates() {
  try {
    const res = await pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' })
    promptTemplates.value = res.records || []
  } catch { promptTemplates.value = [] }
}

async function viewSetDetail(setName) {
  currentSetName.value = setName
  try {
    currentQuestions.value = await getQuestions(setName) || []
    showDetailDialog.value = true
  } catch { ElMessage.error('获取问题列表失败') }
}

async function handleCreateSet() {
  if (!createForm.setName || createForm.questions.length === 0) return
  creating.value = true
  try {
    const payload = createForm.questions.map(q => ({
      setName: createForm.setName,
      question: q.question,
      referenceAnswer: q.referenceAnswer,
      category: q.category,
      difficulty: q.difficulty
    }))
    await createQuestions(payload)
    ElMessage.success(`评测集「${createForm.setName}」创建成功`)
    showCreateDialog.value = false
    createForm.setName = ''
    createForm.questions = [makeEmptyQuestion()]
    await loadTestSets()
  } catch { ElMessage.error('创建失败') } finally { creating.value = false }
}

async function handleDeleteQuestion(id) {
  try {
    await deleteQuestion(id)
    ElMessage.success('删除成功')
    viewSetDetail(currentSetName.value)
  } catch { ElMessage.error('删除失败') }
}

function useForEval(setName) {
  runForm.setName = setName
  activeTab.value = 'tasks'
}

async function handleRunEval() {
  if (!runForm.setName || !runForm.promptTemplateId) return
  running.value = true
  try {
    const res = await runEvaluation(runForm.setName, runForm.promptTemplateId, runForm.topK)
    ElMessage.success(`评测任务已启动 [${res.taskId}]，后台运行中...`)
    await loadTasks()
  } catch { ElMessage.error('启动评测失败') } finally { running.value = false }
}

async function viewTaskDetail(taskId) {
  try {
    currentTaskId.value = taskId
    taskDetail.value = await getTaskDetail(taskId)
    showTaskDetail.value = true
  } catch { ElMessage.error('获取失败') }
}

async function handleDeleteRecord(id) {
  try {
    await deleteRecord(id)
    ElMessage.success('删除成功')
    taskDetail.value = await getTaskDetail(currentTaskId.value)
  } catch { ElMessage.error('删除失败') }
}

const taskMetrics = computed(() => {
  if (!taskDetail.value) return []
  return [
    { label: '总计', value: taskDetail.value.total || 0, color: '#409eff' },
    { label: '相关性均分', value: taskDetail.value.avgAnswerRelevance || '-', color: '#67c23a' },
    { label: '忠实度均分', value: taskDetail.value.avgContextFaithfulness || '-', color: '#e6a23c' },
    { label: '幻觉均分↓', value: taskDetail.value.avgHallucinationScore || '-', color: '#f56c6c' },
    { label: '召回率', value: taskDetail.value.avgContextRecall || '-', color: '#409eff' },
    { label: '精准度', value: taskDetail.value.avgRetrievalPrecision || '-', color: '#909399' }
  ]
})

function scoreColor(val, max = 1) {
  if (val == null) return '#909399'
  const ratio = val / max
  if (ratio >= 0.7) return '#67c23a'
  if (ratio >= 0.4) return '#e6a23c'
  return '#f56c6c'
}

function hallucinationColor(val) {
  if (val == null) return '#909399'
  if (val <= 2) return '#67c23a'
  if (val <= 3) return '#e6a23c'
  return '#f56c6c'
}

function difficultyTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}
</script>

<style scoped>
.tab-content { min-height: 400px; }
.toolbar { margin-bottom: 16px; display: flex; gap: 8px; }
.run-card { background: #fafafa; }
.question-row { display: flex; align-items: center; gap: 4px; margin-bottom: 8px; }
.metric-card { text-align: center; }
.metric-value { font-size: 24px; font-weight: 700; }
.metric-label { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
