<template>
  <div class="train-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>模型微调</span>
          <div class="header-actions">
            <el-button @click="loadTasks" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 新建微调任务
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="tasks" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="modelBase" label="基座模型" width="180" show-overflow-tooltip />
        <el-table-column prop="datasetName" label="数据集" width="150" />
        <el-table-column label="LoRA参数" width="130">
          <template #default="{ row }">
            <span style="font-size:12px;color:#909399">
              r={{ row.loraRank }} α={{ row.loraAlpha }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="训练进度" width="150">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
              :stroke-width="14"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 'TRAINING'"
              size="small" type="warning"
              :loading="pollingIds.has(row.id)"
              @click="handlePoll(row.id)"
            >刷新</el-button>
            <el-popconfirm title="确认删除？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tasks.length === 0"
        description="暂无微调任务，请创建QLoRA微调任务" />

      <el-pagination
        v-if="total > 0"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadTasks"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- ========== 创建任务对话框 ========== -->
    <el-dialog v-model="showCreate" title="新建QLoRA微调任务" width="550px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" placeholder="如：客服FAQ问答微调" />
        </el-form-item>
        <el-form-item label="基座模型" required>
          <el-select v-model="form.modelBase" placeholder="选择基座模型" style="width:100%">
            <el-option label="Qwen2-7B-Instruct（推荐）" value="Qwen2-7B-Instruct" />
            <el-option label="Qwen2-1.5B-Instruct（轻量）" value="Qwen2-1.5B-Instruct" />
            <el-option label="Llama-3-8B-Instruct" value="Llama-3-8B-Instruct" />
            <el-option label="Mistral-7B-Instruct" value="Mistral-7B-Instruct" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据集" required>
          <el-input v-model="form.datasetName" placeholder="数据集名称" />
        </el-form-item>
        <el-form-item label="数据集路径">
          <el-input v-model="form.datasetPath" placeholder="本地JSON文件路径（可选）" />
        </el-form-item>

        <el-divider content-position="left">LoRA超参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="LoRA Rank">
              <el-input-number v-model="form.loraRank" :min="8" :max="256" :step="8" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="LoRA Alpha">
              <el-input-number v-model="form.loraAlpha" :min="4" :max="128" :step="4" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="学习率">
              <el-input-number v-model="form.learningRate" :min="1e-6" :max="1e-3" :step="1e-5"
                :precision="6" style="width:130px" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="训练轮数">
              <el-input-number v-model="form.numEpochs" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="BatchSize">
              <el-input-number v-model="form.batchSize" :min="1" :max="32" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert type="info" :closable="false" show-icon style="margin-top: 8px">
          <template #title>
            任务创建后将自动调用Python微调服务。如Python服务不可用，任务保持PENDING状态。
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating"
          :disabled="!form.taskName || !form.modelBase || !form.datasetName">
          创建并启动
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 详情对话框 ========== -->
    <el-dialog v-model="showDetail" title="微调任务详情" width="600px">
      <template v-if="currentTask">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名称">{{ currentTask.taskName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(currentTask.status)" size="small">{{ statusLabel(currentTask.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="基座模型">{{ currentTask.modelBase }}</el-descriptions-item>
          <el-descriptions-item label="数据集">{{ currentTask.datasetName }}</el-descriptions-item>
          <el-descriptions-item label="LoRA Rank">{{ currentTask.loraRank }}</el-descriptions-item>
          <el-descriptions-item label="LoRA Alpha">{{ currentTask.loraAlpha }}</el-descriptions-item>
          <el-descriptions-item label="学习率">{{ currentTask.learningRate }}</el-descriptions-item>
          <el-descriptions-item label="训练轮数">{{ currentTask.numEpochs }}</el-descriptions-item>
          <el-descriptions-item label="Batch Size">{{ currentTask.batchSize }}</el-descriptions-item>
          <el-descriptions-item label="进度">{{ currentTask.progress || 0 }}%</el-descriptions-item>
          <el-descriptions-item v-if="currentTask.loraWeightPath" label="权重路径" :span="2">
            {{ currentTask.loraWeightPath }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTask.metrics" label="训练指标" :span="2">
            <pre style="margin:0;font-size:12px;max-height:150px;overflow:auto">{{ formatMetrics(currentTask.metrics) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="currentTask.errorMsg" label="错误信息" :span="2">
            <span style="color:#f56c6c">{{ currentTask.errorMsg }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ currentTask.createTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createTask, listTasks, getTask, pollStatus, deleteTask } from '../../api/train'

const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })

// 创建对话框
const showCreate = ref(false)
const creating = ref(false)
const form = reactive({
  taskName: '', modelBase: 'Qwen2-7B-Instruct', datasetName: '', datasetPath: '',
  loraRank: 64, loraAlpha: 16, learningRate: 0.0002, numEpochs: 3, batchSize: 4
})

// 详情对话框
const showDetail = ref(false)
const currentTask = ref(null)

// 自动轮询
const pollingIds = ref(new Set())
let pollingTimer = null

onMounted(() => {
  loadTasks()
  startAutoPolling()
})

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer)
})

async function loadTasks() {
  loading.value = true
  try {
    const data = await listTasks(query)
    tasks.value = data.records || []
    total.value = data.total || 0
  } catch { tasks.value = [] } finally { loading.value = false }
}

function openCreateDialog() {
  Object.assign(form, {
    taskName: '', modelBase: 'Qwen2-7B-Instruct', datasetName: '', datasetPath: '',
    loraRank: 64, loraAlpha: 16, learningRate: 0.0002, numEpochs: 3, batchSize: 4
  })
  showCreate.value = true
}

async function handleCreate() {
  if (!form.taskName || !form.modelBase || !form.datasetName) return
  creating.value = true
  try {
    await createTask(form)
    ElMessage.success('微调任务已创建并启动')
    showCreate.value = false
    await loadTasks()
  } catch { ElMessage.error('创建失败') } finally { creating.value = false }
}

async function viewDetail(id) {
  try {
    currentTask.value = await getTask(id)
    showDetail.value = true
  } catch { ElMessage.error('获取详情失败') }
}

async function handlePoll(id) {
  pollingIds.value.add(id)
  try {
    await pollStatus(id)
    await loadTasks()
  } catch { ElMessage.error('刷新失败') } finally {
    pollingIds.value.delete(id)
  }
}

async function handleDelete(id) {
  try {
    await deleteTask(id)
    ElMessage.success('删除成功')
    await loadTasks()
  } catch { ElMessage.error('删除失败') }
}

// 自动轮询正在训练的任务（每10秒）
function startAutoPolling() {
  pollingTimer = setInterval(async () => {
    const training = tasks.value.filter(t => t.status === 'TRAINING')
    if (training.length === 0) return
    for (const t of training) {
      try { await pollStatus(t.id) } catch { /* ignore */ }
    }
    await loadTasks()
  }, 10000)
}

function formatMetrics(metricsStr) {
  try { return JSON.stringify(JSON.parse(metricsStr), null, 2) } catch { return metricsStr }
}

function statusTag(s) {
  return { PENDING: 'info', TRAINING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[s] || 'info'
}

function statusLabel(s) {
  return { PENDING: '待启动', TRAINING: '训练中', COMPLETED: '已完成', FAILED: '失败' }[s] || s
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-actions {
  display: flex;
  gap: 8px;
}
</style>
