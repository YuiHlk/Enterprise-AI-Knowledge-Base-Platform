<template>
  <div class="ablation-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>消融实验</span>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon> 新建实验
          </el-button>
        </div>
      </template>

      <!-- 实验列表 -->
      <el-table :data="experiments" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="experimentName" label="实验名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="testSetName" label="评测集" width="150" />
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress
              :percentage="row.totalTasks > 0 ? Math.round(row.completedTasks / row.totalTasks * 100) : 0"
              :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
              :stroke-width="14"
            />
          </template>
        </el-table-column>
        <el-table-column label="实验组" width="100" align="center">
          <template #default="{ row }">{{ row.completedTasks }} / {{ row.totalTasks }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDetail(row.id)">对比报告</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="success"
              @click="handleRun(row.id)"
            >运行</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && experiments.length === 0"
        description="暂无消融实验，请创建实验自动对比不同RAG参数的效果" />
    </el-card>

    <!-- ========== 创建实验对话框 ========== -->
    <el-dialog v-model="showCreate" title="新建消融实验" width="650px" top="5vh">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="实验名称" required>
          <el-input v-model="createForm.name" placeholder="如：分块大小与TopK消融实验" />
        </el-form-item>
        <el-form-item label="评测集" required>
          <el-select v-model="createForm.testSetName" placeholder="选择评测集" style="width: 100%">
            <el-option v-for="s in testSetNames" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">基准配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="提示词模板">
              <el-select v-model="createForm.baseConfig.promptTemplateId" placeholder="选择模板" style="width: 100%">
                <el-option v-for="tpl in promptTemplates" :key="tpl.id"
                  :label="`${tpl.scene} (v${tpl.version})`" :value="tpl.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="分块大小">
              <el-input-number v-model="createForm.baseConfig.chunkSize" :min="128" :max="4096" :step="128" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="TopK">
              <el-input-number v-model="createForm.baseConfig.topK" :min="1" :max="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">
          变量配置
          <el-tooltip content="每个变量的所有取值将做笛卡尔积组合，组合数 = 各变量取值数的乘积。组合数不能超过50。">
            <el-icon style="margin-left: 4px; cursor: help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-divider>

        <div v-for="(v, idx) in createForm.variables" :key="idx" class="variable-row">
          <el-input v-model="v.name" placeholder="变量名" style="width: 130px" />
          <el-input v-model="v.valuesStr" placeholder="取值，逗号分隔（如 256,512,1024）" style="flex: 1; margin: 0 8px" />
          <el-button :icon="Delete" circle size="small" @click="removeVariable(idx)"
            :disabled="createForm.variables.length <= 1" />
        </div>
        <el-button type="primary" text @click="addVariable" style="margin-top: 8px">
          <el-icon><Plus /></el-icon> 添加变量
        </el-button>

        <el-alert type="info" :closable="false" show-icon style="margin-top: 12px">
          <template #title>
            实验将自动对每个参数组合运行完整评测流程（检索→生成→LLM评分），完成后生成对比报告
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating"
          :disabled="!canCreate">创建实验</el-button>
      </template>
    </el-dialog>

    <!-- ========== 对比报告对话框 ========== -->
    <el-dialog v-model="showReport" title="消融实验对比报告" width="950px" top="3vh">
      <template v-if="reportData && reportData.groups">
        <!-- 最优指标卡片 -->
        <el-row :gutter="12" style="margin-bottom: 16px">
          <el-col :span="4" v-for="b in bestCards" :key="b.label">
            <el-card shadow="hover" class="best-card">
              <div class="best-label">{{ b.label }}</div>
              <div class="best-value" :style="{ color: b.color }">{{ b.value }}</div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 实验组对比表格 -->
        <el-table :data="reportData.groups" border stripe max-height="450">
          <el-table-column prop="label" label="实验组" min-width="200" />
          <el-table-column label="完成/总数" width="100" align="center">
            <template #default="{ row }">{{ row.completed }} / {{ row.total }}</template>
          </el-table-column>
          <el-table-column label="相关性 ↑" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.avgAnswerRelevance, 5), fontWeight: bestFor('bestRelevance', row.label) ? '700' : '' }">
                {{ row.avgAnswerRelevance }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="忠实度 ↑" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.avgContextFaithfulness, 5), fontWeight: bestFor('bestFaithfulness', row.label) ? '700' : '' }">
                {{ row.avgContextFaithfulness }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="幻觉分 ↓" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: hallucinationColor(row.avgHallucinationScore), fontWeight: bestFor('bestHallucination', row.label) ? '700' : '' }">
                {{ row.avgHallucinationScore }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="召回率 ↑" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.avgContextRecall, 1), fontWeight: bestFor('bestRecall', row.label) ? '700' : '' }">
                {{ row.avgContextRecall }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="精准度 ↑" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: scoreColor(row.avgRetrievalPrecision, 1), fontWeight: bestFor('bestPrecision', row.label) ? '700' : '' }">
                {{ row.avgRetrievalPrecision }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top: 8px; font-size: 12px; color: #909399;">
          注：粗体表示该指标的最优组。↑ 越高越好，↓ 越低越好。
        </div>
      </template>
      <el-empty v-else-if="reportData" description="实验尚未产生对比数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, QuestionFilled } from '@element-plus/icons-vue'
import {
  createExperiment, listExperiments, getExperiment, runExperiment, deleteExperiment
} from '../../api/ablation'
import { listTestSets } from '../../api/evaluation'
import { pagePromptTemplates } from '../../api/promptTemplate'

// ---- 实验列表 ----
const loading = ref(false)
const experiments = ref([])

// ---- 创建对话框 ----
const showCreate = ref(false)
const creating = ref(false)
const testSetNames = ref([])
const promptTemplates = ref([])

const createForm = reactive({
  name: '',
  testSetName: '',
  baseConfig: { promptTemplateId: null, chunkSize: 512, topK: 5 },
  variables: [{ name: '', valuesStr: '' }]
})

const canCreate = computed(() =>
  createForm.name && createForm.testSetName && createForm.baseConfig.promptTemplateId
)

// ---- 报告对话框 ----
const showReport = ref(false)
const reportData = ref(null)

onMounted(async () => {
  await loadExperiments()
  await loadTestSetNames()
  await loadPromptTemplates()
})

async function loadExperiments() {
  loading.value = true
  try { experiments.value = await listExperiments() || [] } catch { experiments.value = [] } finally { loading.value = false }
}

async function loadTestSetNames() {
  try { testSetNames.value = await listTestSets() || [] } catch { testSetNames.value = [] }
}

async function loadPromptTemplates() {
  try {
    const res = await pagePromptTemplates({ page: 1, size: 100, status: 'ACTIVE' })
    promptTemplates.value = res.records || []
  } catch { promptTemplates.value = [] }
}

// ---- 变量管理 ----
function addVariable() {
  createForm.variables.push({ name: '', valuesStr: '' })
}

function removeVariable(idx) {
  if (createForm.variables.length > 1) createForm.variables.splice(idx, 1)
}

function openCreateDialog() {
  // 重置表单
  createForm.name = ''
  createForm.testSetName = ''
  createForm.baseConfig = { promptTemplateId: null, chunkSize: 512, topK: 5 }
  createForm.variables = [{ name: '', valuesStr: '' }]
  showCreate.value = true
}

// ---- 创建实验 ----
async function handleCreate() {
  if (!canCreate.value) return
  creating.value = true
  try {
    const variableConfigs = createForm.variables
      .filter(v => v.name.trim() && v.valuesStr.trim())
      .map(v => ({
        variable: v.name.trim(),
        values: v.valuesStr.split(',').map(s => {
          const trimmed = s.trim()
          // 尝试解析为数字
          const num = Number(trimmed)
          return isNaN(num) ? trimmed : num
        })
      }))

    await createExperiment(
      createForm.name,
      createForm.testSetName,
      createForm.baseConfig,
      variableConfigs
    )
    ElMessage.success('实验创建成功')
    showCreate.value = false
    await loadExperiments()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

// ---- 运行/删除 ----
async function handleRun(id) {
  try {
    await runExperiment(id)
    ElMessage.success('实验已启动，后台运行中...')
    await loadExperiments()
  } catch { ElMessage.error('启动失败') }
}

async function handleDelete(id) {
  try {
    await deleteExperiment(id)
    ElMessage.success('删除成功')
    await loadExperiments()
  } catch { ElMessage.error('删除失败') }
}

// ---- 查看报告 ----
async function viewDetail(id) {
  try {
    reportData.value = await getExperiment(id)
    showReport.value = true
  } catch { ElMessage.error('获取报告失败') }
}

// ---- 最佳指标卡片 ----
const bestCards = computed(() => {
  if (!reportData.value) return []
  return [
    { label: '实验组数', value: reportData.value.totalGroups || 0, color: '#409eff' },
    { label: '最佳相关性', value: reportData.value.bestRelevance || '-', color: '#67c23a' },
    { label: '最佳忠实度', value: reportData.value.bestFaithfulness || '-', color: '#e6a23c' },
    { label: '最低幻觉', value: reportData.value.bestHallucination || '-', color: '#f56c6c' },
    { label: '最佳召回', value: reportData.value.bestRecall || '-', color: '#409eff' },
    { label: '最佳精准', value: reportData.value.bestPrecision || '-', color: '#909399' }
  ]
})

function bestFor(key, label) {
  return reportData.value && reportData.value[key] === label
}

// ---- 着色 ----
function scoreColor(val, max) {
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

function statusTag(s) {
  return { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待执行', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败' }[s] || s
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.variable-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.best-card {
  text-align: center;
}
.best-value {
  font-size: 20px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.best-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
</style>
