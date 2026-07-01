import request from './request'

// 创建消融实验
export function createExperiment(name, testSetName, baseConfig, variableConfigs) {
  return request.post('/ablation/experiments', null, {
    params: {
      name,
      testSetName,
      baseConfig: JSON.stringify(baseConfig),
      variableConfigs: JSON.stringify(variableConfigs)
    }
  })
}

// 获取实验列表
export function listExperiments() {
  return request.get('/ablation/experiments')
}

// 获取实验详情（含对比报告）
export function getExperiment(id) {
  return request.get(`/ablation/experiments/${id}`)
}

// 启动实验
export function runExperiment(id) {
  return request.post(`/ablation/experiments/${id}/run`)
}

// 删除实验
export function deleteExperiment(id) {
  return request.delete(`/ablation/experiments/${id}`)
}
