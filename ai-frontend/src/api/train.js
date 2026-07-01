import request from './request'

// 创建并启动微调任务
export function createTask(params) {
  return request.post('/train/tasks', null, { params })
}

// 分页查询任务列表
export function listTasks(params) {
  return request.get('/train/tasks', { params })
}

// 获取任务详情（自动轮询）
export function getTask(id) {
  return request.get(`/train/tasks/${id}`)
}

// 手动轮询训练状态
export function pollStatus(id) {
  return request.post(`/train/tasks/${id}/poll`)
}

// 删除任务
export function deleteTask(id) {
  return request.delete(`/train/tasks/${id}`)
}
