# 基线验证记录

日期：2026-07-13

## 环境

- JDK：Oracle JDK 21.0.10（项目编译目标 Java 21）
- Maven Wrapper：3.9.10
- Node.js：24.15.0
- npm：11.12.1
- Docker：28.5.2 / Compose 2.40.3

## 已通过

### 后端单元测试

```powershell
.\mvnw.cmd test
```

结果：`BUILD SUCCESS`；18 个测试，0 failure，0 error，5 skipped。跳过项全部来自 `DocumentProcessingServiceTest`，原因是当前异步上传实现存在已确认的 AOP 自调用和临时文件生命周期问题，留待阶段 1 重构后重新启用。

集成测试已按 Maven 约定重命名为 `*IT`，只在下列命令中运行：

```powershell
.\mvnw.cmd verify
```

它需要可用 Docker，并通过 Testcontainers 启动 MySQL 与 Redis Stack。本次受运行环境 Docker 配置权限限制，没有把集成测试成功计入基线结论。

### 前端

```powershell
npm.cmd ci
npm.cmd run type-check
npm.cmd run build-only
```

结果：TypeScript 类型检查通过；Vite 生产构建通过，4780 modules transformed。构建提示主 chunk 大于 500 kB（约 2.07 MB，gzip 643 kB），属于后续按路由/组件拆包的优化项，不阻塞当前二开。

### 静态配置

- `pom.xml` XML 解析通过。
- `docker compose -f docker/docker-compose.yml config --quiet` 通过。
- `git diff --check` 通过。

## 基线阶段发现并修复的验证问题

1. 上游 `.mvn/jvm.config` 硬编码不存在的 GraalVM 路径，导致 Maven 无法启动。
2. `contextLoads` 默认连接本机 MySQL，使单元测试无法独立运行。
3. Testcontainers 集成测试混入 Surefire 单测阶段。
4. Mockito 测试漏注入 `MetricsService`。
5. 向量服务单测靠 Redis 连接失败触发 fallback，既慢又产生大量错误日志。
6. 前端同时提交 npm/pnpm 两套锁，且 pnpm 锁与 `package.json` 不一致。
7. npm 锁文件固定 npmmirror；项目级 `.npmrc` 现在会把下载 host 映射到明确 registry。
