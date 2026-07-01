#!/bin/bash
# =============================================================
# 企业级AI知识库RAG问答与自动化评测平台 — 一键部署脚本 (Linux/Mac)
# =============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; }

echo "=========================================================="
echo "  企业级AI知识库RAG问答与自动化评测平台 - 一键部署"
echo "=========================================================="

# --- 1. 检查前置条件 ---
log "检查前置条件..."

if ! command -v docker &> /dev/null; then
    err "未检测到 Docker，请先安装 Docker: https://docs.docker.com/engine/install/"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    err "未检测到 docker-compose，请先安装: https://docs.docker.com/compose/install/"
    exit 1
fi

DOCKER_COMPOSE="docker compose"
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE="docker-compose"
fi

# --- 2. 环境变量配置 ---
if [ ! -f .env ]; then
    warn "未检测到 .env 文件，从 .env.example 创建默认配置..."
    cp .env.example .env
    echo ""
    warn "请编辑 .env 文件，至少配置 AI_API_KEY 后再重新运行此脚本"
    warn "  编辑命令: vim .env  或  nano .env"
    echo ""
    read -rp "是否现在编辑 .env？(y/n) " choice
    case "$choice" in
        y|Y)
            ${EDITOR:-vim} .env
            ;;
        *)
            warn "请稍后手动编辑 .env 后再运行: bash start.sh"
            exit 0
            ;;
    esac
fi

# 加载环境变量
set -a
source .env 2>/dev/null || true
set +a

# --- 3. 启动所有服务 ---
log "启动所有服务（首次运行将自动构建镜像，可能需要几分钟）..."
$DOCKER_COMPOSE up -d --build

log "等待服务就绪..."
sleep 5

# --- 4. 检查服务状态 ---
log "检查服务状态..."

check_service() {
    local name=$1
    if docker ps --format '{{.Names}}' | grep -q "^${name}$"; then
        echo -e "  ${GREEN}✓${NC} $name 运行中"
    else
        echo -e "  ${RED}✗${NC} $name 未启动"
    fi
}

check_service "qa-mysql"
check_service "qa-chromadb"
check_service "qa-backend"
check_service "qa-frontend"
check_service "qa-python-train"

# --- 5. 输出访问信息 ---
echo ""
echo "=========================================================="
echo -e "  ${GREEN}部署完成！${NC}"
echo "=========================================================="
echo ""
echo "  前端页面:    http://localhost:${FRONTEND_PORT:-3000}"
echo "  后端 API:    http://localhost:${BACKEND_PORT:-8080}"
echo "  Swagger 文档: http://localhost:${BACKEND_PORT:-8080}/swagger-ui.html"
echo "  Python 微调:  http://localhost:8002/docs"
echo "  MySQL:       localhost:3306 (用户: ${MYSQL_USER:-qa_user})"
echo "  ChromaDB:    http://localhost:${CHROMADB_PORT:-8001}"
echo ""
echo "  常用命令:"
echo "    $DOCKER_COMPOSE logs -f [service]  # 查看日志"
echo "    $DOCKER_COMPOSE ps                 # 查看服务状态"
echo "    $DOCKER_COMPOSE down               # 停止所有服务"
echo "    bash start.sh                      # 重新启动"
echo ""
