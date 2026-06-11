# Seahorse Agent 部署前检查清单

**适用场景**: 本地开发部署、生产环境部署、CI/CD流水线

---

## Maven依赖验证

### 构建顺序
- [ ] **kernel模块已install**: 其他模块依赖kernel的接口定义
  ```bash
  ./mvnw install -pl seahorse-agent-kernel -am -DskipTests
  ```

- [ ] **Optional依赖显式声明**: 检查bootstrap/pom.xml中必需的optional依赖
  ```bash
  # 必须显式声明的依赖
  grep -A2 "sa-token-redis-template" seahorse-agent-bootstrap/pom.xml
  ```

- [ ] **@AutoConfigureAfter顺序正确**: Auth配置必须在Redis配置之后
  ```java
  @AutoConfigureAfter({
      DataSourceAutoConfiguration.class,
      RedisAutoConfiguration.class  // 关键：必须等Redis配置完成
  })
  ```

### 依赖验证命令
```bash
# 验证依赖树（确认sa-token-redis-template存在）
./mvnw dependency:tree -pl seahorse-agent-bootstrap | grep sa-token

# 检查JAR内容
jar -tf seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-*-exec.jar | grep -i satoken
```

---

## 运行时配置验证

### Redis配置
- [ ] **RedisConnectionFactory Bean存在**
  ```bash
  # 启动后检查Bean创建日志
  docker logs seahorse-backend 2>&1 | grep -i "RedisConnectionFactory"
  ```

- [ ] **SaTokenDao Bean正确注册**
  ```bash
  # 检查sa-token初始化日志
  docker logs seahorse-backend 2>&1 | grep -i "SaTokenDao"
  
  # 预期输出包含
  # "创建SaTokenDaoForRedisTemplate，使用RedisConnectionFactory"
  # "SaTokenDaoForRedisTemplate初始化成功，token将持久化到Redis"
  ```

- [ ] **Redis keys前缀正确**
  ```bash
  # 登录后验证token存储
  docker exec seahorse-redis redis-cli KEYS "satoken:*"
  # 应该看到 satoken:login:token:* 等key
  ```

### Ollama配置
- [ ] **Ollama服务可访问**
  ```bash
  curl http://localhost:11434/api/tags
  ```

- [ ] **向量模型已拉取**
  ```bash
  docker exec seahorse-ollama ollama list | grep nomic-embed-text
  ```

- [ ] **Backend配置正确**
  ```bash
  docker logs seahorse-backend | grep "OPENAI_COMPATIBLE"
  # 验证环境变量：
  # SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_COMPATIBLE_BASE_URL=http://ollama:11434/v1
  # SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_COMPATIBLE_EMBEDDING_MODEL=nomic-embed-text
  ```

### 数据库配置
- [ ] **PostgreSQL连接正常**
  ```bash
  docker exec seahorse-postgres psql -U postgres -d seahorse -c "SELECT version();"
  ```

- [ ] **向量扩展已安装**
  ```bash
  docker exec seahorse-postgres psql -U postgres -d seahorse -c "SELECT * FROM pg_extension WHERE extname='vector';"
  ```

- [ ] **向量维度匹配**
  ```sql
  -- 检查t_knowledge_vector表定义
  SELECT column_name, udt_name 
  FROM information_schema.columns 
  WHERE table_name='t_knowledge_vector' AND column_name='embedding';
  
  -- 应该是 vector(768)，匹配nomic-embed-text
  ```

---

## 编译验证

### 编译命令
```bash
# 完整构建（包含所有检查）
./mvnw clean install -DskipTests

# 快速构建（跳过格式检查）
./mvnw clean package -DskipTests -Dspotless.check.skip=true

# 仅构建bootstrap模块
./mvnw package -pl seahorse-agent-bootstrap -am -DskipTests
```

### 构建产物验证
- [ ] **JAR文件生成**
  ```bash
  ls -lh seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-*-exec.jar
  ```

- [ ] **JAR可执行**
  ```bash
  java -jar seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-*-exec.jar --help
  ```

---

## Docker部署验证

### 镜像构建
- [ ] **使用本地JAR构建镜像**（避免Maven代理问题）
  ```bash
  docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
  FROM eclipse-temurin:17-jre
  WORKDIR /app
  COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-*-exec.jar app.jar
  EXPOSE 9090
  ENTRYPOINT ["java", "-jar", "app.jar"]
  EOF
  ```

### 服务启动
- [ ] **所有容器启动成功**
  ```bash
  docker compose -f docker-compose.full.yml ps
  # 所有服务状态应该是 "Up"
  ```

- [ ] **Backend健康检查通过**
  ```bash
  curl http://localhost:9090/actuator/health
  # 预期: {"status":"UP"}
  ```

---

## E2E测试前验证

### 认证测试
- [ ] **登录成功**
  ```bash
  TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')
  echo $TOKEN
  ```

- [ ] **Token持久化到Redis**
  ```bash
  docker exec seahorse-redis redis-cli KEYS "satoken:*" | wc -l
  # 应该 > 0
  ```

- [ ] **Token可用于API调用**
  ```bash
  curl -X GET http://localhost:9090/knowledge-base \
    -H "Authorization: Bearer $TOKEN"
  # 应该返回列表，而非"登录已过期"
  ```

### 向量化测试
- [ ] **Ollama向量生成正常**
  ```bash
  curl -X POST http://localhost:11434/api/embeddings \
    -H "Content-Type: application/json" \
    -d '{"model":"nomic-embed-text","prompt":"测试文本"}' | jq '.embedding | length'
  # 应该返回 768
  ```

---

## 常见问题快速检查

| 问题现象 | 检查命令 | 预期结果 |
|---------|---------|---------|
| "登录已过期" | `docker exec seahorse-redis redis-cli DBSIZE` | > 0 |
| NoClassDefFoundError | `jar -tf target/*-exec.jar \| grep SaTokenDao` | 存在多个satoken类 |
| Backend启动失败 | `docker logs seahorse-backend \| tail -50` | 无ERROR日志 |
| 向量维度错误 | `psql -c "\\d t_knowledge_vector"` | embedding vector(768) |

---

## 检查清单完成确认

完成上述所有检查项后，可以开始执行E2E测试：

```bash
bash scripts/e2e-full-test.sh
```

**预期结果**:
- ✅ 登录成功，token持久化
- ✅ 知识库创建成功
- ✅ 文档向量化完成
- ✅ RAG查询返回相关内容
- ✅ Chat对话使用知识库
- ✅ 多轮记忆正常
