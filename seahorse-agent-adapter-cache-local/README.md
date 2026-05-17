# seahorse-agent-adapter-cache-local

本模块提供 Seahorse Agent 的本地内存基础设施适配器。模块名保留 `cache-local` 是为了维持 Maven 坐标和已有依赖兼容，但能力范围不只包含键值缓存。

当前包含的端口实现：

- `KeyValueCachePort`：单 JVM 内存键值缓存。
- `RateLimiterPort`：单 JVM 内存限流。
- `PubSubPort`：单 JVM 进程内发布订阅。
- `DistributedLockPort`：仅单 JVM 可见的本地锁实现。
- `DistributedSemaphorePort`：仅单 JVM 可见的本地信号量实现。
- `IdGeneratorPort`：单 JVM 内存自增 ID。

注意事项：

- 本模块适合本地开发、单节点部署和测试场景。
- 多节点部署不要依赖这里的锁、信号量、限流和发布订阅语义；应切换到 Redis/Redisson 等真正跨节点可见的适配器。
- 后续如果新增 `seahorse-agent-adapter-coordination-local`，应先提供迁移文档和兼容过渡期，再考虑拆分或重命名当前 artifact。
