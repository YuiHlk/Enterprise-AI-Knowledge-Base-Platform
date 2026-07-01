<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6" v-for="card in cards" :key="card.title">
        <el-card shadow="hover">
          <div class="card-content">
            <el-icon :size="36" :color="card.color">
              <component :is="card.icon" />
            </el-icon>
            <div class="card-info">
              <div class="card-title">{{ card.title }}</div>
              <div class="card-desc">{{ card.desc }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>平台介绍</span>
      </template>
      <p>企业级AI知识库RAG问答与自动化评测平台，提供以下核心能力：</p>
      <ul>
        <li><strong>提示词工程系统化</strong>：版本管理、A/B测试、结构化约束、幻觉抑制</li>
        <li><strong>RAG检索增强生成</strong>：文档解析、分块策略、向量检索、重排优化</li>
        <li><strong>LLM-as-Judge自动化评测</strong>：检索指标、答案相关性、上下文忠实度、幻觉检测</li>
        <li><strong>消融实验系统</strong>：多维度自动对比不同参数对模型效果的影响</li>
        <li><strong>Java+Python微调联动</strong>：完整的训练→推理→评测全链路</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
const cards = [
  { title: '提示词模板', desc: '版本管理与A/B测试', icon: 'Document', color: '#409eff' },
  { title: '知识库文档', desc: 'RAG检索增强问答', icon: 'FolderOpened', color: '#67c23a' },
  { title: '评测任务', desc: 'LLM-as-Judge自动化', icon: 'DataAnalysis', color: '#e6a23c' },
  { title: '消融实验', desc: '多维度参数对比', icon: 'Connection', color: '#f56c6c' }
]
</script>

<style scoped>
.card-content {
  display: flex;
  align-items: center;
  gap: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}
.card-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
</style>
