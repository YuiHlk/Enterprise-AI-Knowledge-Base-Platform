<template>
  <div class="knowledge-base">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <div class="header-actions">
            <el-button @click="fetchData" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="showUploadDialog = true">
              <el-icon><Upload /></el-icon> 上传文档
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索筛选 -->
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="文件名">
          <el-input v-model="query.fileName" placeholder="输入文件名搜索" clearable @clear="fetchData" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable @change="fetchData">
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 文档表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="文件类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件大小" width="110">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="分块参数" width="140">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #909399;">
              大小: {{ row.chunkSize }} / 重叠: {{ row.chunkOverlap }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块数" width="80" align="center" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-popconfirm title="确认删除此文档？将同时删除关联的所有分块数据" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态（无筛选时显示引导） -->
      <el-empty v-if="!loading && tableData.length === 0 && !query.status && !query.fileName"
        description="暂无文档，请上传PDF、MD或TXT文件构建知识库" />

      <!-- 分页 -->
      <el-pagination
        v-if="total > 0"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, prev, pager, next, sizes"
        @current-change="fetchData"
        @size-change="fetchData"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="500px" :close-on-click-modal="false">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="() => uploadForm.file = null"
            accept=".pdf,.md,.txt"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / Markdown / TXT 格式文件</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分块大小">
              <el-input-number v-model="uploadForm.chunkSize" :min="128" :max="4096" :step="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分块重叠">
              <el-input-number v-model="uploadForm.chunkOverlap" :min="0" :max="1024" :step="16" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 8px">
          <template #title>
            文档上传后将异步进行PDF解析、文本分块和向量化处理，处理完成后状态变为"已完成"
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading" :disabled="!uploadForm.file">
          上传并处理
        </el-button>
      </template>
    </el-dialog>

    <!-- 文档详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="文档详情" width="500px">
      <el-descriptions v-if="currentDoc" :column="2" border>
        <el-descriptions-item label="ID">{{ currentDoc.id }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ currentDoc.fileName }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ currentDoc.fileType }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(currentDoc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="分块大小">{{ currentDoc.chunkSize }}</el-descriptions-item>
        <el-descriptions-item label="分块重叠">{{ currentDoc.chunkOverlap }}</el-descriptions-item>
        <el-descriptions-item label="分块数量">{{ currentDoc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="处理状态">
          <el-tag :type="statusTagType(currentDoc.status)" size="small">{{ statusLabel(currentDoc.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上传时间" :span="2">{{ currentDoc.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadDocument, pageDocuments, deleteDocument, getDocument } from '../../api/document'

const loading = ref(false)
const uploading = ref(false)
const tableData = ref([])
const total = ref(0)
const showUploadDialog = ref(false)
const showDetailDialog = ref(false)
const currentDoc = ref(null)

const query = reactive({ page: 1, size: 10, fileName: '', status: '' })

const uploadForm = reactive({
  file: null,
  chunkSize: 512,
  chunkOverlap: 64
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.status) params.status = query.status
    // Note: fileName search is client-side filter since backend doesn't support it yet
    const data = await pageDocuments(params)
    let records = data.records || []
    if (query.fileName) {
      records = records.filter(r => r.fileName && r.fileName.includes(query.fileName))
    }
    tableData.value = records
    total.value = data.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file) => {
  uploadForm.file = file.raw
}

const handleUpload = async () => {
  if (!uploadForm.file) return
  uploading.value = true
  try {
    await uploadDocument(uploadForm.file, uploadForm.chunkSize, uploadForm.chunkOverlap)
    ElMessage.success('文档上传成功，后台正在处理中...')
    showUploadDialog.value = false
    uploadForm.file = null
    fetchData()
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

const handleDelete = async (id) => {
  try {
    await deleteDocument(id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

const viewDetail = async (row) => {
  try {
    currentDoc.value = await getDocument(row.id)
    showDetailDialog.value = true
  } catch {
    ElMessage.error('获取详情失败')
  }
}

const resetQuery = () => {
  query.page = 1
  query.size = 10
  query.fileName = ''
  query.status = ''
  fetchData()
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

const statusTagType = (status) => {
  return { PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }[status] || 'info'
}

const statusLabel = (status) => {
  return { PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }[status] || status
}

onMounted(fetchData)
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
.search-form {
  margin-bottom: 16px;
}
</style>
