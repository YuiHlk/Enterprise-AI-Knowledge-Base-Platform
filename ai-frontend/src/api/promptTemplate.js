import request from './request'

// 分页查询
export function pagePromptTemplates(params) {
  return request.get('/prompt-templates', { params })
}

// 获取详情
export function getPromptTemplate(id) {
  return request.get(`/prompt-templates/${id}`)
}

// 新增
export function createPromptTemplate(data) {
  return request.post('/prompt-templates', data)
}

// 更新
export function updatePromptTemplate(id, data) {
  return request.put(`/prompt-templates/${id}`, data)
}

// 删除
export function deletePromptTemplate(id) {
  return request.delete(`/prompt-templates/${id}`)
}

// 获取场景版本列表
export function getSceneVersions(scene) {
  return request.get(`/prompt-templates/scene/${scene}/versions`)
}

// 归档
export function archivePromptTemplate(id) {
  return request.put(`/prompt-templates/${id}/archive`)
}

// 激活
export function activatePromptTemplate(id) {
  return request.put(`/prompt-templates/${id}/activate`)
}
