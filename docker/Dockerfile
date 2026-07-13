# ========================================
# 多阶段构建 Dockerfile（JVM 版本）
# ========================================
# 第一阶段：Maven 构建
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 复制 Maven 配置文件（利用 Docker 层缓存）
COPY pom.xml .

# 复制源代码
COPY src ./src

# CI/本地已单独运行测试；镜像阶段只负责可重复打包
RUN mvn package -DskipTests -B

# 解压 JAR 以优化分层
RUN mkdir -p target/dependency && \
    cd target/dependency && \
    jar -xf ../*.jar

# ========================================
# 第二阶段：运行时镜像
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="ai-knowledge-base-team"
LABEL description="AI Knowledge Base Application - JVM Edition"
LABEL version="1.0.0"

WORKDIR /app

# 创建非 root 用户运行应用（安全最佳实践）
RUN addgroup -S spring && adduser -S spring -G spring

# 安装必要的运行时工具
RUN apk add --no-cache \
    curl \
    tzdata \
    && rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段复制分层的依赖（优化启动速度）
COPY --from=builder /build/target/dependency/BOOT-INF/lib /app/lib
COPY --from=builder /build/target/dependency/META-INF /app/META-INF
COPY --from=builder /build/target/dependency/BOOT-INF/classes /app

# 修改所有者为 spring 用户
RUN chown -R spring:spring /app

# 切换到非 root 用户
USER spring:spring

# 暴露端口
EXPOSE 8080

# JVM 参数优化
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp /app:/app/lib/* com.kb.demo.AiKnowledgeBaseApplication"]
