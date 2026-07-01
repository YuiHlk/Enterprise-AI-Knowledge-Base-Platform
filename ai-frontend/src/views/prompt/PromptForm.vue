<template>
  <div class="prompt-form">
    <el-card>
      <template #header>
        <span>{{ isEdit ? '编辑提示词模板' : '新增提示词模板' }}</span>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="场景名称" prop="scene">
          <el-input v-model="form.scene" placeholder="如：客服问答、知识检索、代码生成" />
        </el-form-item>

        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="你是一个专业的...助手，请根据..."
          />
        </el-form-item>

        <el-form-item label="用户模板" prop="userTemplate">
          <el-input
            v-model="form.userTemplate"
            type="textarea"
            :rows="4"
            placeholder="用户问题：{{question}}&#10;参考资料：{{context}}"
          />
        </el-form-item>

        <el-form-item label="Few-Shot示例">
          <el-input
            v-model="form.fewShotExamples"
            type="textarea"
            :rows="3"
            placeholder='[{"input":"...","output":"..."}]'
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="Temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Top P">
              <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.1" :precision="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Max Tokens">
              <el-input-number v-model="form.maxTokens" :min="1" :max="32768" :step="256" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="DRAFT">草稿</el-radio>
            <el-radio value="ACTIVE">启用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="版本备注">
          <el-input v-model="form.remark" placeholder="版本变更说明" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
          <el-button @click="$router.back()">返回</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPromptTemplate, createPromptTemplate, updatePromptTemplate } from '../../api/promptTemplate'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const form = reactive({
  scene: '',
  systemPrompt: '',
  userTemplate: '',
  fewShotExamples: '',
  temperature: 0.7,
  topP: 1.0,
  maxTokens: 2048,
  status: 'DRAFT',
  remark: ''
})

const rules = {
  scene: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }],
  userTemplate: [{ required: true, message: '请输入用户提示词模板', trigger: 'blur' }]
}

onMounted(async () => {
  if (isEdit.value) {
    const data = await getPromptTemplate(route.params.id)
    Object.assign(form, data)
  }
})

const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updatePromptTemplate(route.params.id, form)
      ElMessage.success('更新成功')
    } else {
      await createPromptTemplate(form)
      ElMessage.success('创建成功')
    }
    router.push('/prompts')
  } finally {
    submitting.value = false
  }
}
</script>
