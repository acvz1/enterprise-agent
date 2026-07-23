# enterprise-agent 项目摸底报告

## 基本信息

| 字段 | 值 |
| --- | --- |
| repo_path | D:\Project\enterprise-agent |
| generated_at | 2026-07-20T04:50:33.935265+00:00 |
| file_count_scanned | 3815 |
| approx_total_bytes | 462603801 |

## 语言和文件类型

| 语言 | 文件数 |
| --- | --- |
| Other | 3706 |
| Java | 81 |
| YAML | 10 |
| TypeScript | 8 |
| Python | 6 |
| SQL | 3 |
| JavaScript | 1 |

## 依赖和环境线索

- `ai-assistant-front/package.json`
- `ai-assistant-front/tsconfig.json`
- `ai-assistant-front/vite.config.ts`
- `docker/Dockerfile`
- `docker/docker-compose.yml`
- `pom.xml`

## README

- `readme.md`
- `.m2-cache/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/README.txt`
- `.m2-cache/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/lib/ext/README.txt`
- `.m2-cache/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/lib/ext/hazelcast/README.txt`
- `.m2-cache/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/lib/ext/redisson/README.txt`
- `.m2-cache/wrapper/dists/apache-maven-3.9.10/a38810a491b03367137adfdfbe7d14c4/lib/jansi-native/README.txt`
- `.tmp/project-reading-coach-update-v2/README.md`
- `.tmp/project-reading-coach-update-v3/README.md`
- `.tmp/project-reading-coach-update-v4/README.md`
- `docs/README.md`

## 核心链路线索

| 类别 | 命中文件数 | 代表路径 |
| --- | --- | --- |
| api_backend | 40 | .m2-cache/repository/ai/djl/api/0.28.0/_remote.repositories<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar.sha1<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom.sha1<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/_remote.repositories<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar.sha1 |
| async_jobs | 11 | src/main/java/com/kb/demo/service/DocumentProcessingWorker.java<br>src/test/java/com/kb/demo/service/DocumentProcessingWorkerAsyncTest.java<br>src/test/java/com/kb/demo/service/DocumentProcessingWorkerTest.java<br>target/classes/com/kb/demo/service/DocumentProcessingWorker.class<br>target/surefire-reports/TEST-com.kb.demo.service.DocumentProcessingWorkerAsyncTest.xml<br>target/surefire-reports/TEST-com.kb.demo.service.DocumentProcessingWorkerTest.xml<br>target/surefire-reports/com.kb.demo.service.DocumentProcessingWorkerAsyncTest.txt<br>target/surefire-reports/com.kb.demo.service.DocumentProcessingWorkerTest.txt |
| config | 40 | .m2-cache/repository/org/apache/maven/maven-settings-builder/3.2.5/_remote.repositories<br>.m2-cache/repository/org/apache/maven/maven-settings-builder/3.2.5/maven-settings-builder-3.2.5.pom<br>.m2-cache/repository/org/apache/maven/maven-settings-builder/3.2.5/maven-settings-builder-3.2.5.pom.sha1<br>.m2-cache/repository/org/apache/maven/maven-settings/3.2.5/_remote.repositories<br>.m2-cache/repository/org/apache/maven/maven-settings/3.2.5/maven-settings-3.2.5.pom<br>.m2-cache/repository/org/apache/maven/maven-settings/3.2.5/maven-settings-3.2.5.pom.sha1<br>.m2-cache/repository/org/infinispan/infinispan-build-configuration-parent/14.0.31.Final/_remote.repositories<br>.m2-cache/repository/org/infinispan/infinispan-build-configuration-parent/14.0.31.Final/infinispan-build-configuration-parent-14.0.31.Final.pom |
| data_pipeline | 5 | .m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/_remote.repositories<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.jar<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.jar.sha1<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.pom<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.pom.sha1 |
| database_state | 40 | .m2-cache/repository/ai/djl/api/0.28.0/_remote.repositories<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar.sha1<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom.sha1<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/_remote.repositories<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.jar<br>.m2-cache/repository/ai/djl/huggingface/tokenizers/0.28.0/tokenizers-0.28.0.jar.sha1 |
| devops_deploy | 40 | .m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/_remote.repositories<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar.sha1<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.pom<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.pom.sha1<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/_remote.repositories<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/docker-java-api-3.3.6.pom<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/docker-java-api-3.3.6.pom.sha1 |
| evaluation | 40 | .m2-cache/repository/io/dropwizard/metrics/metrics-bom/4.2.27/_remote.repositories<br>.m2-cache/repository/io/dropwizard/metrics/metrics-bom/4.2.27/metrics-bom-4.2.27.pom<br>.m2-cache/repository/io/dropwizard/metrics/metrics-bom/4.2.27/metrics-bom-4.2.27.pom.sha1<br>.m2-cache/repository/io/dropwizard/metrics/metrics-parent/4.2.27/_remote.repositories<br>.m2-cache/repository/io/dropwizard/metrics/metrics-parent/4.2.27/metrics-parent-4.2.27.pom<br>.m2-cache/repository/io/dropwizard/metrics/metrics-parent/4.2.27/metrics-parent-4.2.27.pom.sha1<br>.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/_remote.repositories<br>.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar |
| frontend_mobile | 40 | .m2-cache/repository/org/apache/maven/shared/maven-shared-components/15/_remote.repositories<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/15/maven-shared-components-15.pom<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/15/maven-shared-components-15.pom.sha1<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/19/_remote.repositories<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/19/maven-shared-components-19.pom<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/19/maven-shared-components-19.pom.sha1<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/34/_remote.repositories<br>.m2-cache/repository/org/apache/maven/shared/maven-shared-components/34/maven-shared-components-34.pom |
| inference_demo | 40 | .m2-cache/repository/ai/djl/api/0.28.0/_remote.repositories<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.jar.sha1<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom<br>.m2-cache/repository/ai/djl/api/0.28.0/api-0.28.0.pom.sha1<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/_remote.repositories<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar<br>.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar.sha1 |
| model | 40 | .m2-cache/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.15.4/_remote.repositories<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.15.4/jackson-module-parameter-names-2.15.4.jar<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.15.4/jackson-module-parameter-names-2.15.4.jar.sha1<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.15.4/jackson-module-parameter-names-2.15.4.pom<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-module-parameter-names/2.15.4/jackson-module-parameter-names-2.15.4.pom.sha1<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-modules-java8/2.15.4/_remote.repositories<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-modules-java8/2.15.4/jackson-modules-java8-2.15.4.pom<br>.m2-cache/repository/com/fasterxml/jackson/module/jackson-modules-java8/2.15.4/jackson-modules-java8-2.15.4.pom.sha1 |
| security_auth | 40 | .m2-cache/repository/com/healthmarketscience/jackcess/jackcess-encrypt/4.0.2/_remote.repositories<br>.m2-cache/repository/com/healthmarketscience/jackcess/jackcess-encrypt/4.0.2/jackcess-encrypt-4.0.2.jar<br>.m2-cache/repository/com/healthmarketscience/jackcess/jackcess-encrypt/4.0.2/jackcess-encrypt-4.0.2.jar.sha1<br>.m2-cache/repository/com/healthmarketscience/jackcess/jackcess-encrypt/4.0.2/jackcess-encrypt-4.0.2.pom<br>.m2-cache/repository/com/healthmarketscience/jackcess/jackcess-encrypt/4.0.2/jackcess-encrypt-4.0.2.pom.sha1<br>.m2-cache/repository/io/jsonwebtoken/jjwt-api/0.12.3/_remote.repositories<br>.m2-cache/repository/io/jsonwebtoken/jjwt-api/0.12.3/jjwt-api-0.12.3.jar<br>.m2-cache/repository/io/jsonwebtoken/jjwt-api/0.12.3/jjwt-api-0.12.3.jar.sha1 |
| testing_quality | 40 | .m2-cache/repository/org/aspectj/aspectjweaver/1.9.22.1/_remote.repositories<br>.m2-cache/repository/org/aspectj/aspectjweaver/1.9.22.1/aspectjweaver-1.9.22.1.jar<br>.m2-cache/repository/org/aspectj/aspectjweaver/1.9.22.1/aspectjweaver-1.9.22.1.jar.sha1<br>.m2-cache/repository/org/aspectj/aspectjweaver/1.9.22.1/aspectjweaver-1.9.22.1.pom<br>.m2-cache/repository/org/aspectj/aspectjweaver/1.9.22.1/aspectjweaver-1.9.22.1.pom.sha1<br>.m2-cache/repository/org/mockito/mockito-bom/4.11.0/_remote.repositories<br>.m2-cache/repository/org/mockito/mockito-bom/4.11.0/mockito-bom-4.11.0.pom<br>.m2-cache/repository/org/mockito/mockito-bom/4.11.0/mockito-bom-4.11.0.pom.sha1 |
| training | 10 | .m2-cache/repository/com/squareup/retrofit2/converter-jackson/2.9.0/_remote.repositories<br>.m2-cache/repository/com/squareup/retrofit2/converter-jackson/2.9.0/converter-jackson-2.9.0.jar<br>.m2-cache/repository/com/squareup/retrofit2/converter-jackson/2.9.0/converter-jackson-2.9.0.jar.sha1<br>.m2-cache/repository/com/squareup/retrofit2/converter-jackson/2.9.0/converter-jackson-2.9.0.pom<br>.m2-cache/repository/com/squareup/retrofit2/converter-jackson/2.9.0/converter-jackson-2.9.0.pom.sha1<br>.m2-cache/repository/com/squareup/retrofit2/retrofit/2.9.0/_remote.repositories<br>.m2-cache/repository/com/squareup/retrofit2/retrofit/2.9.0/retrofit-2.9.0.jar<br>.m2-cache/repository/com/squareup/retrofit2/retrofit/2.9.0/retrofit-2.9.0.jar.sha1 |

## Notebook / Docker / Test 线索

### Notebooks
- 无

### Docker
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/_remote.repositories`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.jar.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.pom`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.4/docker-java-api-3.3.4.pom.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/_remote.repositories`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/docker-java-api-3.3.6.pom`
- `.m2-cache/repository/com/github/docker-java/docker-java-api/3.3.6/docker-java-api-3.3.6.pom.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.4/_remote.repositories`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.4/docker-java-parent-3.3.4.pom`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.4/docker-java-parent-3.3.4.pom.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.6/_remote.repositories`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.6/docker-java-parent-3.3.6.pom`
- `.m2-cache/repository/com/github/docker-java/docker-java-parent/3.3.6/docker-java-parent-3.3.6.pom.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.4/_remote.repositories`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.4/docker-java-transport-zerodep-3.3.4.jar`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.4/docker-java-transport-zerodep-3.3.4.jar.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.4/docker-java-transport-zerodep-3.3.4.pom`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.4/docker-java-transport-zerodep-3.3.4.pom.sha1`
- `.m2-cache/repository/com/github/docker-java/docker-java-transport-zerodep/3.3.6/_remote.repositories`

### Tests
- `.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar.sha1`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.pom`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.pom.sha1`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar.sha1`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.pom`
- `.m2-cache/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.pom.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-starter-test/3.2.10/spring-boot-starter-test-3.2.10.jar`
- `.m2-cache/repository/org/springframework/boot/spring-boot-starter-test/3.2.10/spring-boot-starter-test-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-starter-test/3.2.10/spring-boot-starter-test-3.2.10.pom`
- `.m2-cache/repository/org/springframework/boot/spring-boot-starter-test/3.2.10/spring-boot-starter-test-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test-autoconfigure/3.2.10/spring-boot-test-autoconfigure-3.2.10.jar`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test-autoconfigure/3.2.10/spring-boot-test-autoconfigure-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test-autoconfigure/3.2.10/spring-boot-test-autoconfigure-3.2.10.pom`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test-autoconfigure/3.2.10/spring-boot-test-autoconfigure-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test/3.2.10/spring-boot-test-3.2.10.jar`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test/3.2.10/spring-boot-test-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test/3.2.10/spring-boot-test-3.2.10.pom`
- `.m2-cache/repository/org/springframework/boot/spring-boot-test/3.2.10/spring-boot-test-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/spring-test/6.1.13/spring-test-6.1.13.jar`
- `.m2-cache/repository/org/springframework/spring-test/6.1.13/spring-test-6.1.13.jar.sha1`
- `.m2-cache/repository/org/springframework/spring-test/6.1.13/spring-test-6.1.13.pom`
- `.m2-cache/repository/org/springframework/spring-test/6.1.13/spring-test-6.1.13.pom.sha1`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.19.1/testcontainers-bom-1.19.1.pom`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.19.1/testcontainers-bom-1.19.1.pom.sha1`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.19.8/testcontainers-bom-1.19.8.pom`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.19.8/testcontainers-bom-1.19.8.pom.sha1`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.20.0/testcontainers-bom-1.20.0.pom`
- `.m2-cache/repository/org/testcontainers/testcontainers-bom/1.20.0/testcontainers-bom-1.20.0.pom.sha1`

## 潜在数据/状态/模型/资源路径

- `.m2-cache/repository/org/springframework/data/spring-data-bom/2023.1.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-bom/2023.1.10/spring-data-bom-2023.1.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-bom/2023.1.10/spring-data-bom-2023.1.10.pom.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-commons/3.2.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-commons/3.2.10/spring-data-commons-3.2.10.jar`
- `.m2-cache/repository/org/springframework/data/spring-data-commons/3.2.10/spring-data-commons-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-commons/3.2.10/spring-data-commons-3.2.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-commons/3.2.10/spring-data-commons-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa-parent/3.2.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa-parent/3.2.10/spring-data-jpa-parent-3.2.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa-parent/3.2.10/spring-data-jpa-parent-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa/3.2.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa/3.2.10/spring-data-jpa-3.2.10.jar`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa/3.2.10/spring-data-jpa-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa/3.2.10/spring-data-jpa-3.2.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-jpa/3.2.10/spring-data-jpa-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-keyvalue/3.2.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-keyvalue/3.2.10/spring-data-keyvalue-3.2.10.jar`
- `.m2-cache/repository/org/springframework/data/spring-data-keyvalue/3.2.10/spring-data-keyvalue-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-keyvalue/3.2.10/spring-data-keyvalue-3.2.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-keyvalue/3.2.10/spring-data-keyvalue-3.2.10.pom.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-redis/3.2.10/_remote.repositories`
- `.m2-cache/repository/org/springframework/data/spring-data-redis/3.2.10/spring-data-redis-3.2.10.jar`
- `.m2-cache/repository/org/springframework/data/spring-data-redis/3.2.10/spring-data-redis-3.2.10.jar.sha1`
- `.m2-cache/repository/org/springframework/data/spring-data-redis/3.2.10/spring-data-redis-3.2.10.pom`
- `.m2-cache/repository/org/springframework/data/spring-data-redis/3.2.10/spring-data-redis-3.2.10.pom.sha1`
- `.npm-cache/_cacache/content-v2/sha512/49/db/0a32b23d4dd823770794491f4cc1e1c0e0427c6311e7f0315a0e2b2f85595439ee01175b4b0fb1808f4948a96565f9d3dbfeb131af406d6f2e65a109b6d1`
- `.npm-cache/_cacache/content-v2/sha512/db/07/02fe81b11e2b3f0681e490fc2576088f49872964adc9536f16a0c56fe79c87260719f2b957bee1bd899820cfeb14d5de1b460206012c325aa8f1bb714cd2`
- `.npm-cache/_cacache/content-v2/sha512/db/e4/8cc6d1b53dcb0bd2eca192e9a5394e90a3d4c043f5b21fbb6d8673c8390074e7fd768f3f676aae8667a899e80a55b0edebaf682c9d37f7fbe395eb7904d5`
- `.npm-cache/_cacache/content-v2/sha512/db/ed/6d8465145968cb4d84c76562b59fc6206b48a3073d5702151770acbcd6b77983aca1ee11aa329e39bfb71a7526c41dcf46dce4efe394b29de44dde9b380d`

## 目录树摘要

```text
enterprise-agent/
  .agents/
  .m2-cache/
  .mvn/
  .npm-cache/
  .omc/
  .tmp/
  ai-assistant-front/
  docker/
  docs/
  mysql/
  src/
  target/
  .env.template
  .gitignore
  LICENSE
  UPSTREAM_NOTICE.md
  mvnw
  mvnw.cmd
  pom.xml
  readme.md
    repository/
    wrapper/
      ai/
      aopalliance/
      ch/
      com/
      commons-codec/
      commons-io/
      commons-logging/
      dev/
      io/
      jakarta/
      javax/
      junit/
      net/
      org/
      redis/
      software/
      xerces/
      xml-apis/
      dists/
    wrapper/
      maven-wrapper.properties
    _cacache/
    _logs/
    _update-notifier-last-checked
      content-v2/
      index-v5/
      tmp/
      2026-07-13T07_16_52_838Z-debug-0.log
      2026-07-13T07_20_03_747Z-debug-0.log
      2026-07-13T07_22_03_808Z-debug-0.log
      2026-07-13T07_22_09_334Z-debug-0.log
      2026-07-13T07_22_09_361Z-debug-0.log
      2026-07-13T15_47_26_200Z-debug-0.log
    sessions/
    state/
    project-memory.json
      7188468e-a214-4299-9f70-fe0c31f64081.json
      fde5dbe9-81dd-48ee-9051-2f9f30dbbb27.json
      sessions/
    project-reading-coach/
    project-reading-coach-cognitive-update/
    project-reading-coach-occam-update/
    project-reading-coach-update/
    project-reading-coach-update-v2/
    project-reading-coach-update-v3/
    project-reading-coach-update-v4/
    LearnWhat-后端学习笔记.before-prune-20260715-200947.md
    LearnWhat-后端学习笔记.current.md
    LearnWhat-后端学习笔记.md
    list-index-test.md
    plain-index-test.md
    project-reading-coach-SKILL-before-20260716.md
    project-reading-coach-SKILL-before-cognitive-update-20260719.md
    project-reading-coach-SKILL-before-occam-20260719.md
    企业知识库Agent项目学习笔记.before-20260716.md
    企业知识库Agent项目学习笔记.current.md
    企业知识库Agent项目学习笔记.full-before-skill-rewrite-20260716.md
    企业知识库Agent项目学习笔记.skill-rewrite.md
      SKILL.md
      agents/
      references/
      scripts/
      SKILL.md
      agents/
      references/
      scripts/
      SKILL.md
      agents/
      references/
      scripts/
      SKILL.md
      agents/
      references/
      scripts/
      .SKILL.backup-20260715-160659.md
      LICENSE
      README.md
      SKILL.md
      agents/
      references/
      scripts/
      .SKILL.backup-20260715-160659.md
      LICENSE
      README.md
      SKILL.md
      agents/
      references/
      scripts/
      .SKILL.backup-20260715-160659.md
      LICENSE
      README.md
      SKILL.md
    public/
    src/
    .editorconfig
    .gitattributes
    .gitignore
    .npmrc
    .prettierrc.json
    env.d.ts
    eslint.config.ts
    index.html
    package-lock.json
    package.json
    tailwind.config.js
    tsconfig.app.json
    tsconfig.json
    tsconfig.node.json
    vite.config.ts
      favicon.ico
      assets/
      components/
      router/
      services/
      stores/
      views/
      App.vue
      main.ts
    prometheus/
    Dockerfile
    docker-compose.yml
      prometheus.yml
    adr/
    00-start-here.md
    01-upstream-audit.md
    02-architecture-and-call-chain.md
    03-secondary-development-roadmap.md
    04-interview-ownership.md
    05-baseline-verification.md
    06-stage-1-reliable-ingestion.md
    README.md
      0001-use-java-for-ai-backend.md
    02-add-evaluation-tables.sql
    add-file-type-column.sql
    init.sql
    main/
    test/
      java/
      resources/
      java/
    classes/
    generated-sources/
    generated-test-sources/
    maven-status/
    surefire-reports/
    test-classes/
    jacoco.exec
      META-INF/
      com/
      application.yml
      annotations/
      test-annotations/
      maven-compiler-plugin/
      2026-07-14T20-43-37_681.dumpstream
      2026-07-14T20-48-38_779.dumpstream
      2026-07-14T20-50-33_308.dumpstream
      2026-07-14T20-54-50_359.dumpstream
      2026-07-14T20-56-04_130.dumpstream
      2026-07-14T21-03-56_651.dumpstream
      TEST-com.kb.demo.service.DocumentFileStorageTest.xml
      TEST-com.kb.demo.service.DocumentProcessingServiceTest.xml
      TEST-com.kb.demo.service.DocumentProcessingWorkerAsyncTest.xml
      TEST-com.kb.demo.service.DocumentProcessingWorkerTest.xml
      TEST-com.kb.demo.service.ResponseEvaluationServiceTest.xml
      TEST-com.kb.demo.service.VectorSearchServiceTest.xml
      com.kb.demo.service.DocumentFileStorageTest.txt
      com.kb.demo.service.DocumentProcessingServiceTest.txt
      com.kb.demo.service.DocumentProcessingWorkerAsyncTest.txt
      com.kb.demo.service.DocumentProcessingWorkerTest.txt
      com.kb.demo.service.ResponseEvaluationServiceTest.txt
      com.kb.demo.service.VectorSearchServiceTest.txt
      com/
```

## 下一步人工确认

- 找到最小可运行命令：API、页面、CLI、worker、测试、训练或 demo 至少一个。
- 确认依赖、环境变量、数据库/数据文件、端口和外部服务。
- 确认 baseline/demo 是否能在本地、Docker、云服务器或 GPU 环境上跑通。
- 确认自己要做的面试亮点：改造点、demo、测试、报告或实验计划。
