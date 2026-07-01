import request from './request'

// 上传文档
export function uploadDocument(file, chunkSize = 512, chunkOverlap = 64) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('chunkSize', chunkSize)
  formData.append('chunkOverlap', chunkOverlap)
  // axios auto-detects FormData and sets Content-Type with boundary
  return request.post('/documents/upload', formData)
}

// 分页查询文档列表
export function pageDocuments(params) {
  return request.get('/documents', { params })
}

// 获取文档详情
export function getDocument(id) {
  return request.get(`/documents/${id}`)
}

// 删除文档
export function deleteDocument(id) {
  return request.delete(`/documents/${id}`)
}
