# 后端 Spring Boot 服务 Dockerfile
# 多阶段构建：Maven 编译 + JRE 运行

FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先复制父 POM 和子模块 POM，利用 Docker 缓存层加速
COPY pom.xml /build/pom.xml
COPY ai-java-main/pom.xml /build/ai-java-main/pom.xml

# 下载依赖（在源码变更时复用缓存）
RUN mvn dependency:go-offline -pl ai-java-main -B -q || true

# 复制源码并构建
COPY ai-java-main/src /build/ai-java-main/src
RUN mvn package -pl ai-java-main -DskipTests -B -q

# 运行阶段
FROM eclipse-temurin:17-jre
WORKDIR /app

# 复制构建产物
COPY --from=builder /build/ai-java-main/target/*.jar /app/app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD java -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher 2>&1 || \
  wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
