# Python QLoRA 微调服务 Dockerfile
# GPU 训练使用基础镜像；CPU 模式可直接启动测试 API

FROM python:3.10-slim
WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 依赖（FastAPI 服务，训练依赖按需在 GPU 环境安装）
COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt

# 复制服务源码
COPY main.py trainer.py /app/

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -f http://localhost:8002/health || exit 1

EXPOSE 8002
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8002"]
