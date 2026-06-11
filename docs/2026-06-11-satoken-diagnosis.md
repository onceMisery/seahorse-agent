# sa-token Redis持久化问题诊断报告

**日期**: 2026-06-11 08:40  
**状态**: ❌ sa-token仍使用非标准前缀，Bean未按预期创建

---

## 诊断结果

### 1. 编译和打包 ✅
- JAR文件正常生成: 110MB
- 依赖树包含sa-token-redis-template: ✅
- Docker镜像构建成功: ✅
- Backend启动成功: 55秒 ✅

### 2. 登录功能 ✅
- 登录API返回token: ✅
- Token格式正确: UUID格式 ✅

### 3. Token持久化 ⚠️
**问题**: Redis中存在token，但使用**非标准前缀**
```
实际: Authorization:login:token:*
预期: satoken:login:token:*
```

### 4. API调用 ❌
```bash
curl /knowledge-base -H "Authorization: Bearer $TOKEN"
{"code":"1","message":"登录已过期，请重新登录"}
```

### 5. Bean创建日志 ❌
**预期日志未出现**:
- 无 "创建SaTokenDaoForRedisTemplate"
- 无 "初始化成功，token将持久化到Redis"
- 无 "RedisConnectionFactory不可用" 警告

---

## 根因分析

### 问题1: sa-token-redis-template类不在JAR中
```bash
jar -tf *.jar | grep -i "cn/dev33/satoken"
# 输出为空 - sa-token类未打包到JAR
```

虽然依赖树显示存在，但Spring Boot打包未包含这些类。

### 问题2: 存在其他认证机制
Redis key前缀为`Authorization:login:*`说明存在另一套认证存储机制，可能是:
- 自定义的AuthorizationTokenStore
- Spring Security的TokenStore
- 其他自定义实现

### 问题3: SaTokenDao Bean未创建
日志中完全没有SeahorseAgentAuthAdapterAutoConfiguration相关输出，说明:
- Bean创建时没有执行我们的日志代码
- 或者使用了其他SaTokenDao实现
- 或者配置类本身未加载

---

## 下一步诊断

### 方案A: 检查实际使用的认证机制
```bash
# 查找Authorization前缀的来源
grep -r "Authorization:login" seahorse-agent-adapter-web/src
grep -r "Authorization:login" seahorse-agent-kernel/src
```

### 方案B: 强制使用sa-token标准DAO
检查是否有其他Bean覆盖了我们的SaTokenDao配置。

### 方案C: 验证AutoConfiguration加载
```bash
docker logs seahorse-backend | grep "SeahorseAgentAuthAdapterAutoConfiguration"
```

---

## 临时结论

**sa-token Redis持久化修复未生效**，但token确实持久化到Redis（使用非标准前缀）。

**主要问题**: 存在**另一套认证存储机制**，与sa-token并行或替代了sa-token的标准存储。

**建议**: 查找`Authorization:login:token`的实现代码，确认实际认证机制。
