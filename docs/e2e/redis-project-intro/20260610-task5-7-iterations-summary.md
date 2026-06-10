# Task 5 根因诊断:7 次迭代历程

## 问题描述
E2E 测试只产生 1/5 AGENT_ARTIFACT 事件,文本工件(html/text/json/csv)未持久化。

## 完整根因链

### 根因 #1: Bootstrap 缺少 storage-s3 依赖
**现象**: Classpath 缺少 s3 adapter  
**修复**: 添加依赖到 bootstrap pom.xml  
**结果**: 依赖在,但 bean 未创建

### 根因 #2: 自动配置加载顺序
**现象**: KernelAgent 可能在 Storage 之前加载  
**修复**: 添加 @AutoConfigureAfter(Storage)  
**结果**: 顺序正确,但 bean 仍未创建

### 根因 #3: 属性前缀不匹配
**现象**: @ConditionalOnProperty 使用 `seahorse-agent.*` 前缀,但环境变量映射为 `seahorse.agent.*`  
**修复**: 统一改为 `seahorse.agent`  
**结果**: 前缀统一,但条件仍不满足

### 根因 #4: @ConditionalOnExpression 表达式错误
**现象**: SpEL 表达式 `!'${...}'.isEmpty()` 对不存在的属性仍返回 false  
**修复**: 改用 @ConditionalOnExpression  
**结果**: 表达式问题,条件仍不满足

### 根因 #5: S3ObjectStorageAdapter 有独立条件
**现象**: 即使 S3Client bean 创建,Adapter bean 因独立的 @ConditionalOnProperty 未创建  
**修复**: 去掉 Adapter 的 @ConditionalOnProperty  
**结果**: Client 本身未创建,Adapter 依赖失败

### 根因 #6: @ConditionalOnProperty 对 ConfigurationProperties 不生效
**现象**: 即使使用 @ConfigurationProperties,@ConditionalOnProperty 仍无法匹配  
**修复**: 引入 S3StorageProperties 配置类  
**结果**: 配置类正常,但条件仍不满足

### 根因 #7: @ConditionalOnClass 检查失败 (最终根因,诊断中)
**现象**: `@ConditionalOnClass(name="software.amazon.awssdk.services.s3.S3Client")` 在运行时检查失败  
**可能原因**:
- Spring Boot 3.x 的嵌套 JAR 类加载问题
- `@ConditionalOnClass(name=...)` 字符串检查在某些情况下失效
- Autoconfigure 模块的 optional 依赖导致类加载时机问题

**修复策略**: 去掉所有条件注解,强制创建 bean,添加日志诊断属性绑定

## 7 次迭代对比

| 迭代 | 策略 | 条件类型 | 结果 | 关键发现 |
|-----|------|---------|------|---------|
| 1 | 添加依赖 | - | 1/5 | 依赖在但 bean 不在 |
| 2 | 修复顺序 | @AutoConfigureAfter | 1/5 | 顺序不是问题 |
| 3 | 修复前缀 | @ConditionalOnProperty | 1/5 | 前缀统一无效 |
| 4 | SpEL 表达式 | @ConditionalOnExpression | 1/5 | 表达式语法问题 |
| 5 | 去掉 Adapter 条件 | 只检查 Client | 1/5 | Client 未创建 |
| 6 | ConfigurationProperties | 标准绑定 | 1/5 | 配置类正常但条件失效 |
| 7 | 去掉所有条件+日志 | 强制创建 | 构建中 | 准备看日志诊断 |

## 诊断进度
- [x] 验证依赖树包含 AWS SDK
- [x] 验证 JAR 包含 AWS SDK  
- [x] 验证环境变量正确传递
- [x] 验证属性前缀映射规则
- [x] 验证 ConfigurationProperties 绑定
- [ ] 验证 @ConditionalOnClass 检查结果 (当前)
- [ ] 验证 bean 创建日志 (当前)
- [ ] 验证属性实际值 (当前)

---
**更新时间**: 2026-06-10 13:59  
**状态**: 第 7 次迭代构建中
