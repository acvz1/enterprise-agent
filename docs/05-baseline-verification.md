# 基线验证记录

日期：2026-07-13

## 环境

- JDK：Oracle JDK 21.0.10（项目编译目标 Java 21）
- Maven Wrapper：3.9.10
- Node.js：24.15.0
- npm：11.12.1
- Docker：28.5.2 / Compose 2.40.3

## 源码完整性

- `origin`：个人仓库，用于提交二次开发代码。
- `upstream`：`2518350LJL/ai-knowledge-base`，只用于获取原项目。
- 上游固定提交：`82fa41079387b3450787d709b6a6efd17b45c00e`。
- 当前已恢复 72 个主 Java 文件、29 个前端源码文件、Maven Wrapper 和 MySQL 初始化脚本。
- 没有恢复上游误提交的根目录 `node_modules`、GraalVM 配置、性能测试和大批教程。

## 已通过

### 后端单元测试

```powershell
# 当前电脑默认是 JDK 25，测试时只对本终端临时切换到 JDK 21
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.m2-cache'

.\mvnw.cmd "-Dmaven.repo.local=.m2-cache/repository" test
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

`npm ci` 同时报告上游依赖树存在 17 个安全告警（1 low、6 moderate、9 high、1 critical）。本轮没有直接执行可能引入破坏性升级的 `npm audit fix --force`，把它记录为后续依赖治理项。

### 静态配置

- `pom.xml` XML 解析通过。
- `docker compose -f docker/docker-compose.yml config --quiet` 通过。
- `git diff --check` 通过。

## 基线阶段发现并修复的验证问题

1. 个人仓库最初只提交了少量修改文件，缺少大部分 Service、Entity、Repository 和前端源码；现已从固定上游提交补全。
2. 当前电脑默认 JDK 25，与项目的 Hibernate 增强插件不兼容；项目验证固定使用 JDK 21。
3. 上游 `.mvn/jvm.config` 硬编码不存在的 GraalVM 路径，导致 Maven 无法启动。
4. 空壳 `contextLoads` 默认连接本机 MySQL，却没有业务断言；已经删除，完整上下文验证交给 Testcontainers 集成测试。
5. Testcontainers 集成测试混入 Surefire 单测阶段。
6. Mockito 测试漏注入 `MetricsService`。
7. 向量服务单测靠 Redis 连接失败触发 fallback，既慢又产生大量错误日志。
8. 前端同时提交 npm/pnpm 两套锁，且 pnpm 锁与 `package.json` 不一致。
9. npm 锁文件固定 npmmirror；项目级 `.npmrc` 现在会把下载 host 映射到明确 registry。
