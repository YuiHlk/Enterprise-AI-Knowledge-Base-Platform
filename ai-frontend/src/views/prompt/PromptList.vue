<template>
  <div class="prompt-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>提示词模板管理</span>
          <el-button type="primary" @click="$router.push('/prompts/create')">
            <el-icon><Plus /></el-icon> 新增模板
          </el-button>
        </div>
      </template>

      <!-- 搜索 -->
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="场景">
          <el-input v-model="query.scene" placeholder="输入场景名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable>
            <el-option label="启用" value="ACTIVE" />
            <el-option label="归档" value="ARCHIVED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="scene" label="场景" width="150" />
        <el-table-column prop="systemPrompt" label="系统提示词" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="temperature" label="Temperature" width="100" />
        <el-table-column prop="maxTokens" label="MaxTokens" width="100" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/prompts/${row.id}/edit`)">编辑</el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              size="small" type="success"
              @click="handleActivate(row)"
            >激活</el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              size="small" type="warning"
              @click="handleArchive(row)"
            >归档</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next, sizes"
        @change="fetchData"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePromptTemplates,
  deletePromptTemplate,
  archivePromptTemplate,
  activatePromptTemplate
} from '../../api/promptTemplate'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, scene: '', status: '' })

const fetchData = async () => {
  loading.value = true
  try {
    const data = await pagePromptTemplates(query)
    tableData.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除「${row.scene}」v${row.version}？`, '提示', { type: 'warning' })
  await deletePromptTemplate(row.id)
  ElMessage.success('删除成功')
  fetchData()
}

const handleArchive = async (row) => {
  await archivePromptTemplate(row.id)
  ElMessage.success('归档成功')
  fetchData()
}

const handleActivate = async (row) => {
  await activatePromptTemplate(row.id)
  ElMessage.success('激活成功')
  fetchData()
}

const statusType = (status) => {
  return { ACTIVE: 'success', ARCHIVED: 'info', DRAFT: 'warning' }[status] || 'info'
}

const resetQuery = () => {
  query.page = 1
  query.size = 10
  query.scene = ''
  query.status = ''
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 16px;
}
</style>
