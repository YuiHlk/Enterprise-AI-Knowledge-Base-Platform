# 项目总结：企业级 AI 知识库 RAG 问答与自动化评测平台

## 项目概述

本项目是一个完整的全栈 AI 应用平台，覆盖工业级 AI 应用开发的全套工程能力。系统以 Spring Boot 3.4.5 + Vue 3 为技术底座，集成了提示词工程、RAG 检索增强生成、LLM-as-Judge 自动化评测、消融实验、模型微调联动等 AI 应用核心模块。

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户浏览器 (:3000)                       │
│                    Vue3 + Element Plus                       │
└──────────┬──────────────────────────────────────┬───────────┘
           │ /api 反向代理                          │
           ▼                                       ▼
┌──────────────────────┐              ┌──────────────────────┐
│   Spring Boot (:8080) │              │  Python FastAPI      │
│   ai-java-main        │──POST /train─▶│  (:8002)            │
│   7 Controllers       │◀──GET status──│  QLoRA Fine-tuning  │
│   10 Services         │              │  transformers/peft   │
└──────┬───────┬────────┘              └──────────────────────┘
       │       │
       ▼       ▼
┌──────────┐ ┌──────────┐
│ MySQL    │ │ ChromaDB │
│ (:3306)  │ │ (:8001)  │
│ 8 tables │ │ 向量存储  │
└──────────┘ └──────────┘
```

## 技术选型说明

| 组件 | 选型 | 理由 |
|------|------|------|
| 后端框架 | Spring Boot 3.4.5 | 企业级 Java 生态，成熟稳定 |
| AI 集成 | Spring AI 1.0.0-M6 | 统一 OpenAI 兼容协议抽象，降低模型切换成本 |
| ORM | MyBatis-Plus 3.5.9 | Lambda 查询 + 自动填充 + 分页插件，开发效率高 |
| 向量数据库 | ChromaDB | 开源、Docker 部署轻量、REST API 友好 |
| 前端框架 | Vue 3 + Vite | 组合式 API、极速热更新、TypeScript 友好 |
| UI 库 | Element Plus | 成熟的企业级组件库，中文文档完善 |
| 文档解析 | Apache PDFBox 3.0.3 | 纯 Java 实现，无需外部依赖 |
| Python 微调 | FastAPI + transformers/peft | 高性能异步框架 + HuggingFace 生态 |
| 容器化 | Docker Compose | 5 服务一键编排，环境一致 |

## 各阶段成果

### 阶段一：基础架构搭建

**后端：**
- Spring Boot 项目初始化，多模块 Maven 结构
- 8 张数据表 DDL（prompt_template, rag_document, rag_chunk, qa_test_set, qa_evaluation_record, ablation_experiment, chat_log, train_task）
- MyBatis-Plus 配置 + 分页插件
- 全局异常处理 + 统一响应模型
- SpringDoc OpenAPI 文档配置
- 异步线程池配置（@EnableAsync）

**前端：**
- Vue 3 + Vite 项目搭建
- Element Plus 组件库集成
- Vue Router 路由配置（含守卫）
- Axios 请求封装 + 响应拦截
- MainLayout 主布局（侧边栏导航 + 顶部栏）
- Dashboard 首页概览

**提示词工程：**
- PromptTemplate 实体 + Mapper
- CRUD 完整后端接口（含版本管理、归档/激活）
- 前端提示词列表 + 编辑表单页面

### 阶段二：RAG 检索增强问答

**文档处理管道：**
- DocumentService：上传 → 解析 → 分块 → 向量化（异步处理）
- TextChunkService：滑动窗口分块（chunkSize + overlap 可配置）
- EmbeddingService：调用 OpenAI 兼容 Embedding API
- ChromaClientConfig：WebClient 封装 ChromaDB REST API
- RetrievalService：语义检索 + 相似度过滤

**问答链路：**
- QAService：检索 → Prompt 渲染 → LLM 调用 → 结果返回 → 日志记录
- PromptRenderService：正则替换 `{{variable}}` 模板变量
- 对话历史持久化 + 会话管理

**前端页面：**
- KnowledgeBase.vue：文档上传（拖拽/选择）、状态筛选、详情弹窗
- ChatView.vue：消息气泡、Prompt 模板选择、文档筛选、来源展示、思考动画

**文档：**
- README.md：项目概述、架构图、快速开始
- DEVELOPMENT.md：环境搭建、项目结构、编码规范、API 参考

### 阶段三：LLM-as-Judge 自动化评测

**评测引擎核心设计：**
- 三维 LLM 打分体系（1-5 分）：
  - 答案相关性（Answer Relevance）：模型回答是否切题
  - 上下文忠实度（Context Faithfulness）：回答是否基于检索上下文
  - 幻觉风险（Hallucination Risk）：回答是否包含无依据内容
- 评分 Prompt 设计：固定评分标准 + 结构化 JSON 输出要求
- 防御性 JSON 解析：处理 markdown 代码块包裹、尾随逗号
- 客观指标 Java 计算：检索精准度（关键词命中率）、上下文召回率

**工程实现：**
- EvaluationService（~500 行核心逻辑）
- @Async 批量执行（可配置并发度）
- 评测结果聚合汇总（按 taskId 分组计算平均分）
- 8 个 REST 端点

**前端：**
- EvalList.vue：双 Tab 布局
  - 测试集管理：问题列表、批量导入、难度标记
  - 评测任务：执行按钮、进度追踪、汇总报告

### 阶段四：消融实验系统

**组合生成算法：**
- 笛卡尔积递归 + 回溯实现
- 安全限制：单变量最多 10 个值，总组合不超过 50
- 支持变量：chunkSize、topK、promptTemplateId、modelName

**实验执行：**
- @Async 异步迭代所有组合
- 每组配置复用 EvaluationService.evaluateSingle
- 进度追踪（completedTasks / totalTasks）

**对比报告：**
- 按变量值分组聚合（每组平均分）
- 每项指标自动标注最优组（加粗高亮）
- 支持 higher-is-better / lower-is-better 双向判断

**前端：**
- AblationList.vue：实验列表、创建对话框（动态变量构建器）、对比报告表格

### 阶段五：Java+Python 微调联动

**Python 服务：**
- FastAPI 应用（main.py）：/health、POST /train、GET /train/{id}/status
- 模拟训练流程（后台 daemon 线程，5%→100% 阶梯进度）
- QLoRATrainer 骨架（trainer.py）：完整训练代码注释，GPU 环境即可启用
- 支持模型：Qwen2-7B-Instruct、Qwen2-1.5B-Instruct、Llama-3-8B-Instruct、Mistral-7B-Instruct

**Java 联动：**
- TrainTaskService：通过 @Qualifier WebClient 调用 Python 服务
- 任务创建 → Python 启动 → 状态轮询 → 进度/指标同步
- 状态映射：Python 状态 ↔ Java 枚举

**前端：**
- TrainTask.vue：任务列表（进度条）、创建对话框（LoRA 超参数配置）、详情弹窗、自动轮询

### 阶段六：全局优化与容器化部署

**Docker 化：**
- 3 个多阶段 Dockerfile（后端、前端、Python）
- Nginx 反向代理前端 + API 代理
- docker-compose.yml 5 服务编排（含健康检查 + 启动依赖）
- .env.example 环境变量模板

**一键部署：**
- start.sh（Linux/Mac）：自动检测环境、引导配置、构建启动、状态检查
- start.bat（Windows）：对应 Windows 批处理版本

**文档更新：**
- README.md：完整项目说明、一键部署指引、6 阶段全部完成
- DEVELOPMENT.md：详细开发指南、全部 API 端点、FAQ
- PROJECT_SUMMARY.md：本文档，项目总结与经验沉淀

## 关键数据

| 维度 | 数据 |
|------|------|
| 后端 Controller | 7 个 |
| 后端 Service 接口/实现 | 10 + 10 |
| 后端 Domain 实体 | 9 个 |
| 后端 Mapper | 8 个 |
| REST API 端点 | 40+ |
| 数据库表 | 8 张 |
| 前端页面组件 | 8 个 |
| 前端 API 模块 | 6 个 |
| Python 服务文件 | 3 个 |
| Docker 服务 | 5 个 |
| 文档文件 | 4 个 |

## 工程亮点

### 1. LLM-as-Judge 评分设计
评分 Prompt 被设计为结构化 JSON 输出，配合低温（0.1）以降低评分方差。包含明确的 1-5 分等级描述和评分锚点。解析器防御性处理 LLM 输出的各种格式变体（markdown 代码块、尾随逗号、键名加引号/不加引号）。

### 2. 消融实验笛卡尔积
递归回溯算法生成组合，配合安全限制防止组合爆炸。结果按变量分组聚合，自动标注每项指标的最优配置，使多维度的效果对比一目了然。

### 3. 异构服务协同
Java 与 Python 通过 REST API 松耦合协作。Java 负责业务编排和状态持久化，Python 专注于 ML 训练计算。WebClient 异步非阻塞调用 + @Qualifier 多客户端注入。

### 4. 全面参数化
所有阈值、超参数、地址均通过 application.yml 或环境变量配置，无任何硬编码。支持 OpenAI 兼容协议的全模型切换，仅需修改 base-url + api-key + model 三个参数。

### 5. Docker Compose 一键部署
从零环境到全栈运行仅需：cp .env.example .env → bash start.sh。5 个服务带有健康检查和正确的启动依赖顺序。

## 技术债务与改进方向

1. **认证授权**：当前无用户认证体系，生产环境需集成 Spring Security + JWT
2. **测试覆盖**：缺少单元测试和集成测试，需补充 Service 层 Mock 测试
3. **Python 训练生产化**：当前为模拟训练流程，GPU 环境需安装完整依赖并启用 trainer.py
4. **Python 服务持久化**：任务状态存储在内存 dict，生产建议 Redis/PostgreSQL
5. **前端状态管理**：随页面复杂度增长，可考虑引入 Pinia 状态管理
6. **CI/CD 管道**：缺少 GitHub Actions/Jenkins 自动化构建和部署流水线
7. **监控告警**：缺少 Prometheus + Grafana 监控面板和日志聚合（ELK/Loki）
8. **WebSocket 推送**：训练进度目前靠前端轮询，可用 WebSocket 实现服务端主动推送
9. **文件存储**：文档上传存储在本地上传目录，生产环境建议对接 OSS/MinIO

## 总结

本项目完整实践了从零搭建企业级 AI 应用的全过程，涵盖了提示词工程、RAG 系统、自动化评测、消融实验、模型微调等 AI 应用领域的核心技术模块。通过 Java + Python 异构微服务架构，展示了如何在企业级 Java 生态中集成 AI/ML 能力的最佳实践。

项目严格遵循工程化规范：配置外部化、分层架构、统一响应、异步处理、容器化部署，具备直接落地改造为生产系统的代码质量。
