# Python QLoRA 微调服务

轻量级 HTTP 微服务，为 Java 主工程提供 QLoRA 4bit 模型微调能力。

## 架构定位

```
Java (ai-java-main)          Python (python-train-side)
┌─────────────────────┐       ┌──────────────────────┐
│ TrainController     │──POST─→│ /train (启动微调)     │
│ TrainTaskService    │←─GET──│ /train/{id}/status  │
│                     │       │                      │
│ 业务调度/持久化      │       │ 模型训练/权重导出     │
└─────────────────────┘       └──────────────────────┘
```

Java 负责：任务管理、状态持久化、业务编排
Python 负责：数据集处理、QLoRA 微调、LoRA 权重导出

## 快速开始

### 1. 安装依赖

```bash
cd python-train-side
pip install -r requirements.txt
```

### 2. 启动服务

```bash
python main.py
# 或
uvicorn main:app --host 0.0.0.0 --port 8002 --reload
```

服务运行在 http://localhost:8002

### 3. API 文档

启动后访问 http://localhost:8002/docs 查看 Swagger 文档。

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查（Java启动前验证） |
| POST | `/train` | 启动微调任务 |
| GET | `/train/{task_id}/status` | 查询训练进度和指标 |

## GPU 环境配置

1. 安装 CUDA 12.1+ 和 PyTorch (CUDA版)
2. 取消 `requirements.txt` 中训练依赖的注释并安装
3. 确保 GPU 显存 ≥ 16GB（Qwen2-7B 4bit ≈ 8GB显存 + 训练开销）

## 支持的基座模型

- Qwen2-7B-Instruct（推荐，中文优化）
- Qwen2-1.5B-Instruct（轻量测试）
- Llama-3-8B-Instruct
- Mistral-7B-Instruct

## 开发说明

- `main.py` 包含模拟训练流程，可直接启动测试 HTTP 接口
- `trainer.py` 包含完整 QLoRA 训练代码骨架，取消注释并安装GPU依赖后即可生产使用
- 任务状态存储在内存中，生产环境建议替换为 Redis
