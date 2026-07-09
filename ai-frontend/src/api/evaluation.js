import request from './request'

// ========== 评测问题集管理 ==========

// 获取所有评测集名称列表
export function listTestSets() {
  return request.get('/evaluation/test-sets')
}

// 获取指定评测集的问题列表
export function getQuestions(setName) {
  return request.get(`/evaluation/test-sets/${encodeURIComponent(setName)}/questions`)
}

// 创建评测问题（支持批量）
export function createQuestions(questions) {
  return request.post('/evaluation/test-sets', questions)
}

// 删除评测问题
export function deleteQuestion(id) {
  return request.delete(`/evaluation/test-sets/${id}`)
}

// ========== 评测任务管理 ==========

// 启动批量评测任务（异步）
export function runEvaluation(setName, promptTemplateId, topK = 5, chunkSize = null) {
  const params = { setName, promptTemplateId, topK }
  if (chunkSize) params.chunkSize = chunkSize
  return request.post('/evaluation/run', null, { params })
}

// 获取所有评测任务列表
export function listTasks() {
  return request.get('/evaluation/tasks')
}

// 获取任务详情（含汇总统计+所有记录）
export function getTaskDetail(taskId) {
  return request.get(`/evaluation/tasks/${taskId}`)
}

// 获取单条评测记录详情
export function getRecord(id) {
  return request.get(`/evaluation/records/${id}`)
}

// 删除单条评测记录
export function deleteRecord(id) {
  return request.delete(`/evaluation/records/${id}`)
}
