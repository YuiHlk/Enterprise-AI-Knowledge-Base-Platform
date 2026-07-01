# 开发指南

本文档面向参与本项目的开发者，涵盖环境搭建、项目结构、编码规范和开发流程。

## 开发环境准备

### 必需软件

| 软件 | 版本要求 | 用途 |
|------|---------|------|
| JDK | 17+ | Java 后端编译运行 |
| Maven | 3.8+ | 后端依赖管理与构建 |
| Node.js | 18+ | 前端构建工具链 |
| Docker | 20.10+ | MySQL + ChromaDB + 全栈容器化 |
| IntelliJ IDEA | 2023+ | 推荐 IDE（Community 版即可） |

### 开发工具配置

**IntelliJ IDEA 插件推荐：**
- Spring Boot Helper
- MyBatisX（MyBatis-Plus XML 跳转）
- Vue.js（前端文件高亮）
- Docker（容器管理）

**Maven 配置：** 项目已配置 Spring Milestones 仓库（`pom.xml`），用于获取 Spring AI 里程碑版本。确保 Maven `settings.xml` 可访问中央仓库和 Spring 仓库。

## 项目结构详解

### 后端 ai-java-main

```
ai-java-main/src/main/java/com/test/qa/
├── QaApplication.java              # @SpringBootApplication 启动类（@EnableAsync）
├── config/
│   ├── AsyncConfig.java             # 线程池配置（核心4，最大8，队列100）
│   ├── ChromaClientConfig.java      # ChromaDB HTTP 客户端（Spring WebClient）
│   ├── GlobalExceptionHandler.java  # 全局异常处理（@RestControllerAdvice）
│   ├── MyBatisPlusConfig.java       # MyBatis-Plus 分页插件 + MapperScan
│   ├── PythonTrainClientConfig.java # Python 微调服务 WebClient
│   └── SpringDocConfig.java         # OpenAPI Swagger 文档配置
├── controller/
│   ├── PromptTemplateController.java # /api/prompt-templates 提示词CRUD+版本管理
│   ├── DocumentController.java       # /api/documents 文档上传/查询/删除
│   ├── QAController.java             # /api/qa RAG问答+对话历史
│   ├── EvaluationController.java     # /api/evaluation 评测任务+测试集管理
│   ├── AblationController.java       # /api/ablation 消融实验
│   └── TrainController.java          # /api/train 微调任务管理
├── domain/
│   ├── Result.java                   # 统一响应包装 {code, message, data}
│   ├── PromptTemplate.java           # 提示词模板实体
│   ├── RagDocument.java              # RAG文档实体
│   ├── RagChunk.java                 # 分块实体
│   ├── ChatLog.java                  # 对话日志实体
│   ├── QaTestSet.java                # 评测问题集实体
│   ├── QaEvaluationRecord.java       # 评测记录实体
│   ├── AblationExperiment.java       # 消融实验实体
│   └── TrainTask.java                # 微调任务实体
├── mapper/                          # MyBatis-Plus BaseMapper 接口（8个）
├── service/
│   ├── QAService.java               # RAG编排核心（检索→拼接→LLM→日志）
│   ├── DocumentService.java         # 文档上传+异步处理接口
│   ├── EmbeddingService.java        # Embedding生成+ChromaDB交互
│   ├── RetrievalService.java        # 语义检索+结果加载
│   ├── TextChunkService.java        # 滑动窗口文本分块
│   ├── PromptRenderService.java     # 模板渲染（{{variable}}替换）
│   ├── TestSetService.java          # 评测集管理
│   ├── EvaluationService.java       # LLM-as-Judge评测引擎
│   ├── AblationService.java         # 消融实验引擎（笛卡尔积组合）
│   ├── TrainTaskService.java        # 微调任务编排（Java↔Python联动）
│   └── impl/                        # 服务实现类（10个）
└── resources/
    ├── application.yml              # 应用配置（数据源/AI/ChromaDB/RAG/评测/消融/微调/线程池）
    ├── db/init.sql                  # 8张表DDL（数据库初始化）
    └── mapper/                      # MyBatis XML映射文件目录
```

### 前端 ai-frontend

```
ai-frontend/src/
├── main.js                  # Vue 应用入口（ElementPlus + 图标注册）
├── App.vue                  # 根组件（<router-view />）
├── api/
│   ├── request.js           # Axios 实例（/api 代理，响应拦截）
│   ├── qa.js                # 问答相关 API
│   ├── promptTemplate.js    # 提示词模板 API
│   ├── document.js          # 文档管理 API
│   ├── evaluation.js        # 评测 API
│   ├── ablation.js          # 消融实验 API
│   └── train.js             # 微调任务 API
├── router/index.js          # Vue Router 配置（10条路由）
├── layouts/MainLayout.vue   # 主布局（侧边栏 + 头部 + 内容区）
└── views/
    ├── Dashboard.vue        # 首页概览
    ├── prompt/
    │   ├── PromptList.vue   # 提示词列表（搜索/分页/操作）
    │   └── PromptForm.vue   # 新增/编辑提示词表单
    ├── knowledge/
    │   └── KnowledgeBase.vue # 知识库文档管理
    ├── chat/
    │   └── ChatView.vue     # RAG问答对话界面
    ├── evaluation/
    │   └── EvalList.vue     # 评测管理（测试集 + 评测任务）
    ├── ablation/
    │   └── AblationList.vue  # 消融实验（实验列表 + 对比报告）
    └── train/
        └── TrainTask.vue    # 模型微调任务管理
```

### Python 微调服务 python-train-side

```
python-train-side/
├── main.py              # FastAPI 应用（/health, /train, /train/{id}/status）
├── trainer.py           # QLoRATrainer 类骨架（完整训练代码注释，GPU环境启用）
├── requirements.txt     # Python 依赖（FastAPI 必装，训练依赖按需取消注释）
├── Dockerfile           # 容器化部署
└── README.md           # Python 服务独立说明
```

## 本地运行

### 1. 启动 Docker 服务

```bash
docker-compose up -d mysql chromadb
```

### 2. 初始化数据库

首次运行时，`init.sql` 会自动执行。也可手动执行：

```bash
docker exec -i qa-mysql mysql -uroot -proot123 qa_platform < ai-java-main/src/main/resources/db/init.sql
```

### 3. 配置 AI 模型

编辑 `ai-java-main/src/main/resources/application.yml`，配置大模型 API：

```yaml
spring:
  ai:
    openai:
      api-key: ${AI_API_KEY:your-api-key}
      base-url: ${AI_BASE_URL:https://api.openai.com}
      chat:
        options:
          model: gpt-4o-mini
```

支持所有 OpenAI 兼容协议的模型服务（通义千问、DeepSeek、Claude 等），只需修改 `base-url`、`api-key` 和 `model`。

### 4. 启动后端

```bash
cd ai-java-main
mvn spring-boot:run
```

访问 Swagger 文档：http://localhost:8080/swagger-ui.html

### 5. 启动前端

```bash
cd ai-frontend
npm install    # 首次运行
npm run dev
```

访问前端页面：http://localhost:3000

### 6. 启动 Python 微调服务（可选）

```bash
cd python-train-side
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8002 --reload
```

## API 接口参考

### 提示词模板 API（/api/prompt-templates）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页查询（page, size, scene, status） |
| GET | `/{id}` | 获取详情 |
| POST | `/` | 新增（body: PromptTemplate JSON） |
| PUT | `/{id}` | 更新 |
| DELETE | `/{id}` | 删除 |
| GET | `/scene/{scene}/versions` | 获取场景所有版本 |
| PUT | `/{id}/archive` | 归档 |
| PUT | `/{id}/activate` | 激活 |

### 文档管理 API（/api/documents）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/upload` | 上传文档（multipart: file, chunkSize, chunkOverlap） |
| GET | `/` | 分页查询（page, size, status） |
| GET | `/{id}` | 文档详情 |
| DELETE | `/{id}` | 删除（级联删除分块） |

### RAG 问答 API（/api/qa）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ask` | RAG 问答（question, promptTemplateId, documentId?, sessionId?） |
| GET | `/chat-history` | 会话历史（sessionId） |

### 评测 API（/api/evaluation）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/test-sets` | 分页查询评测集 |
| POST | `/test-sets` | 创建评测问题 |
| POST | `/test-sets/batch` | 批量导入 |
| DELETE | `/test-sets/{id}` | 删除问题 |
| POST | `/run` | 启动批量评测（异步） |
| GET | `/records` | 查询评测记录 |
| GET | `/tasks` | 评测任务列表 |
| GET | `/tasks/{taskId}/summary` | 任务汇总报告 |

### 消融实验 API（/api/ablation/experiments）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 实验列表 |
| POST | `/` | 创建实验（含变量配置） |
| POST | `/{id}/run` | 启动实验（异步） |
| GET | `/{id}` | 实验详情 + 对比报告 |
| DELETE | `/{id}` | 删除实验 |

### 微调任务 API（/api/train/tasks）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页查询任务列表 |
| POST | `/` | 创建并启动微调任务 |
| GET | `/{id}` | 获取任务详情 |
| POST | `/{id}/poll` | 手动轮询训练状态 |
| DELETE | `/{id}` | 删除任务 |

## 编码规范

### Java 后端规范

1. **严格分层调用**：Controller → Service → Mapper，不允许跨层调用
2. **配置外部化**：所有参数、阈值、地址写入 `application.yml`，禁止硬编码
3. **统一响应**：所有接口返回 `Result<T>` 包装（code/message/data）
4. **异常处理**：统一由 `GlobalExceptionHandler` 处理，Service 层抛业务异常即可
5. **API 文档**：每个 Controller 和接口必须带 `@Tag`、`@Operation`、`@Parameter` 注解
6. **注释要求**：复杂 AI 工程问题（幻觉风险、Prompt 不匹配、检索噪声、评测偏差）必须写详细注释
7. **异步操作**：耗时处理（文档解析、向量化、批量评测、消融实验）使用 `@Async` 异步执行

### Vue 前端规范

1. **API 封装**：所有后端请求封装在 `src/api/` 模块中，页面组件不直接写 axios 调用
2. **组件模式**：列表页使用 el-card + el-table + el-pagination 模式
3. **状态管理**：简单场景用 reactive/ref 即可，不引入 Vuex/Pinia
4. **错误处理**：API 调用统一 try/catch，提供用户友好的错误提示
5. **加载状态**：表格使用 v-loading，按钮使用 :loading，空数据使用 el-empty
6. **定时器清理**：setInterval/setTimeout 必须在 onUnmounted 中清除

### 关键配置项说明

```yaml
# RAG 配置
rag:
  chunk:
    default-size: 512        # 默认分块大小（影响召回粒度和语义完整性）
    default-overlap: 64      # 重叠大小（缓解边界语义割裂）
    max-size: 4096           # 最大分块（防止单块超出模型上下文）
  retrieval:
    top-k: 5                 # 检索返回Top-K（增大提高召回，但引入噪声）
    similarity-threshold: 0.7 # 相似度阈值（过低引入无关内容，过高遗漏相关）

# 评测配置
evaluation:
  judge:
    temperature: 0.1         # LLM-as-Judge低温评级，减少评分方差
  batch:
    concurrency: 4           # 批量评测并发数

# 消融实验配置
ablation:
  max-variable-values: 10    # 单变量最大取值数（防组合爆炸）
  max-combinations: 50       # 最大总组合数（安全性限制）
```

## Docker Compose 部署

完整部署所有服务（MySQL + ChromaDB + 后端 + 前端 + Python 微调）：

### 方式一：一键脚本

```bash
bash start.sh    # Linux/Mac
start.bat        # Windows
```

### 方式二：手动执行

```bash
# 1. 配置环境变量
cp .env.example .env
vim .env  # 编辑 AI_API_KEY 等配置

# 2. 启动全部服务
docker compose up -d --build

# 3. 查看日志
docker compose logs -f

# 4. 停止服务
docker compose down
```

### 服务端口映射

| 服务 | 容器端口 | 宿主机端口 | 说明 |
|------|---------|-----------|------|
| MySQL | 3306 | 3306 | 数据库 |
| ChromaDB | 8000 | 8001 | 向量数据库 |
| Backend | 8080 | 8080 | Spring Boot API |
| Frontend | 3000 | 3000 | Vue3 Nginx 静态服务 |
| Python Train | 8002 | 8002 | FastAPI 微调服务 |

### GPU 支持（Python 微调服务）

取消 `docker-compose.yml` 中 `python-train` 服务的 GPU 配置注释：

```yaml
python-train:
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

同时取消 `python-train-side/requirements.txt` 中训练依赖的注释并重新构建。

## 测试

### 后端测试

```bash
cd ai-java-main
mvn test
```

### 前端构建验证

```bash
cd ai-frontend
npm run build
```

### Python 服务验证

```bash
cd python-train-side
python -c "from main import app; print('OK')"
```

## 开发流程

1. **需求确认**：以 `Java_AI应用开发.txt` 为唯一需求来源
2. **分支开发**：从主分支创建 feature 分支
3. **后端优先**：先完成 Controller + Service + Mapper 全链路
4. **前后端联调**：前端页面通过 Vite 代理调用本地后端
5. **自测**：编译通过 + 启动验证 + 核心流程走通
6. **代码审查**：检查分层规范、注释完整性、配置外部化

## 常见问题

**Q: ChromaDB 连接失败？**
确认 Docker 容器运行中：`docker ps | grep chromadb`。容器内 ChromaDB 端口为 8000，宿主机映射为 8001。Docker Compose 内部网络使用 8000。

**Q: 前端页面空白？**
检查浏览器控制台，确认 Vite 代理是否转发到后端。确保后端先启动（端口 8080）。

**Q: 文档上传后一直是"处理中"状态？**
确认 AI API Key 已配置，Embedding 服务可正常调用。检查后端日志中的异步任务执行情况。

**Q: 问答返回为空？**
确认至少有一个文档状态为"已完成"（即有向量化分块入库），且选择的提示词模板状态为"启用"。

**Q: Docker 构建缓慢？**
首次构建需要下载基础镜像和所有依赖，后续构建会利用 Docker 层缓存加速。如修改了依赖，仅对应层会重新构建。

**Q: 微调任务一直处于 PENDING 状态？**
确认 Python 微调服务已启动（`docker ps | grep python-train`），且后端配置 `PYTHON_TRAIN_URL` 正确指向该服务。
