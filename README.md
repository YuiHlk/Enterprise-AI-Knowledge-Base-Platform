# 企业级 AI 知识库 RAG 问答与自动化评测平台

基于 Spring Boot + Vue3 的全栈 AI 应用平台，系统覆盖工业级 AI 应用开发全套工程能力：提示词工程、RAG 检索增强生成、LLM-as-Judge 自动化评测、消融实验、模型微调联动。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Java 17, Spring Boot 3.4.5 |
| AI 框架 | Spring AI 1.0.0-M6 (OpenAI 兼容协议) |
| ORM | MyBatis-Plus 3.5.9 |
| 数据库 | MySQL 8.0 |
| 向量数据库 | ChromaDB |
| 文档解析 | Apache PDFBox 3.0.3 |
| API 文档 | SpringDoc OpenAPI 2.7.0 |
| 前端框架 | Vue 3, Vite, Element Plus |
| 容器化 | Docker Compose (一键部署) |
| Python 微调 | Python 3.10, FastAPI, transformers/peft |

## 项目架构

```
QA/
├── ai-java-main/           # SpringBoot 核心后端服务
│   ├── config/             # 配置层（AI、ChromaDB、异步、异常处理、Python客户端）
│   ├── controller/         # REST API 控制器（7个Controller，40+个端点）
│   ├── service/            # 业务逻辑层（QA、文档、嵌入、检索、评测、消融、微调）
│   ├── domain/             # 11个实体与统一响应模型
│   ├── mapper/             # MyBatis-Plus 数据访问层（8个Mapper）
│   └── resources/          # 配置、数据库初始化脚本（8张表）
├── ai-frontend/            # Vue3 + Element Plus 管理前端
│   ├── src/views/          # 8个页面组件（Dashboard、Prompt、Knowledge、Chat、Eval、Ablation、Train）
│   ├── src/api/            # 6个后端 API 封装模块
│   ├── src/router/         # 路由配置（10条路由）
│   └── src/layouts/        # 布局组件
├── python-train-side/      # Python QLoRA 微调子服务
│   ├── main.py             # FastAPI 服务（/train端点）
│   ├── trainer.py          # QLoRA 训练器骨架
│   └── requirements.txt    # Python 依赖
├── docker-compose.yml      # 5服务容器编排（MySQL + ChromaDB + Backend + Frontend + Python）
├── start.sh / start.bat    # 一键部署脚本
├── .env.example            # 环境变量配置模板
└── pom.xml                 # Maven 父级 POM
```

## 快速开始

### 前置要求

- Docker & Docker Compose (唯一必需)
- JDK 17+ / Node.js 18+ / Maven 3.8+ (仅本地开发需要)

### 一键部署（推荐）

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env，至少填写 AI_API_KEY

# 2. 一键启动所有服务
# Linux/Mac:
bash start.sh
# Windows:
start.bat
```

部署完成后访问：
- 前端页面: http://localhost:3000
- 后端 Swagger: http://localhost:8080/swagger-ui.html
- Python 微调文档: http://localhost:8002/docs

### 本地开发

```bash
# 1. 启动基础设施
docker-compose up -d mysql chromadb

# 2. 启动后端
cd ai-java-main && mvn spring-boot:run

# 3. 启动前端
cd ai-frontend && npm install && npm run dev

# 4. 启动 Python 微调服务（可选）
cd python-train-side && pip install -r requirements.txt && uvicorn main:app --port 8002
```

## 核心功能模块

### 模块一：系统化提示词工程平台
- 提示词模板版本管理（场景、系统提示、用户模板、Few-Shot 示例）
- 动态 Prompt 渲染引擎（`{{variable}}` 模板变量替换）
- 温度、Top-P、MaxTokens 可配置
- 版本归档/激活生命周期管理

### 模块二：RAG 检索增强问答系统
- 文档上传解析（PDF/MD/TXT）
- 可配置滑动窗口分块策略（chunkSize + overlap）
- Embedding 向量生成 + ChromaDB 向量存储
- 语义相似度检索 + 上下文拼接 + LLM 问答
- 异步处理管道（上传即返回，后台向量化）
- 会话管理 + 对话历史持久化

### 模块三：LLM-as-Judge 自动化评测
- 检索精准度、上下文召回率（客观指标，Java 计算）
- 答案相关性、上下文忠实度、幻觉风险（LLM 三维打分 1-5 分）
- 全自动批量评测任务（异步线程池，可配置并发度）
- 评测任务汇总报告（按任务维度聚合平均分）

### 模块四：消融实验系统
- 多维度自动对比：Chunk 大小、TopK、Prompt 版本、模型名称
- 笛卡尔积组合生成 + 安全限制防爆炸
- 全自动批量执行 + 进度追踪
- 对比报告：按变量分组聚合，自动标注每项指标最优

### 模块五：Java+Python 微调联动
- QLoRA 4bit 模型微调（Qwen2-7B-Instruct 等）
- Java WebClient 调用 Python FastAPI 服务
- LoRA 超参数可视化配置（Rank、Alpha、学习率、Epoch、BatchSize）
- 前端自动轮询训练进度（每10秒）

## 开发阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段一 | 后端工程初始化、数据库、Prompt 后端接口、前端框架 | ✅ 完成 |
| 阶段二 | 完整 RAG 问答链路、知识库与对话前端页面 | ✅ 完成 |
| 阶段三 | 自动化评测体系、评测可视化前端 | ✅ 完成 |
| 阶段四 | 消融实验系统 | ✅ 完成 |
| 阶段五 | Python 微调子服务、Java 对接、前端页面 | ✅ 完成 |
| 阶段六 | 全局优化、Docker Compose 一键部署、项目总结 | ✅ 完成 |

## 工程规范

- Java 严格分层：Controller → Service → Domain → Mapper → Config
- 所有参数 yml 配置化，禁止硬编码
- 所有接口带完整 SpringDoc/Swagger 注解
- 前端只调用后端接口，不直接请求大模型 API
- AI 工程问题必须写详细注释（幻觉、Prompt 不匹配、检索噪声）
- 异步操作统一使用 @Async 线程池

## 学习收获

本项目覆盖工业级 AI 应用开发全套能力，完成后可独立负责：
- 提示词工程体系搭建与版本管理
- RAG 系统从文档解析到问答链路的完整落地
- LLM-as-Judge 自动化评测系统构建
- 消融实验框架与多维度效果量化
- Java+Python 异构微服务协同架构
- Docker Compose 全栈容器化部署
