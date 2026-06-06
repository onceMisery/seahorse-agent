# 10 · 管理后台（租户管理 + 用户管理 + 审计日志）— 可落地技术方案

> 状态：已定稿 ｜ 作者：架构组 ｜ 日期：2026-06-05 ｜ 定位：SaaS 运营必备
>
> **重要依赖**：本方案依赖 `01-多租户`（TenantContext）、`03-用户体系`（CurrentUser）、`04-计费`（订阅状态）。

---

## 1. 目标与范围

### 1.1 要做什么（MVP）

| 编号 | 目标 | 优先级 |
|------|------|--------|
| G1 | 超级管理员角色：跨租户查询能力（绕过 TenantContext 过滤） | P0 |
| G2 | 租户管理：列表查询、详情（订阅/用量/用户数）、操作（暂停/恢复/删除） | P0 |
| G3 | 用户管理：跨租户用户列表、封禁/解封、重置密码、强制下线 | P0 |
| G4 | 审计日志：记录敏感操作（登录/权限变更/数据删除/支付），查询/导出 | P0 |
| G5 | 前端管理后台：独立路由、权限守卫、租户详情页、审计日志搜索 | P1 |

### 1.2 明确不做（后延）

- **不做**实时监控大屏（属 05-监控方案）
- **不做**自定义报表生成器
- **不做**数据导出为 Excel（仅支持 CSV）
- **不做**租户自助升级/降级套餐（属 04-计费方案）

### 1.3 验收信号

1. ✅ 超级管理员登录后能看到所有租户列表，点击某租户查看其用户/Agent/知识库
2. ✅ 封禁租户后，该租户下所有用户无法登录
3. ✅ 审计日志记录"用户 A 删除了知识库 B"，可按时间/用户/操作类型筛选
4. ✅ 强制下线功能让目标用户的 Session 立即失效

---

## 2. 现状（代码级审查）

### 2.1 用户模型

**表**：`t_user`

```sql
CREATE TABLE t_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128),  -- 当前明文存储（安全缺口）
    role VARCHAR(32) DEFAULT 'USER',
    avatar VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**缺口**：
- 无 `tenant_id`（待 01-多租户补充）
- 无 `status` 字段（无法封禁）
- 无 `email` 字段
- 无 `super_admin` 角色

**Service**：`KernelUserService`  
**路径**：`seahorse-agent-kernel/.../kernel/application/user/KernelUserService.java`

**当前功能**：仅用户 CRUD，无跨租户查询、无封禁操作。

### 2.2 租户模型

**表**：`sa_tenant`（假设由 01-多租户创建）

```sql
CREATE TABLE sa_tenant (
    tenant_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',  -- ACTIVE/SUSPENDED/DELETED
    subscription_plan_id BIGINT,  -- 关联 04-计费
    storage_quota_gb NUMERIC(10, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Service**：当前无租户管理 Service，需新建。

### 2.3 审计日志

**检索结果**：全仓搜 "AuditLog" 无结果。

**结论**：审计日志功能**完全缺失**，需从零设计。

### 2.4 权限框架

**当前**：Sa-Token + 角色校验

**检查点**：`SaTokenCurrentUserAdapter.currentUser()` 返回 `CurrentUser(userId, username, role, avatar)`

**缺口**：
- 无 `SUPER_ADMIN` 角色
- 角色硬编码为 `"USER"` 或 `"ADMIN"`（无超管）

---

## 3. 技术方案

### 3.1 超级管理员（P0）

#### 3.1.1 角色定义

**增强 `t_user.role`**：支持 `SUPER_ADMIN`

```sql
ALTER TABLE t_user ADD CONSTRAINT chk_user_role 
CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN'));
```

**CurrentUser 增强**：

```java
public record CurrentUser(
    Long userId, 
    String username, 
    String role,  // "SUPER_ADMIN", "ADMIN", "USER"
    String avatar,
    String tenantId  // 新增：普通用户有值，超管为 null
) {
    public boolean isSuperAdmin() {
        return "SUPER_ADMIN".equals(role);
    }
}
```

#### 3.1.2 跨租户查询能力

**新增 Port**：`AdminRepositoryPort`（绕过 TenantContext）

```java
public interface AdminRepositoryPort {
    
    /**
     * 跨租户查询所有租户列表（仅超管调用）
     */
    List<TenantRecord> findAllTenants(int page, int size);
    
    /**
     * 跨租户查询某租户下的所有用户
     */
    List<UserRecord> findUsersByTenant(String tenantId, int page, int size);
    
    /**
     * 跨租户查询某租户的资源统计
     */
    TenantResourceSummary getTenantResourceSummary(String tenantId);
}
```

**实现**：`JdbcAdminRepositoryAdapter`

```java
@Component
public class JdbcAdminRepositoryAdapter implements AdminRepositoryPort {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public List<TenantRecord> findAllTenants(int page, int size) {
        // 直接查询，不带 tenant_id 过滤
        return jdbcTemplate.query("""
            SELECT tenant_id, name, status, subscription_plan_id, storage_quota_gb, created_at
            FROM sa_tenant
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            this::mapTenant,
            size, (page - 1) * size
        );
    }
    
    @Override
    public List<UserRecord> findUsersByTenant(String tenantId, int page, int size) {
        return jdbcTemplate.query("""
            SELECT id, username, email, status, role, created_at
            FROM t_user
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """,
            this::mapUser,
            tenantId, size, (page - 1) * size
        );
    }
    
    @Override
    public TenantResourceSummary getTenantResourceSummary(String tenantId) {
        Long userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_user WHERE tenant_id = ?", Long.class, tenantId);
        
        Long agentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sa_agent_definition WHERE tenant_id = ?", Long.class, tenantId);
        
        Long kbCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM t_knowledge_base WHERE tenant_id = ?", Long.class, tenantId);
        
        return new TenantResourceSummary(userCount, agentCount, kbCount);
    }
}
```

#### 3.1.3 权限检查注解

**自定义注解**：`@RequireSuperAdmin`

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSuperAdmin {
    String message() default "需要超级管理员权限";
}
```

**AOP 拦截器**：

```java
@Aspect
@Component
public class SuperAdminAspect {
    
    private final ObjectProvider<CurrentUserPort> currentUserPortProvider;
    
    @Around("@annotation(requireSuperAdmin)")
    public Object checkSuperAdmin(ProceedingJoinPoint pjp, RequireSuperAdmin requireSuperAdmin) throws Throwable {
        CurrentUser currentUser = currentUserPortProvider.getIfAvailable().currentUser();
        
        if (currentUser == null || !currentUser.isSuperAdmin()) {
            throw new ForbiddenException(requireSuperAdmin.message());
        }
        
        return pjp.proceed();
    }
}
```

### 3.2 租户管理（P0）

#### 3.2.1 Service 层

**新增 Service**：`KernelAdminTenantService`

```java
public class KernelAdminTenantService implements AdminTenantInboundPort {
    
    private final AdminRepositoryPort adminRepositoryPort;
    private final AuditLogPort auditLogPort;
    
    @Override
    public PageResult<TenantRecord> listTenants(int page, int size, String statusFilter) {
        List<TenantRecord> tenants = adminRepositoryPort.findAllTenants(page, size);
        if (statusFilter != null) {
            tenants = tenants.stream()
                .filter(t -> statusFilter.equals(t.status()))
                .toList();
        }
        return new PageResult<>(tenants, page, size, countTenants());
    }
    
    @Override
    public TenantDetailView getTenantDetail(String tenantId) {
        TenantRecord tenant = adminRepositoryPort.findTenantById(tenantId);
        TenantResourceSummary summary = adminRepositoryPort.getTenantResourceSummary(tenantId);
        SubscriptionInfo subscription = subscriptionPort.getByTenantId(tenantId);
        
        return new TenantDetailView(tenant, summary, subscription);
    }
    
    @Override
    public void suspendTenant(String tenantId, String reason, String operator) {
        adminRepositoryPort.updateTenantStatus(tenantId, TenantStatus.SUSPENDED);
        auditLogPort.log(new AuditLogEntry(
            operator, tenantId, "SUSPEND_TENANT", "租户", tenantId, reason
        ));
        
        // 强制下线该租户所有用户
        sessionManager.invalidateAllSessionsByTenant(tenantId);
    }
    
    @Override
    public void deleteTenant(String tenantId, boolean cascade, String operator) {
        if (cascade) {
            // 级联删除：用户、Agent、知识库、对话等
            cascadeDeleteTenantData(tenantId);
        }
        
        adminRepositoryPort.updateTenantStatus(tenantId, TenantStatus.DELETED);
        auditLogPort.log(new AuditLogEntry(
            operator, tenantId, "DELETE_TENANT", "租户", tenantId, "级联=" + cascade
        ));
    }
    
    private void cascadeDeleteTenantData(String tenantId) {
        // 执行顺序：子数据 → 主数据
        jdbcTemplate.update("DELETE FROM t_conversation WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM t_knowledge_base WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM sa_agent_definition WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM t_user WHERE tenant_id = ?", tenantId);
    }
}
```

#### 3.2.2 Controller 层

```java
@RestController
@RequestMapping("/api/admin/tenants")
public class SeahorseAdminTenantController {
    
    private final ObjectProvider<AdminTenantInboundPort> adminTenantPortProvider;
    
    @GetMapping
    @RequireSuperAdmin
    public Map<String, Object> listTenants(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<TenantRecord> result = adminTenantPortProvider.getIfAvailable()
            .listTenants(page, size, status);
        return Map.of("code", "0", "data", result);
    }
    
    @GetMapping("/{tenantId}")
    @RequireSuperAdmin
    public Map<String, Object> getTenantDetail(@PathVariable String tenantId) {
        TenantDetailView detail = adminTenantPortProvider.getIfAvailable()
            .getTenantDetail(tenantId);
        return Map.of("code", "0", "data", detail);
    }
    
    @PutMapping("/{tenantId}/suspend")
    @RequireSuperAdmin
    public Map<String, Object> suspendTenant(
            @PathVariable String tenantId,
            @RequestBody SuspendRequest request) {
        adminTenantPortProvider.getIfAvailable()
            .suspendTenant(tenantId, request.reason(), getCurrentUsername());
        return Map.of("code", "0", "message", "租户已暂停");
    }
    
    @DeleteMapping("/{tenantId}")
    @RequireSuperAdmin
    public Map<String, Object> deleteTenant(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "false") boolean cascade) {
        adminTenantPortProvider.getIfAvailable()
            .deleteTenant(tenantId, cascade, getCurrentUsername());
        return Map.of("code", "0", "message", "租户已删除");
    }
}

record SuspendRequest(String reason) {}
```

### 3.3 用户管理（P0）

#### 3.3.1 用户表增强

```sql
-- 增加缺失字段
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT 'default';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email VARCHAR(128);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'ACTIVE';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- 状态约束
ALTER TABLE t_user ADD CONSTRAINT chk_user_status 
CHECK (status IN ('ACTIVE', 'BANNED', 'DELETED'));

CREATE INDEX idx_user_tenant ON t_user(tenant_id);
CREATE INDEX idx_user_status ON t_user(status);
```

#### 3.3.2 Service 层

```java
public class KernelAdminUserService implements AdminUserInboundPort {
    
    private final AdminRepositoryPort adminRepositoryPort;
    private final TokenServicePort tokenServicePort;
    private final AuditLogPort auditLogPort;
    
    @Override
    public PageResult<UserRecord> listUsers(String tenantId, int page, int size, String statusFilter) {
        List<UserRecord> users = adminRepositoryPort.findUsersByTenant(tenantId, page, size);
        if (statusFilter != null) {
            users = users.stream()
                .filter(u -> statusFilter.equals(u.status()))
                .toList();
        }
        return new PageResult<>(users, page, size, countUsers(tenantId));
    }
    
    @Override
    public void banUser(Long userId, String reason, String operator) {
        adminRepositoryPort.updateUserStatus(userId, UserStatus.BANNED);
        tokenServicePort.kickoutUser(userId);  // 强制下线
        
        auditLogPort.log(new AuditLogEntry(
            operator, getUserTenantId(userId), "BAN_USER", "用户", userId.toString(), reason
        ));
    }
    
    @Override
    public void resetPassword(Long userId, String newPassword, String operator) {
        String hashedPassword = passwordEncoder.encode(newPassword);
        adminRepositoryPort.updateUserPassword(userId, hashedPassword);
        tokenServicePort.kickoutUser(userId);  // 重置后强制重新登录
        
        auditLogPort.log(new AuditLogEntry(
            operator, getUserTenantId(userId), "RESET_PASSWORD", "用户", userId.toString(), null
        ));
    }
    
    @Override
    public void forceLogout(Long userId, String operator) {
        tokenServicePort.kickoutUser(userId);
        
        auditLogPort.log(new AuditLogEntry(
            operator, getUserTenantId(userId), "FORCE_LOGOUT", "用户", userId.toString(), null
        ));
    }
}
```

### 3.4 审计日志（P0）

#### 3.4.1 数据模型

**表**：`sa_audit_log`

```sql
CREATE TABLE sa_audit_log (
    log_id BIGSERIAL PRIMARY KEY,
    operator VARCHAR(64) NOT NULL,  -- 操作人用户名
    operator_id BIGINT,  -- 操作人 ID
    tenant_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,  -- 操作类型
    resource_type VARCHAR(32),  -- 资源类型：用户/租户/知识库等
    resource_id VARCHAR(128),
    detail TEXT,  -- 操作详情
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_tenant ON sa_audit_log(tenant_id);
CREATE INDEX idx_audit_action ON sa_audit_log(action);
CREATE INDEX idx_audit_time ON sa_audit_log(created_at DESC);
CREATE INDEX idx_audit_operator ON sa_audit_log(operator_id);
```

#### 3.4.2 敏感操作列表

| 操作类型 | 说明 | 级别 |
|---------|------|------|
| `LOGIN_SUCCESS` | 登录成功 | INFO |
| `LOGIN_FAILED` | 登录失败 | WARN |
| `LOGOUT` | 登出 | INFO |
| `SUSPEND_TENANT` | 暂停租户 | CRITICAL |
| `DELETE_TENANT` | 删除租户 | CRITICAL |
| `BAN_USER` | 封禁用户 | CRITICAL |
| `RESET_PASSWORD` | 重置密码 | WARN |
| `FORCE_LOGOUT` | 强制下线 | WARN |
| `DELETE_KNOWLEDGE_BASE` | 删除知识库 | WARN |
| `UPDATE_QUOTA` | 修改配额 | WARN |
| `PAYMENT_SUCCESS` | 支付成功 | INFO |
| `PAYMENT_FAILED` | 支付失败 | WARN |

#### 3.4.3 Service 层

```java
public class KernelAuditLogService implements AuditLogPort {
    
    private final JdbcTemplate jdbcTemplate;
    
    @Override
    public void log(AuditLogEntry entry) {
        jdbcTemplate.update("""
            INSERT INTO sa_audit_log 
            (operator, operator_id, tenant_id, action, resource_type, resource_id, detail, ip_address, user_agent)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            entry.operator(), entry.operatorId(), entry.tenantId(), entry.action(),
            entry.resourceType(), entry.resourceId(), entry.detail(), 
            entry.ipAddress(), entry.userAgent()
        );
    }
    
    @Override
    public PageResult<AuditLogRecord> query(AuditLogQuery query) {
        StringBuilder sql = new StringBuilder("""
            SELECT log_id, operator, tenant_id, action, resource_type, resource_id, detail, created_at
            FROM sa_audit_log
            WHERE 1=1
            """);
        
        List<Object> params = new ArrayList<>();
        
        if (query.tenantId() != null) {
            sql.append(" AND tenant_id = ?");
            params.add(query.tenantId());
        }
        
        if (query.action() != null) {
            sql.append(" AND action = ?");
            params.add(query.action());
        }
        
        if (query.startTime() != null) {
            sql.append(" AND created_at >= ?");
            params.add(query.startTime());
        }
        
        if (query.endTime() != null) {
            sql.append(" AND created_at <= ?");
            params.add(query.endTime());
        }
        
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(query.size());
        params.add((query.page() - 1) * query.size());
        
        List<AuditLogRecord> logs = jdbcTemplate.query(sql.toString(), this::mapLog, params.toArray());
        return new PageResult<>(logs, query.page(), query.size(), countLogs(query));
    }
}
```

#### 3.4.4 日志归档策略

**定时任务**：每月 1 号归档 30 天前的日志到对象存储

```java
@Scheduled(cron = "0 0 2 1 * ?")  // 每月 1 号凌晨 2 点
public void archiveOldLogs() {
    Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
    
    // 1. 导出为 CSV
    List<AuditLogRecord> oldLogs = jdbcTemplate.query("""
        SELECT * FROM sa_audit_log WHERE created_at < ?
        """, this::mapLog, cutoff);
    
    String csv = convertToCsv(oldLogs);
    
    // 2. 上传到对象存储
    String key = "audit-logs/archive-" + cutoff.toString() + ".csv";
    objectStoragePort.upload(key, csv.getBytes());
    
    // 3. 删除数据库记录
    jdbcTemplate.update("DELETE FROM sa_audit_log WHERE created_at < ?", cutoff);
    
    log.info("Archived {} audit logs to {}", oldLogs.size(), key);
}
```

### 3.5 前端实现骨架

#### 3.5.1 租户列表页

**文件**：`frontend/src/pages/admin/TenantListPage.tsx`

```tsx
import { Table, Tag, Button, Space, Modal, Input, message } from 'antd';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export const TenantListPage = () => {
  const [tenants, setTenants] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  
  useEffect(() => {
    loadTenants();
  }, []);
  
  const loadTenants = async () => {
    setLoading(true);
    const res = await fetch('/api/admin/tenants?page=1&size=50');
    const { data } = await res.json();
    setTenants(data.items);
    setLoading(false);
  };
  
  const handleSuspend = (tenantId: string) => {
    Modal.confirm({
      title: '确认暂停租户？',
      content: <Input.TextArea placeholder="请输入暂停原因" id="suspend-reason" />,
      onOk: async () => {
        const reason = (document.getElementById('suspend-reason') as HTMLTextAreaElement).value;
        await fetch(`/api/admin/tenants/${tenantId}/suspend`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason })
        });
        message.success('租户已暂停');
        loadTenants();
      }
    });
  };
  
  const columns = [
    { title: '租户 ID', dataIndex: 'tenantId', key: 'tenantId' },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : status === 'SUSPENDED' ? 'orange' : 'red'}>
          {status}
        </Tag>
      ),
    },
    { title: '套餐', dataIndex: 'subscriptionPlanId', key: 'plan' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" onClick={() => navigate(`/admin/tenants/${record.tenantId}`)}>
            详情
          </Button>
          {record.status === 'ACTIVE' && (
            <Button type="link" danger onClick={() => handleSuspend(record.tenantId)}>
              暂停
            </Button>
          )}
        </Space>
      ),
    },
  ];
  
  return (
    <div>
      <h2>租户管理</h2>
      <Table columns={columns} dataSource={tenants} loading={loading} rowKey="tenantId" />
    </div>
  );
};
```

#### 3.5.2 租户详情页

```tsx
export const TenantDetailPage = () => {
  const { tenantId } = useParams();
  const [detail, setDetail] = useState(null);
  
  useEffect(() => {
    fetch(`/api/admin/tenants/${tenantId}`)
      .then(res => res.json())
      .then(({ data }) => setDetail(data));
  }, [tenantId]);
  
  if (!detail) return <Spin />;
  
  return (
    <div>
      <Descriptions title="租户信息" bordered>
        <Descriptions.Item label="租户 ID">{detail.tenant.tenantId}</Descriptions.Item>
        <Descriptions.Item label="名称">{detail.tenant.name}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={detail.tenant.status === 'ACTIVE' ? 'green' : 'orange'}>
            {detail.tenant.status}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="用户数">{detail.summary.userCount}</Descriptions.Item>
        <Descriptions.Item label="Agent 数">{detail.summary.agentCount}</Descriptions.Item>
        <Descriptions.Item label="知识库数">{detail.summary.kbCount}</Descriptions.Item>
        <Descriptions.Item label="订阅套餐">{detail.subscription?.planName}</Descriptions.Item>
        <Descriptions.Item label="到期时间">{detail.subscription?.expiresAt}</Descriptions.Item>
      </Descriptions>
      
      <Tabs defaultActiveKey="users" style={{ marginTop: 20 }}>
        <TabPane tab="用户列表" key="users">
          <UserListByTenant tenantId={tenantId} />
        </TabPane>
        <TabPane tab="Agent 列表" key="agents">
          <AgentListByTenant tenantId={tenantId} />
        </TabPane>
      </Tabs>
    </div>
  );
};
```

#### 3.5.3 审计日志页

```tsx
export const AuditLogPage = () => {
  const [logs, setLogs] = useState([]);
  const [filters, setFilters] = useState({ action: null, tenantId: null });
  
  const loadLogs = async () => {
    const query = new URLSearchParams({
      page: '1',
      size: '50',
      ...filters
    }).toString();
    
    const res = await fetch(`/api/admin/audit-logs?${query}`);
    const { data } = await res.json();
    setLogs(data.items);
  };
  
  const columns = [
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    { title: '操作人', dataIndex: 'operator', key: 'operator' },
    { title: '租户', dataIndex: 'tenantId', key: 'tenantId' },
    { 
      title: '操作', 
      dataIndex: 'action', 
      key: 'action',
      render: (action: string) => {
        const critical = ['SUSPEND_TENANT', 'DELETE_TENANT', 'BAN_USER'];
        return (
          <Tag color={critical.includes(action) ? 'red' : 'default'}>
            {action}
          </Tag>
        );
      },
    },
    { title: '资源类型', dataIndex: 'resourceType', key: 'resourceType' },
    { title: '资源 ID', dataIndex: 'resourceId', key: 'resourceId' },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
  ];
  
  return (
    <div>
      <h2>审计日志</h2>
      <Space style={{ marginBottom: 16 }}>
        <Select
          placeholder="操作类型"
          style={{ width: 200 }}
          onChange={(value) => setFilters({ ...filters, action: value })}
          allowClear
        >
          <Option value="LOGIN_SUCCESS">登录成功</Option>
          <Option value="LOGIN_FAILED">登录失败</Option>
          <Option value="SUSPEND_TENANT">暂停租户</Option>
          <Option value="BAN_USER">封禁用户</Option>
          <Option value="DELETE_KNOWLEDGE_BASE">删除知识库</Option>
        </Select>
        <Input 
          placeholder="租户 ID" 
          style={{ width: 200 }}
          onChange={(e) => setFilters({ ...filters, tenantId: e.target.value })}
        />
        <Button type="primary" onClick={loadLogs}>搜索</Button>
      </Space>
      <Table columns={columns} dataSource={logs} rowKey="logId" />
    </div>
  );
};
```

---

## 4. 任务清单（Checkbox）

### Phase 1 — 后端核心（P0，第 1 周）

- [ ] **超级管理员**
  - [ ] `t_user` 表增加 `SUPER_ADMIN` 角色支持
  - [ ] `CurrentUser` 增加 `tenantId` 和 `isSuperAdmin()` 方法
  - [ ] `@RequireSuperAdmin` 注解 + AOP 拦截器
  - [ ] 超管权限测试（越权访问应被拒绝）

- [ ] **跨租户查询**
  - [ ] `AdminRepositoryPort` 接口定义
  - [ ] `JdbcAdminRepositoryAdapter` 实现
  - [ ] Repository 单元测试

- [ ] **租户管理**
  - [ ] `KernelAdminTenantService` 实现
  - [ ] `SeahorseAdminTenantController` 实现
  - [ ] 暂停租户功能测试
  - [ ] 级联删除功能测试

- [ ] **用户管理**
  - [ ] `t_user` 表增加 `status`、`email`、`tenant_id` 字段
  - [ ] `KernelAdminUserService` 实现
  - [ ] `SeahorseAdminUserController` 实现
  - [ ] 封禁用户 + 强制下线测试

### Phase 2 — 审计日志（P0，第 2 周）

- [ ] **数据模型**
  - [ ] `sa_audit_log` 表创建
  - [ ] 索引优化（tenant_id、action、created_at）

- [ ] **Service 层**
  - [ ] `KernelAuditLogService` 实现
  - [ ] `AuditLogPort` 接口定义
  - [ ] 日志查询 + 过滤功能
  - [ ] 日志归档定时任务

- [ ] **埋点集成**
  - [ ] 登录/登出埋点
  - [ ] 租户操作埋点
  - [ ] 用户操作埋点
  - [ ] 支付操作埋点（集成 04-计费）

- [ ] **Controller 层**
  - [ ] `SeahorseAuditLogController` 实现
  - [ ] API 测试

### Phase 3 — 前端实现（P1，第 3 周）

- [ ] **路由配置**
  - [ ] `/admin/tenants` 租户列表
  - [ ] `/admin/tenants/:id` 租户详情
  - [ ] `/admin/audit-logs` 审计日志

- [ ] **权限守卫**
  - [ ] 检查当前用户是否为超管
  - [ ] 非超管访问管理后台跳转 403

- [ ] **页面组件**
  - [ ] `TenantListPage` 实现
  - [ ] `TenantDetailPage` 实现
  - [ ] `AuditLogPage` 实现
  - [ ] `UserListByTenant` 组件

---

## 5. 测试策略

### 5.1 权限测试

**测试用例 1：超管能跨租户查询**
```java
@Test
void superAdminCanQueryAllTenants() {
    // Given
    loginAsSuperAdmin();
    
    // When
    List<TenantRecord> tenants = adminTenantService.listTenants(1, 20, null).getItems();
    
    // Then
    assertThat(tenants).hasSizeGreaterThan(1);  // 包含多个租户
}
```

**测试用例 2：普通用户访问管理后台被拒绝**
```java
@Test
void normalUserCannotAccessAdminApi() {
    // Given
    loginAsNormalUser("tenant-a");
    
    // When & Then
    assertThatThrownBy(() -> 
        adminTenantService.listTenants(1, 20, null)
    ).isInstanceOf(ForbiddenException.class);
}
```

### 5.2 租户操作测试

**测试用例 3：暂停租户后用户无法登录**
```java
@Test
void suspendedTenantUsersCannotLogin() {
    // Given
    String tenantId = "tenant-test";
    Long userId = createUser(tenantId, "user1");
    
    // When
    adminTenantService.suspendTenant(tenantId, "测试", "admin");
    
    // Then
    assertThatThrownBy(() -> 
        authService.login("user1", "password")
    ).hasMessageContaining("租户已暂停");
}
```

### 5.3 审计日志测试

**测试用例 4：敏感操作被记录**
```java
@Test
void sensitiveOperationsAreAudited() {
    // When
    adminTenantService.suspendTenant("tenant-a", "违规", "admin");
    
    // Then
    List<AuditLogRecord> logs = auditLogService.query(new AuditLogQuery(
        null, "SUSPEND_TENANT", null, null, 1, 10
    )).getItems();
    
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).operator()).isEqualTo("admin");
    assertThat(logs.get(0).resourceId()).isEqualTo("tenant-a");
}
```

---

## 6. 验收标准

1. ✅ 超级管理员登录后访问 `/admin/tenants`，看到所有租户列表（≥2 个租户）
2. ✅ 点击租户"test-tenant"，详情页显示其用户数（5）、Agent数（3）、知识库数（2）
3. ✅ 暂停租户"test-tenant"，该租户下用户"user1"尝试登录失败（提示"租户已暂停"）
4. ✅ 审计日志页搜索"SUSPEND_TENANT"，找到刚才的暂停记录，包含操作人和时间
5. ✅ 封禁用户"user2"，该用户当前 Session 立即失效（页面跳转到登录页）
6. ✅ 普通用户访问 `/admin/tenants` 返回 403 Forbidden

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **超管权限泄露** | 可跨租户访问敏感数据 | ① MFA 二次认证 ② IP 白名单 ③ 操作审计 ④ 定期审计超管账号 |
| **级联删除误操作** | 数据永久丢失 | ① 软删除（标记 DELETED）② 二次确认弹窗 ③ 30 天恢复期 |
| **审计日志过大** | 数据库性能下降 | ① 30 天归档到对象存储 ② 分区表（按月） ③ 热数据 7 天 |
| **Session 管理复杂** | 强制下线失效 | ① Sa-Token 统一管理 ② Redis 共享 Session ③ 定期清理 |

---

## 8. 参考文件锚点

### 8.1 依赖方案

- **多租户**：`docs/aegis/plans/saas-mvp-impl/01-multi-tenancy.md`（TenantContext 契约）
- **用户体系**：`docs/aegis/plans/saas-mvp-impl/03-user-system.md`（CurrentUser 模型）
- **计费系统**：`docs/aegis/plans/saas-mvp-impl/04-billing.md`（订阅状态查询）

### 8.2 相关代码（实施后）

- Repository：`seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcAdminRepositoryAdapter.java`
- Service：`seahorse-agent-kernel/.../kernel/application/admin/KernelAdminTenantService.java`
- Controller：`seahorse-agent-adapter-web/.../web/SeahorseAdminTenantController.java`
- 审计日志：`seahorse-agent-kernel/.../kernel/application/audit/KernelAuditLogService.java`

### 8.3 表结构

- `sa_tenant`：租户表（01-多租户创建）
- `t_user`：用户表（需增加 tenant_id、status、email）
- `sa_audit_log`：审计日志表（本方案新增）

---

**文档版本**：v1.0-final  
**最后更新**：2026-06-05  
**已确认决策**：
- 超管 IP 白名单：**必须启用**（配置在 `application.yml` 的 `seahorse.admin.allowed-ips`，空则全放行但记录告警）
- 审计日志归档：**30 天**（归档到对象存储，保留 90 天后彻底删除；合规要求高的客户可配置为 180 天）
- 级联删除：**默认关闭**（需二次确认 + 输入租户名验证，防误删）
