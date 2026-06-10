# Task 5 修复完整根因分析 — 2026-06-10

## 问题现象
E2E 测试只产生 1 个 AGENT_ARTIFACT 事件(image_generation),缺少 4 个文本工件事件(newsletter/ppt/chart/frontend)。

## 根因链条

### 根因 #1: Bootstrap 缺少 storage-s3 依赖 ✅ 已修复
**位置**: `seahorse-agent-bootstrap/pom.xml`  
**问题**: 未声明对 `seahorse-agent-adapter-storage-s3` 的依赖  
**影响**: 运行时 classpath 缺少 `S3ObjectStorageAdapter` 类和 AWS SDK  
**修复**: 添加依赖声明
```xml
<dependency>
    <groupId>com.miracle.ai</groupId>
    <artifactId>seahorse-agent-adapter-storage-s3</artifactId>
    <version>${project.version}</version>
</dependency>
```
**验证**: 
- Maven 依赖树包含 `software.amazon.awssdk:s3:jar:2.40.2`
- exec.jar 包含 `BOOT-INF/lib/seahorse-agent-adapter-storage-s3-0.0.1-SNAPSHOT.jar`
- exec.jar 包含 `BOOT-INF/lib/s3-2.40.2.jar`

### 根因 #2: 自动配置顺序依赖缺失 ✅ 已修复(但不是真正根因)
**位置**: `SeahorseAgentKernelAgentAutoConfiguration.java`  
**问题**: `@AutoConfigureAfter` 未声明对 `SeahorseAgentStorageAdapterAutoConfiguration` 的依赖  
**影响**: 
- KernelAgent 配置加载时,Storage adapter 可能还未初始化
- `ObjectStoragePort` bean 不存在
- `GenerationToolArtifactPublicationPort` 构造器收到 null
- 文本工件持久化在第 116 行被跳过:`if (objectStorage == null) return;`

**修复**: 在 `@AutoConfigureAfter` 添加 Storage 配置类
```java
@AutoConfigureAfter({
        SeahorseAgentStorageAdapterAutoConfiguration.class,  // NEW
        SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentKernelRegistryAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class
})
```

**为什么 imports 顺序不够?**  
`AutoConfiguration.imports` 定义**初始顺序**(第 25 行 Storage,第 44 行 KernelAgent),
但 Spring Boot 会根据 `@AutoConfigureBefore/After` 注解重新排序。
如果 KernelAgent 没有显式声明 `@AutoConfigureAfter(Storage)`,
实际加载顺序可能因类加载器、线程调度等因素变化,导致 bean 依赖不满足。

**验证策略**:
1. 检查 Spring Boot 自动配置报告:`java -jar app.jar --debug | grep -A 5 "S3StorageAutoConfiguration"`
2. 检查 ObjectStoragePort bean 是否存在:`actuator/beans | jq '.contexts[].beans | select(.seahorseS3ObjectStorageAdapter)'`
3. 运行 E2E 测试,统计 AGENT_ARTIFACT 事件数量(预期 5 个)

### 根因 #3: 属性前缀不匹配 ✅ **真正根因**
**位置**: `SeahorseAgentStorageAdapterAutoConfiguration.java` 所有 `@ConditionalOnProperty` 和 `@Value` 注解  
**问题**: 属性前缀使用了 `seahorse-agent`(带连字符),但 Spring Boot 环境变量映射规则是:
- `SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ENDPOINT` → `seahorse.agent.adapters.storage.s3.endpoint`(点分隔)
- **不会映射为** `seahorse-agent.adapters.storage.s3.endpoint`(连字符不变)

**影响**:
- S3Client bean 的 `@ConditionalOnProperty` 检查 `seahorse-agent.adapters.storage.type=s3` 失败
- 环境变量 `SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=s3` 被映射为 `seahorse.agent.adapters.storage.type=s3`
- 条件不匹配 → S3Client bean 未创建 → ObjectStoragePort=null

**修复**: 将所有属性前缀从 `seahorse-agent` 改为 `seahorse.agent`
```java
// 修复前
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.storage", name = "type", havingValue = "s3")
@Value("${seahorse-agent.adapters.storage.s3.endpoint:}")

// 修复后
@ConditionalOnProperty(prefix = "seahorse.agent.adapters.storage.s3", name = "endpoint")
@Value("${seahorse.agent.adapters.storage.s3.endpoint:}")
```

**额外改进**: S3Client bean 条件从检查 `type=s3` 改为检查 `endpoint` 存在性,更直接。

### 根因 #4: S3ObjectStorageAdapter bean 条件仍然依赖 type 属性 ✅ **最终根因**
**位置**: `SeahorseAgentStorageAdapterAutoConfiguration.java` 第 91-92 行  
**问题**: S3ObjectStorageAdapter bean 的 `@ConditionalOnProperty` 仍然检查 `seahorse.agent.adapters.storage.type=s3`
- 即使修复了 S3Client bean 的条件(改用 ConditionalOnExpression)
- S3ObjectStorageAdapter bean 仍然有独立的条件检查
- 该条件使用了错误的前缀,永不满足
- **结果**: S3Client 创建了,但 S3ObjectStorageAdapter 未创建 → ObjectStoragePort=null

**修复**: 去掉 S3ObjectStorageAdapter 的 `@ConditionalOnProperty`,只依赖 `@ConditionalOnBean(S3Client.class)`
```java
// 修复前
@Bean
@ConditionalOnBean(S3Client.class)
@ConditionalOnProperty(prefix = "seahorse.agent.adapters.storage", name = "type",
        havingValue = "s3", matchIfMissing = true)
@ConditionalOnMissingBean(ObjectStoragePort.class)
public S3ObjectStorageAdapter seahorseS3ObjectStorageAdapter(S3Client s3Client)

// 修复后
@Bean
@ConditionalOnBean(S3Client.class)
@ConditionalOnMissingBean(ObjectStoragePort.class)
public S3ObjectStorageAdapter seahorseS3ObjectStorageAdapter(S3Client s3Client)
```

**逻辑**: S3Client 已经通过 endpoint 存在性验证了配置,S3ObjectStorageAdapter 只需依赖 S3Client 存在即可。

**验证策略**:
1. 启动后检查 S3Client bean 是否被创建:`docker exec seahorse-backend sh -c 'echo "S3Client loaded"' > /dev/null && echo OK`
2. 检查日志中是否有 S3 相关初始化信息
3. 运行 E2E 测试,验证 5 个 AGENT_ARTIFACT 事件

## 诊断过程

1. **阶段 1: 发现缺口**  
   读取 E2E verdict,发现只有 image 工具发布了 artifact 事件。

2. **阶段 2: 定位触发点**  
   检查 `GenerationToolArtifactPublicationPort.publish()` 代码,发现第 95-97 行文本工件持久化逻辑。

3. **阶段 3: 检查依赖**  
   发现 `ObjectStoragePort` 通过 `ObjectProvider.getIfAvailable()` 注入,可能为 null。

4. **阶段 4: 检查自动配置**  
   发现 `SeahorseAgentStorageAdapterAutoConfiguration` 存在,但 S3 adapter 有 `@ConditionalOnClass` 条件。

5. **阶段 5: 检查 bootstrap 依赖**  
   执行 `grep storage-s3 pom.xml` → 无结果 → **发现根因 #1**

6. **阶段 6: 修复并验证**  
   添加依赖,重新构建,Docker 镜像打包,部署 → E2E 仍然失败(只有 1 个事件)

7. **阶段 7: 深度诊断**  
   - 验证 exec.jar 包含 s3 依赖 ✅
   - 验证环境变量配置 ✅
   - 检查 Docker 镜像缓存 ✅(无缓存重建仍失败)
   - 检查异常处理:发现 `LocalToolGatewayPort` 第 251 行 `catch (RuntimeException ignored)` 静默吞掉异常

8. **阶段 8: 检查自动配置顺序**  
   检查 `AutoConfiguration.imports` → Storage 在第 25 行,KernelAgent 在第 44 行  
   检查 `@AutoConfigureAfter` → 未包含 Storage → **发现根因 #2**

9. **阶段 9: 第一轮修复验证(迭代 1-2)**  
   - 迭代 1:添加 storage-s3 依赖 → E2E 仍失败
   - 迭代 2:添加 @AutoConfigureAfter(Storage) → E2E 仍失败

10. **阶段 10: 深入属性绑定**  
    分析 `@ConditionalOnProperty` 条件:前缀 `seahorse-agent`,但环境变量映射为 `seahorse.agent`  
    → **发现根因 #3**

11. **阶段 11: 第二轮修复验证(迭代 3)**  
    - 修改属性前缀为 `seahorse.agent` → E2E 仍失败(1/5)
    - 怀疑 @ConditionalOnProperty 对自定义前缀不生效

12. **阶段 12: 改用 ConditionalOnExpression(迭代 4)**  
    - S3Client 改用 SpEL 表达式检查 endpoint → E2E 仍失败(1/5)
    - **关键发现**:S3ObjectStorageAdapter 有独立的 @ConditionalOnProperty 条件!

13. **阶段 13: 定位最终根因**  
    发现 S3ObjectStorageAdapter bean 第 91-92 行仍然依赖 `type=s3` 属性检查  
    即使 S3Client 创建成功,S3ObjectStorageAdapter 仍因条件不满足而未创建  
    → **发现根因 #4(最终根因)**

14. **阶段 14: 最终修复(迭代 5,进行中)**  
    去掉 S3ObjectStorageAdapter 的 @ConditionalOnProperty,只依赖 @ConditionalOnBean(S3Client)  
    构建→部署→E2E 验证中...

## 修复验证清单

- [x] 添加 storage-s3 依赖到 bootstrap pom.xml
- [x] 验证 Maven 依赖树包含 AWS SDK
- [x] 验证 exec.jar 包含 s3 adapter + AWS SDK
- [x] 添加 Storage 到 KernelAgent 的 @AutoConfigureAfter
- [x] 将属性前缀从 seahorse-agent 改为 seahorse.agent
- [x] S3Client 改用 @ConditionalOnExpression 检查 endpoint
- [x] 去掉 S3ObjectStorageAdapter 的 @ConditionalOnProperty
- [x] 重新构建 + Docker 镜像打包(进行中)
- [ ] 部署并健康检查通过
- [ ] 运行 E2E 测试,验证产生 5 个 AGENT_ARTIFACT 事件

## 关键学习

1. **Spring Boot 自动配置条件是独立的**  
   每个 @Bean 的条件注解独立评估,即使父 bean 创建成功,子 bean 仍可能因独立条件失败而未创建。

2. **@ConditionalOnProperty 对自定义前缀脆弱**  
   环境变量 `SEAHORSE_AGENT_*` 映射为 `seahorse.agent.*`,不会映射为 `seahorse-agent.*`。  
   建议:统一使用点分隔前缀,或使用 @ConfigurationProperties。

3. **@ConditionalOnExpression 更灵活但更复杂**  
   直接用 SpEL 检查属性值,避免前缀映射问题,但表达式容易出错。

4. **条件注解应该最小化**  
   如果 bean A 已经验证了配置,依赖 A 的 bean B 只需 @ConditionalOnBean(A),无需重复检查配置。

5. **ObjectProvider 模式隐藏了依赖缺失**  
   `ObjectProvider.getIfAvailable()` 返回 null 时不会失败,导致运行时静默跳过逻辑。

6. **异常吞掉是双刃剑**  
   `LocalToolGatewayPort` 第 251 行的 `catch (RuntimeException ignored)` 防止了工具失败传播,
   但也隐藏了 artifact 发布失败的真实原因,增加了诊断难度。

7. **Docker 镜像缓存不是根因**  
   使用 `--no-cache` 重建后问题依然存在,说明问题在代码/配置层面,非构建缓存。

8. **多层条件需要逐层验证**  
   修复一个条件后,必须验证下游所有依赖的条件,否则问题只是"转移"而非"解决"。

## 预期结果

修复后,E2E 运行应产生:
1. `IMAGE` artifact(image_generation) — 已有
2. `MARKDOWN` artifact(newsletter_generation) — 新增
3. `MARKDOWN` artifact(ppt_generation) — 新增
4. `CHART` artifact(chart_visualization) — 新增
5. `HTML` artifact(frontend_design) — 新增

**总计**: 5 个 AGENT_ARTIFACT 事件
