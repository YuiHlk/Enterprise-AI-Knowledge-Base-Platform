@echo off
REM =============================================================
REM 企业级AI知识库RAG问答与自动化评测平台 — 一键部署脚本 (Windows)
REM =============================================================
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo ==========================================================
echo   企业级AI知识库RAG问答与自动化评测平台 - 一键部署
echo ==========================================================

REM --- 1. 检查前置条件 ---
echo [INFO] 检查前置条件...

where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未检测到 Docker，请先安装 Docker Desktop: https://docs.docker.com/desktop/install/windows-install/
    pause
    exit /b 1
)

REM --- 2. 环境变量配置 ---
if not exist .env (
    echo [WARN] 未检测到 .env 文件，从 .env.example 创建默认配置...
    copy .env.example .env >nul
    echo.
    echo [WARN] 请编辑 .env 文件，至少配置 AI_API_KEY 后再重新运行此脚本
    echo.
    notepad .env
    echo.
    echo 配置完成后按任意键继续...
    pause >nul
)

REM --- 3. 启动所有服务 ---
echo [INFO] 启动所有服务（首次运行将自动构建镜像，可能需要几分钟）...

REM 检测 docker compose 子命令
set COMPOSE_CMD=docker-compose
docker compose version >nul 2>&1
if not errorlevel 1 (
    set COMPOSE_CMD=docker compose
)

%COMPOSE_CMD% up -d --build

echo [INFO] 等待服务就绪...
timeout /t 8 /nobreak >nul

REM --- 4. 检查服务状态 ---
echo [INFO] 检查服务状态...

for %%s in (qa-mysql qa-chromadb qa-backend qa-frontend qa-python-train) do (
    docker ps --format "{{.Names}}" | findstr "^%%s$" >nul 2>&1
    if errorlevel 1 (
        echo   [FAIL] %%s 未启动
    ) else (
        echo   [OK] %%s 运行中
    )
)

REM --- 5. 输出访问信息 ---
echo.
echo ==========================================================
echo   部署完成！
echo ==========================================================
echo.
echo   前端页面:     http://localhost:3000
echo   后端 API:     http://localhost:8080
echo   Swagger 文档: http://localhost:8080/swagger-ui.html
echo   Python 微调:  http://localhost:8002/docs
echo   MySQL:        localhost:3306
echo   ChromaDB:     http://localhost:8001
echo.
echo   常用命令:
echo     %COMPOSE_CMD% logs -f [service]  查看日志
echo     %COMPOSE_CMD% ps                 查看服务状态
echo     %COMPOSE_CMD% down               停止所有服务
echo     start.bat                        重新启动
echo.

pause
