# 前端 Vue3 应用 Dockerfile
# 多阶段构建：Node 编译 + Nginx 静态服务

FROM node:18-alpine AS builder
WORKDIR /build

# 利用 Docker 缓存层：先复制依赖文件
COPY package.json package-lock.json* /build/
RUN npm ci --silent 2>/dev/null || npm install --silent

# 复制源码并构建
COPY . /build
RUN npm run build

# 运行阶段：Nginx 提供静态文件服务 + API 反向代理
FROM nginx:alpine

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 覆盖 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:3000/ || exit 1

EXPOSE 3000
ENTRYPOINT ["nginx", "-g", "daemon off;"]
