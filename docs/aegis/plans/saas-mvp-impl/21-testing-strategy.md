# 块J · 测试策略与实践 — 质量保障方案

> 文档定位：SaaS MVP 执行计划第 21 篇。功能增强系列之「质量保障」。  
> 关键属性：**P1 优先级、质量保障必需、独立可实施**。  
> 编写依据：2026-06-05 测试现状审查 + 测试金字塔最佳实践。  
> 工作量口径：1 人 × 5-7 天。

> **工期说明**：
> - 原版 2 天工期仅够搭建框架，不足以完成核心 Service 单元测试 + 集成测试 + E2E + CI
> - 实际工期取决于业务模块数量：核心模块（用户/配额/知识库/对话）约需 5-7 天
> - 如果仅覆盖 P0 目标（单元测试 + 集成测试 + E2E + CI），可压缩至 4-5 天

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 测试覆盖率低（< 30%）
- ❌ 单元测试不完整（核心业务逻辑无测试）
- ❌ 无集成测试（数据库、外部 API 无验证）
- ❌ 无前端测试（UI 组件无测试）
- ❌ 无 E2E 测试（用户场景无自动化验证）
- ❌ 无压力测试（不知道系统瓶颈在哪）

**生产风险**：
- 🔥 **回归风险**：改一处代码，多处功能受影响
- 🔥 **性能未知**：不知道能承受多少并发
- 🔥 **集成风险**：数据库升级、依赖升级可能破坏功能

**本方案价值**：
- ✅ 单元测试（JUnit 5、Mockito，覆盖率 > 80%）
- ✅ 集成测试（@SpringBootTest、Testcontainers）
- ✅ 前端测试（Jest、React Testing Library）
- ✅ E2E 测试（Playwright，核心用户路径）
- ✅ 压力测试（JMeter、K6，找到性能瓶颈）
- ✅ CI/CD 集成（PR 自动运行测试）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | 单元测试（核心业务逻辑） | **P0** | < 30% | > 80% |
| G2 | 集成测试（Repository、Adapter） | **P0** | 无 ❌ | > 50% |
| G3 | 前端测试（React 组件） | P1 | 无 ❌ | > 60% |
| G4 | E2E 测试（核心用户路径） | **P0** | 无 ❌ | 3 条路径 |
| G5 | 压力测试（性能瓶颈定位） | P1 | 无 ❌ | TPS 目标 |
| G6 | CI/CD 集成（自动化测试） | **P0** | 无 ❌ | PR 自动运行 |

### 1.2 测试金字塔

```
         /\
        /E2E\         5%  (慢，昂贵，脆弱)
       /------\
      /Integration\   15% (中速，中成本)
     /------------\
    /  Unit Tests  \  80% (快，便宜，稳定)
   /----------------\
```

### 1.3 验收信号

#### P0 验收

1. ✅ 单元测试覆盖率 > 80%（JaCoCo 报告）
2. ✅ 集成测试覆盖所有 Repository（Testcontainers）
3. ✅ E2E 测试：用户注册 → 创建知识库 → 对话（Playwright）
4. ✅ CI 自动运行测试，失败时阻止合并

#### P1 验收

5. ⚠️ 前端组件测试覆盖率 > 60%
6. ⚠️ 压力测试：500 并发 TPS > 1000

---

## 2. 现状（测试审查）

### 2.1 已有测试

**单元测试**：
- ✅ 少量 Service 测试
- ⚠️ 覆盖率 < 30%

**集成测试**：
- ❌ 无

**前端测试**：
- ❌ 无

### 2.2 测试工具栈

| 层级 | 工具 | 状态 |
|------|------|------|
| 单元测试 | JUnit 5 | ✅ 已引入 |
| Mock 框架 | Mockito | ✅ 已引入 |
| 集成测试 | Testcontainers | ❌ 缺失 |
| 前端测试 | Jest | ❌ 缺失 |
| E2E 测试 | Playwright | ❌ 缺失 |
| 覆盖率 | JaCoCo | ⚠️ 已配置但未强制 |

---

## 3. 技术方案

### 3.1 单元测试（P0）

#### 3.1.1 测试框架

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

包含：JUnit 5、Mockito、AssertJ、Hamcrest

#### 3.1.2 Service 层测试

**示例**：`QuotaServiceTest`

```java
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {
    
    @Mock
    private QuotaRepository quotaRepository;
    
    @Mock
    private UsageRecordRepository usageRecordRepository;
    
    @InjectMocks
    private QuotaService quotaService;
    
    @Test
    @DisplayName("扣减配额成功")
    void testDeductSuccess() {
        // Given
        Quota quota = Quota.builder()
            .id(1L)
            .remaining(1000L)
            .build();
        
        when(quotaRepository.findById(1L)).thenReturn(Optional.of(quota));
        
        // When
        quotaService.deduct(1L, 100);
        
        // Then
        verify(quotaRepository).save(argThat(q -> q.getRemaining() == 900));
        verify(usageRecordRepository).save(any(UsageRecord.class));
    }
    
    @Test
    @DisplayName("配额不足时抛异常")
    void testDeductInsufficientQuota() {
        // Given
        Quota quota = Quota.builder()
            .id(1L)
            .remaining(50L)
            .build();
        
        when(quotaRepository.findById(1L)).thenReturn(Optional.of(quota));
        
        // When & Then
        assertThatThrownBy(() -> quotaService.deduct(1L, 100))
            .isInstanceOf(QuotaExceededException.class)
            .hasMessage("Quota exceeded");
    }
}
```

#### 3.1.3 Domain 层测试

```java
class QuotaPolicyTest {
    
    @Test
    @DisplayName("配额扣减后余额正确")
    void testDeduct() {
        // Given
        QuotaPolicy policy = QuotaPolicy.builder()
            .total(1000L)
            .remaining(1000L)
            .build();
        
        // When
        policy.deduct(300);
        
        // Then
        assertThat(policy.getRemaining()).isEqualTo(700);
    }
    
    @Test
    @DisplayName("配额不足时不允许扣减")
    void testCannotDeductWhenInsufficient() {
        // Given
        QuotaPolicy policy = QuotaPolicy.builder()
            .remaining(50L)
            .build();
        
        // When & Then
        assertThat(policy.canDeduct(100)).isFalse();
    }
}
```

#### 3.1.4 覆盖率配置

**JaCoCo 配置**（`pom.xml`）：
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**查看报告**：
```bash
mvn test
open target/site/jacoco/index.html
```

---

### 3.2 集成测试（P0）

#### 3.2.1 Testcontainers 配置

**依赖**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

**基础配置类**：
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

#### 3.2.2 Repository 测试

```java
class QuotaRepositoryTest extends IntegrationTestBase {
    
    @Autowired
    private QuotaRepository quotaRepository;
    
    @Test
    @DisplayName("按租户查询配额")
    void testFindByTenantId() {
        // Given
        Quota quota = Quota.builder()
            .tenantId("tenant-123")
            .remaining(1000L)
            .build();
        quotaRepository.save(quota);
        
        // When
        List<Quota> result = quotaRepository.findByTenantId("tenant-123");
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRemaining()).isEqualTo(1000L);
    }
}
```

#### 3.2.3 Adapter 测试

```java
@SpringBootTest
class OpenAiCompatibleChatAdapterTest extends IntegrationTestBase {
    
    @Autowired
    private ChatModelPort chatModelPort;
    
    @Test
    @DisplayName("调用 AI 模型成功")
    void testChat() {
        // Given
        ChatRequest request = ChatRequest.builder()
            .messages(List.of(
                Message.user("Hello")
            ))
            .build();
        
        // When
        ChatResponse response = chatModelPort.chat(request);
        
        // Then
        assertThat(response.getContent()).isNotEmpty();
    }
}
```

---

### 3.3 前端测试（P1）

#### 3.3.1 Jest 配置

**依赖**：
```bash
npm install --save-dev jest @testing-library/react @testing-library/jest-dom
```

**配置**（`jest.config.js`）：
```javascript
module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.ts'],
  moduleNameMapper: {
    '\\.(css|less|scss)$': 'identity-obj-proxy',
  },
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/index.tsx',
  ],
  coverageThreshold: {
    global: {
      lines: 60,
      statements: 60,
    },
  },
};
```

#### 3.3.2 组件测试

```typescript
// components/KnowledgeBaseCard.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { KnowledgeBaseCard } from './KnowledgeBaseCard';

describe('KnowledgeBaseCard', () => {
  const mockKb = {
    id: 1,
    name: 'Test KB',
    documentCount: 10,
  };
  
  test('renders knowledge base name', () => {
    render(<KnowledgeBaseCard data={mockKb} />);
    expect(screen.getByText('Test KB')).toBeInTheDocument();
  });
  
  test('calls onClick when clicked', () => {
    const handleClick = jest.fn();
    render(<KnowledgeBaseCard data={mockKb} onClick={handleClick} />);
    
    fireEvent.click(screen.getByText('Test KB'));
    expect(handleClick).toHaveBeenCalledWith(1);
  });
  
  test('shows document count', () => {
    render(<KnowledgeBaseCard data={mockKb} />);
    expect(screen.getByText('10 个文档')).toBeInTheDocument();
  });
});
```

#### 3.3.3 Hook 测试

```typescript
// hooks/useKnowledgeBases.test.ts
import { renderHook, waitFor } from '@testing-library/react';
import { useKnowledgeBases } from './useKnowledgeBases';

describe('useKnowledgeBases', () => {
  test('fetches knowledge bases', async () => {
    const { result } = renderHook(() => useKnowledgeBases());
    
    expect(result.current.isLoading).toBe(true);
    
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    
    expect(result.current.data).toHaveLength(3);
  });
});
```

---

### 3.4 E2E 测试（P0）

#### 3.4.1 Playwright 配置

**依赖**：
```bash
npm install --save-dev @playwright/test
npx playwright install
```

**配置**（`playwright.config.ts`）：
```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:3000',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev',
    port: 3000,
  },
});
```

#### 3.4.2 用户路径测试

```typescript
// e2e/user-journey.spec.ts
import { test, expect } from '@playwright/test';

test('用户注册并创建知识库', async ({ page }) => {
  // 1. 注册
  await page.goto('/register');
  await page.fill('input[name="email"]', 'test@example.com');
  await page.fill('input[name="password"]', 'Password123');
  await page.click('button[type="submit"]');
  
  await expect(page).toHaveURL('/dashboard');
  
  // 2. 创建知识库
  await page.click('text=创建知识库');
  await page.fill('input[name="name"]', 'My First KB');
  await page.click('button:has-text("确定")');
  
  await expect(page.locator('text=My First KB')).toBeVisible();
  
  // 3. 上传文档
  await page.click('text=My First KB');
  // ✅ Playwright v1.x+ 推荐使用 locator().setInputFiles（page.setInputFiles 已废弃）
  await page.locator('input[type="file"]').setInputFiles('test-data/sample.pdf');
  
  await expect(page.locator('text=上传成功')).toBeVisible();
  
  // 4. 开始对话
  await page.click('text=开始对话');
  await page.fill('textarea[placeholder="输入消息"]', 'Hello');
  await page.click('button:has-text("发送")');
  
  await expect(page.locator('.message-bubble')).toContainText('Hello');
});
```

#### 3.4.3 核心路径清单

| 路径 | 步骤 | 优先级 |
|------|------|--------|
| 用户注册 → 首次登录 | 注册 → 邮箱验证 → 登录 → 看到欢迎页 | P0 |
| 创建知识库 → 上传文档 → 对话 | 创建 KB → 上传 PDF → 开始对话 → 收到回复 | P0 |
| 配额耗尽 → 升级套餐 → 恢复使用 | 用完配额 → 看到提示 → 支付 → 配额恢复 | P1 |

---

### 3.5 压力测试（P1）

#### 3.5.1 JMeter 测试计划

**场景**：查询会话列表（100 并发）

```xml
<TestPlan>
  <ThreadGroup>
    <stringProp name="ThreadGroup.num_threads">100</stringProp>
    <stringProp name="ThreadGroup.ramp_time">10</stringProp>
    <stringProp name="ThreadGroup.duration">60</stringProp>
    
    <HTTPSamplerProxy>
      <stringProp name="HTTPSampler.domain">localhost</stringProp>
      <stringProp name="HTTPSampler.port">9090</stringProp>
      <stringProp name="HTTPSampler.path">/api/sessions</stringProp>
      <stringProp name="HTTPSampler.method">GET</stringProp>
    </HTTPSamplerProxy>
  </ThreadGroup>
</TestPlan>
```

**运行**：
```bash
jmeter -n -t test-plan.jmx -l result.jtl
```

#### 3.5.2 K6 脚本

```javascript
// load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // 2 分钟升到 100 并发
    { duration: '5m', target: 100 },  // 保持 5 分钟
    { duration: '2m', target: 0 },    // 2 分钟降到 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% 请求 < 500ms
  },
};

export default function () {
  const res = http.get('http://localhost:9090/api/sessions', {
    headers: { Authorization: 'Bearer YOUR_TOKEN' },
  });
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  sleep(1);
}
```

**运行**：
```bash
k6 run load-test.js
```

---

### 3.6 CI/CD 集成（P0）

#### 3.6.1 GitHub Actions 配置

```yaml
# .github/workflows/test.yml
name: Test

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run tests
        run: mvn test
      
      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          file: target/site/jacoco/jacoco.xml
  
  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Run tests
        run: npm test -- --coverage
  
  e2e-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: testdb
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Start backend
        run: |
          mvn spring-boot:run -Dspring-boot.run.profiles=test &
          # 等待后端启动（最多 60 秒）
          for i in $(seq 1 30); do
            curl -sf http://localhost:9090/actuator/health && break
            sleep 2
          done
      
      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
      
      - name: Install frontend dependencies
        run: npm ci
      
      - name: Install Playwright
        run: npx playwright install --with-deps
      
      - name: Run E2E tests
        run: npm run test:e2e
```

---

## 4. 实施步骤

### Day 1-2：单元测试
- Day 1 上午：核心 Service（QuotaService, UserService）单元测试（3h）
- Day 1 下午：Domain 层 + 工具类单元测试（2h）
- Day 2 上午：补充剩余 Service 单元测试 + 覆盖率检查（3h）
- Day 2 下午：Mock 复杂场景（外部 API 调用、消息队列）（2h）

### Day 3：集成测试
- 上午：Testcontainers 基础配置 + PostgreSQL 容器（2h）
- 下午：Repository 层集成测试（所有 Repository）（3h）

### Day 4：前端测试
- 上午：Jest 配置 + 核心组件测试（NotificationBell, KnowledgeBaseCard）（3h）
- 下午：Hook 测试 + 覆盖率配置（2h）

### Day 5：E2E + CI
- 上午：Playwright 配置 + 核心用户路径测试（3h）
- 下午：GitHub Actions CI 配置 + 联调（2h）

### Day 6-7（可选）：压力测试 + 补充
- 压力测试脚本编写 + 执行 + 结果分析
- 补充遗漏测试用例

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ CI 绿色（所有测试通过）
✅ 覆盖率报告生成

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06
