# 06 · 知识库增强（版本管理 + 权限控制 + 分享链接）— 可落地技术方案

> 状态：已定稿 ｜ 作者：架构组 ｜ 日期：2026-06-05 ｜ 定位：主线功能增强
> **依赖**：01-多租户（TenantContext）、03-用户体系（CurrentUser）

---

## 1. 目标与范围

### 1.1 要做什么（MVP）

| 编号 | 目标 | 优先级 |
|------|------|--------|
| G1 | 版本管理：手动/自动快照、回滚、版本对比（文档增删改 diff） | P0 |
| G2 | 权限控制：知识库级 RBAC（owner/editor/viewer 三角色） | P0 |
| G3 | 分享链接：生成公开链接、密码保护、过期时间、访问统计 | P1 |

### 1.2 明确不做（后延）

- **不做**团队协作（实时编辑冲突）
- **不做**知识库模板市场
- **不做**导入导出（Excel/PDF）

### 1.3 验收信号

1. ✅ 创建快照 V1，删除 2 个文档后回滚到 V1，文档恢复
2. ✅ 设置用户 B 为 VIEWER，用户 B 尝试上传文档返回 403
3. ✅ 生成分享链接（密码 1234），未登录用户输入密码后可查看文档

---

## 2. 现状（代码级审查）

### 2.1 当前表结构

**表**：`t_knowledge_base`

```sql
CREATE TABLE t_knowledge_base (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64),
    collection_name VARCHAR(64),
    created_by BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**缺口**：
- 无 `tenant_id`（待 01-多租户补充）
- 无权限关联表
- 无版本记录表

### 2.2 Service 现状

**类**：`KernelKnowledgeBaseService`  
**路径**：`seahorse-agent-kernel/.../kernel/application/knowledge/KernelKnowledgeBaseService.java`

**当前功能**：基础 CRUD（创建、更新、删除、查询）

**缺口**：无版本管理、无权限检查、无分享功能

---

## 3. 技术方案

### 3.1 版本管理（P0）

#### 3.1.1 数据模型

**新表**：`t_knowledge_base_version`

```sql
CREATE TABLE t_knowledge_base_version (
    version_id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    snapshot_data JSONB NOT NULL,  -- 文档列表快照
    snapshot_type VARCHAR(32) DEFAULT 'MANUAL',  -- MANUAL/AUTO
    created_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_kb_version UNIQUE (kb_id, version_number)
);

CREATE INDEX idx_kb_version_kb ON t_knowledge_base_version(kb_id);
```

**快照数据格式**（JSONB）：
```json
{
  "documents": [
    {"doc_id": 123, "doc_name": "产品手册.pdf", "chunk_count": 50, "file_url": "s3://..."},
    {"doc_id": 456, "doc_name": "FAQ.docx", "chunk_count": 20, "file_url": "s3://..."}
  ],
  "total_docs": 2,
  "total_chunks": 70,
  "snapshot_time": "2026-06-05T10:30:00Z"
}
```

#### 3.1.2 Service 实现

**新增 Service**：`KernelKnowledgeBaseVersionService`

```java
public class KernelKnowledgeBaseVersionService {
    
    private final KnowledgeBaseVersionRepositoryPort versionRepository;
    private final KnowledgeDocumentRepositoryPort documentRepository;
    
    public Long createSnapshot(Long kbId, String operator, SnapshotType type) {
        // 1. 查询当前文档列表
        List<DocumentRecord> docs = documentRepository.findByKbId(kbId);
        
        // 2. 构造快照数据
        SnapshotData snapshot = new SnapshotData(
            docs,
            docs.size(),
            docs.stream().mapToInt(DocumentRecord::chunkCount).sum(),
            Instant.now()
        );
        
        // 3. 保存快照
        int versionNumber = versionRepository.getLatestVersionNumber(kbId) + 1;
        return versionRepository.create(kbId, versionNumber, snapshot, type, operator);
    }
    
    public void rollback(Long kbId, Long versionId, String operator) {
        // 1. 加载快照
        VersionRecord version = versionRepository.findById(versionId);
        SnapshotData snapshot = objectMapper.readValue(version.snapshotData(), SnapshotData.class);
        
        // 2. 当前文档软删除
        documentRepository.softDeleteAllByKbId(kbId);
        
        // 3. 恢复快照中的文档
        snapshot.documents().forEach(doc -> {
            if (documentRepository.existsAndDeleted(doc.docId())) {
                documentRepository.restore(doc.docId());
            } else {
                // 文档已物理删除，从备份恢复
                documentRepository.recreateFromBackup(doc);
            }
        });
        
        // 4. 审计日志
        auditLogPort.log(new AuditLogEntry(
            operator, TenantContext.getTenantId(), "ROLLBACK_KB", 
            "知识库", kbId.toString(), "回滚到版本 " + version.versionNumber()
        ));
    }
    
    public VersionDiff compareVersions(Long kbId, int baseVersion, int targetVersion) {
        SnapshotData base = getSnapshot(kbId, baseVersion);
        SnapshotData target = getSnapshot(kbId, targetVersion);
        
        Set<Long> baseDocIds = base.documents().stream().map(DocumentRecord::docId).collect(Collectors.toSet());
        Set<Long> targetDocIds = target.documents().stream().map(DocumentRecord::docId).collect(Collectors.toSet());
        
        List<Long> added = targetDocIds.stream().filter(id -> !baseDocIds.contains(id)).toList();
        List<Long> removed = baseDocIds.stream().filter(id -> !targetDocIds.contains(id)).toList();
        
        return new VersionDiff(added, removed, base.totalDocs(), target.totalDocs());
    }
}
```

#### 3.1.3 自动快照触发

**触发时机**：
- 上传文档后（累计 10 个文档变更）
- 删除文档前（保护性快照）
- 手动点击"创建快照"按钮

```java
@EventListener
public void onDocumentUploaded(DocumentUploadedEvent event) {
    int changesSinceLastSnapshot = countChangesSinceLastSnapshot(event.kbId());
    
    if (changesSinceLastSnapshot >= 10) {
        versionService.createSnapshot(event.kbId(), "system", SnapshotType.AUTO);
    }
}

@EventListener
public void onDocumentDeleting(DocumentDeletingEvent event) {
    // 删除前保护性快照
    versionService.createSnapshot(event.kbId(), "system", SnapshotType.AUTO);
}
```

### 3.2 权限控制（P0）

#### 3.2.1 数据模型

**新表**：`t_knowledge_base_permission`

```sql
CREATE TABLE t_knowledge_base_permission (
    permission_id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,  -- OWNER/EDITOR/VIEWER
    granted_by BIGINT,
    grant_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id VARCHAR(64) DEFAULT 'default',
    CONSTRAINT uk_kb_user_perm UNIQUE (kb_id, user_id)
);

CREATE INDEX idx_kb_perm_kb ON t_knowledge_base_permission(kb_id);
CREATE INDEX idx_kb_perm_user ON t_knowledge_base_permission(user_id);
```

#### 3.2.2 权限检查

**新增 Service**：`KnowledgeBasePermissionService`

```java
public class KnowledgeBasePermissionService {
    
    private final KnowledgeBasePermissionRepositoryPort permissionRepository;
    private final CurrentUserPort currentUserPort;
    
    public boolean hasPermission(Long kbId, Long userId, KbRole requiredRole) {
        PermissionRecord perm = permissionRepository.findByKbAndUser(kbId, userId);
        
        if (perm == null) {
            // 无权限记录，检查是否为创建者
            KnowledgeBaseRecord kb = kbRepository.findById(kbId);
            return kb.createdBy().equals(userId) && requiredRole != KbRole.OWNER;
        }
        
        return switch (requiredRole) {
            case VIEWER -> true;  // 任何角色都能查看
            case EDITOR -> List.of(KbRole.EDITOR, KbRole.OWNER).contains(perm.role());
            case OWNER -> KbRole.OWNER == perm.role();
        };
    }
    
    public void grantPermission(Long kbId, Long targetUserId, KbRole role, Long operatorId) {
        // 检查操作人是否为 OWNER
        if (!hasPermission(kbId, operatorId, KbRole.OWNER)) {
            throw new ForbiddenException("只有 OWNER 可以授权");
        }
        
        permissionRepository.grant(kbId, targetUserId, role, operatorId);
    }
    
    public void revokePermission(Long kbId, Long targetUserId, Long operatorId) {
        if (!hasPermission(kbId, operatorId, KbRole.OWNER)) {
            throw new ForbiddenException("只有 OWNER 可以撤销权限");
        }
        
        permissionRepository.revoke(kbId, targetUserId);
    }
}
```

#### 3.2.3 权限检查切面

**自定义注解**：`@RequireKbPermission`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireKbPermission {
    KbRole value();
    String kbIdParam() default "kbId";
}
```

**AOP 拦截器**：

```java
@Aspect
@Component
public class KbPermissionAspect {
    
    @Around("@annotation(requireKbPermission)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequireKbPermission requireKbPermission) throws Throwable {
        // 提取 kbId 参数
        Long kbId = extractKbId(pjp, requireKbPermission.kbIdParam());
        Long userId = currentUserPort.currentUser().userId();
        
        if (!permissionService.hasPermission(kbId, userId, requireKbPermission.value())) {
            throw new ForbiddenException("权限不足：需要 " + requireKbPermission.value());
        }
        
        return pjp.proceed();
    }
}
```

**使用示例**：

```java
@RequireKbPermission(KbRole.EDITOR)
public void uploadDocument(Long kbId, MultipartFile file) {
    // 只有 EDITOR 和 OWNER 能上传
    documentService.upload(kbId, file);
}

@RequireKbPermission(KbRole.OWNER)
public void deleteKnowledgeBase(Long kbId) {
    // 只有 OWNER 能删除
    knowledgeBaseRepository.delete(kbId);
}
```

### 3.3 分享链接（P1）

#### 3.3.1 数据模型

**新表**：`t_knowledge_base_share`

```sql
CREATE TABLE t_knowledge_base_share (
    share_id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL,
    share_token VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128),  -- bcrypt 加密
    expires_at TIMESTAMP,
    access_count INT DEFAULT 0,
    last_accessed_at TIMESTAMP,
    created_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id VARCHAR(64) DEFAULT 'default'
);

CREATE INDEX idx_kb_share_token ON t_knowledge_base_share(share_token);
CREATE INDEX idx_kb_share_kb ON t_knowledge_base_share(kb_id);
```

**访问日志表**：`t_knowledge_base_share_access_log`

```sql
CREATE TABLE t_knowledge_base_share_access_log (
    log_id BIGSERIAL PRIMARY KEY,
    share_id BIGINT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    access_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_share_log_share ON t_knowledge_base_share_access_log(share_id);
```

#### 3.3.2 Service 实现

**新增 Service**：`KnowledgeBaseShareService`

```java
public class KnowledgeBaseShareService {
    
    private final KnowledgeBaseShareRepositoryPort shareRepository;
    private final PasswordEncoder passwordEncoder;
    
    public String createShareLink(Long kbId, ShareConfig config, Long userId) {
        // 检查权限
        if (!permissionService.hasPermission(kbId, userId, KbRole.VIEWER)) {
            throw new ForbiddenException("无权分享此知识库");
        }
        
        // 生成唯一 token
        String token = generateToken();
        String hashedPassword = config.password() != null 
            ? passwordEncoder.encode(config.password()) 
            : null;
        
        shareRepository.create(kbId, token, hashedPassword, config.expiresAt(), userId);
        
        return "/share/" + token;
    }
    
    public KnowledgeBaseShareView accessShare(String token, String password, String ipAddress, String userAgent) {
        ShareRecord share = shareRepository.findByToken(token);
        
        if (share == null) {
            throw new ShareNotFoundException("分享链接不存在");
        }
        
        // 检查过期
        if (share.expiresAt() != null && Instant.now().isAfter(share.expiresAt())) {
            throw new ShareExpiredException("分享链接已过期");
        }
        
        // 检查密码
        if (share.password() != null) {
            if (password == null || !passwordEncoder.matches(password, share.password())) {
                throw new PasswordIncorrectException("密码错误");
            }
        }
        
        // 记录访问
        shareRepository.incrementAccessCount(share.shareId());
        accessLogRepository.log(share.shareId(), ipAddress, userAgent);
        
        // 返回知识库信息
        KnowledgeBaseRecord kb = knowledgeBaseRepository.findById(share.kbId());
        List<DocumentRecord> docs = documentRepository.findByKbId(share.kbId());
        
        return new KnowledgeBaseShareView(kb, docs, share.accessCount() + 1);
    }
    
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}

record ShareConfig(String password, Instant expiresAt) {}
```

#### 3.3.3 Controller 层

```java
@RestController
@RequestMapping("/api/knowledge-bases")
public class SeahorseKnowledgeBaseShareController {
    
    @PostMapping("/{kbId}/share")
    public Map<String, Object> createShare(
            @PathVariable Long kbId,
            @RequestBody ShareCreateRequest request) {
        String shareUrl = shareService.createShareLink(
            kbId, 
            new ShareConfig(request.password(), request.expiresAt()),
            getCurrentUserId()
        );
        return Map.of("code", "0", "data", Map.of("shareUrl", shareUrl));
    }
    
    @GetMapping("/share/{token}")
    public Map<String, Object> accessShare(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            HttpServletRequest httpRequest) {
        KnowledgeBaseShareView view = shareService.accessShare(
            token, 
            password, 
            getClientIp(httpRequest),
            httpRequest.getHeader("User-Agent")
        );
        return Map.of("code", "0", "data", view);
    }
}

record ShareCreateRequest(String password, Instant expiresAt) {}
```

---

## 4. 前端实现

### 4.1 版本历史页

```tsx
import { Timeline, Button, Modal, message } from 'antd';

export const KnowledgeBaseVersionHistory = ({ kbId }: { kbId: number }) => {
  const [versions, setVersions] = useState([]);
  
  useEffect(() => {
    fetch(`/api/knowledge-bases/${kbId}/versions`)
      .then(res => res.json())
      .then(({ data }) => setVersions(data));
  }, [kbId]);
  
  const handleRollback = (versionId: number, versionNumber: number) => {
    Modal.confirm({
      title: `确认回滚到版本 ${versionNumber}？`,
      content: '当前文档将被快照中的文档覆盖',
      onOk: async () => {
        await fetch(`/api/knowledge-bases/${kbId}/versions/${versionId}/rollback`, {
          method: 'POST'
        });
        message.success('已回滚');
        window.location.reload();
      }
    });
  };
  
  return (
    <div>
      <Button type="primary" onClick={() => createSnapshot(kbId)}>创建快照</Button>
      <Timeline style={{ marginTop: 20 }}>
        {versions.map(v => (
          <Timeline.Item key={v.versionId} color={v.snapshotType === 'AUTO' ? 'gray' : 'blue'}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <div>
                <strong>版本 {v.versionNumber}</strong> 
                <Tag style={{ marginLeft: 8 }}>{v.snapshotType}</Tag>
              </div>
              <Button size="small" onClick={() => handleRollback(v.versionId, v.versionNumber)}>
                回滚
              </Button>
            </div>
            <div style={{ color: '#999', fontSize: 12 }}>
              {v.createTime} · 文档数：{v.snapshotData.total_docs}
            </div>
          </Timeline.Item>
        ))}
      </Timeline>
    </div>
  );
};
```

### 4.2 权限设置页

```tsx
export const KnowledgeBasePermissionSettings = ({ kbId }: { kbId: number }) => {
  const [permissions, setPermissions] = useState([]);
  const [modalVisible, setModalVisible] = useState(false);
  
  const columns = [
    { title: '用户', dataIndex: 'username', key: 'username' },
    { 
      title: '角色', 
      dataIndex: 'role', 
      key: 'role',
      render: (role: string) => <Tag color={role === 'OWNER' ? 'red' : role === 'EDITOR' ? 'blue' : 'default'}>{role}</Tag>
    },
    { title: '授权时间', dataIndex: 'grantTime', key: 'grantTime' },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        record.role !== 'OWNER' && (
          <Button danger size="small" onClick={() => revokePermission(record.permissionId)}>
            移除
          </Button>
        )
      ),
    },
  ];
  
  const addUser = async (values: { userId: number; role: string }) => {
    await fetch(`/api/knowledge-bases/${kbId}/permissions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values)
    });
    message.success('已添加');
    setModalVisible(false);
    loadPermissions();
  };
  
  return (
    <div>
      <Button type="primary" onClick={() => setModalVisible(true)}>添加成员</Button>
      <Table columns={columns} dataSource={permissions} style={{ marginTop: 16 }} />
      
      <Modal title="添加成员" visible={modalVisible} onCancel={() => setModalVisible(false)}>
        <Form onFinish={addUser}>
          <Form.Item name="userId" label="用户" rules={[{ required: true }]}>
            <Select placeholder="选择用户">
              {/* 用户列表 */}
            </Select>
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select>
              <Option value="VIEWER">查看者</Option>
              <Option value="EDITOR">编辑者</Option>
            </Select>
          </Form.Item>
          <Button type="primary" htmlType="submit">确定</Button>
        </Form>
      </Modal>
    </div>
  );
};
```

### 4.3 分享链接页

```tsx
export const KnowledgeBaseShareModal = ({ kbId }: { kbId: number }) => {
  const [form] = Form.useForm();
  const [shareUrl, setShareUrl] = useState('');
  
  const handleCreate = async (values: any) => {
    const res = await fetch(`/api/knowledge-bases/${kbId}/share`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values)
    });
    const { data } = await res.json();
    setShareUrl(window.location.origin + data.shareUrl);
  };
  
  const copyToClipboard = () => {
    navigator.clipboard.writeText(shareUrl);
    message.success('已复制到剪贴板');
  };
  
  return (
    <Modal title="生成分享链接" visible={true}>
      <Form form={form} onFinish={handleCreate}>
        <Form.Item name="password" label="访问密码">
          <Input.Password placeholder="留空则无需密码" />
        </Form.Item>
        <Form.Item name="expiresAt" label="过期时间">
          <DatePicker showTime placeholder="留空则永久有效" />
        </Form.Item>
        <Button type="primary" htmlType="submit">生成链接</Button>
      </Form>
      
      {shareUrl && (
        <div style={{ marginTop: 16, padding: 12, background: '#f5f5f5', borderRadius: 4 }}>
          <Input value={shareUrl} readOnly addonAfter={<Button size="small" onClick={copyToClipboard}>复制</Button>} />
        </div>
      )}
    </Modal>
  );
};
```

---

## 5. 任务清单

### Phase 1 — 版本管理（P0，第 1 周）

- [ ] **数据模型**
  - [ ] `t_knowledge_base_version` 表创建
  - [ ] `KernelKnowledgeBaseVersionService` 实现
  - [ ] `KnowledgeBaseVersionRepositoryPort` + Adapter

- [ ] **核心功能**
  - [ ] 创建快照 API
  - [ ] 回滚 API
  - [ ] 版本对比 API
  - [ ] 自动快照触发逻辑

- [ ] **前端**
  - [ ] 版本历史页
  - [ ] 手动创建快照按钮
  - [ ] 回滚确认弹窗

### Phase 2 — 权限控制（P0，第 2 周）

- [ ] **数据模型**
  - [ ] `t_knowledge_base_permission` 表创建
  - [ ] `KnowledgeBasePermissionService` 实现

- [ ] **权限检查**
  - [ ] `@RequireKbPermission` 注解 + AOP
  - [ ] 集成到文档上传/删除等操作

- [ ] **前端**
  - [ ] 权限设置页
  - [ ] 添加/移除成员功能

### Phase 3 — 分享链接（P1，第 3 周）

- [ ] **数据模型**
  - [ ] `t_knowledge_base_share` 表创建
  - [ ] `t_knowledge_base_share_access_log` 表创建

- [ ] **核心功能**
  - [ ] 生成分享链接 API
  - [ ] 访问分享链接 API（含密码验证）
  - [ ] 访问统计

- [ ] **前端**
  - [ ] 生成分享链接弹窗
  - [ ] 访问分享链接页面（无需登录）

---

## 6. 验收标准

1. ✅ 创建快照 V1（3 个文档），删除 2 个文档，回滚到 V1，文档恢复到 3 个
2. ✅ 用户 A 设置用户 B 为 VIEWER，用户 B 上传文档返回 403 Forbidden
3. ✅ 生成分享链接（密码 1234，7 天过期），未登录用户输入密码后可查看文档列表
4. ✅ 版本对比显示"版本 2 vs 版本 1：+2 文档，-1 文档"

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **快照数据过大** | JSONB 字段膨胀，查询变慢 | ① 快照只存元数据，不存文件内容 ② 压缩 JSON ③ 超过 100 个文档时归档到对象存储 |
| **回滚失败** | 文档丢失 | ① 回滚前创建保护性快照 ② 事务内执行 ③ 失败后自动回滚事务 |
| **权限绕过** | 越权访问 | ① AOP 统一拦截 ② 单元测试覆盖所有操作 ③ 代码审查 |
| **分享链接滥用** | 未授权访问 | ① 密码保护 ② 过期时间 ③ 访问频率限制（同 IP 1 分钟最多 10 次）|

---

## 8. 参考文件锚点

### 8.1 依赖方案

- **多租户**：`docs/aegis/plans/saas-mvp-impl/01-multi-tenancy.md`
- **用户体系**：`docs/aegis/plans/saas-mvp-impl/03-user-system.md`

### 8.2 相关代码（实施后）

- Service：`seahorse-agent-kernel/.../kernel/application/knowledge/KernelKnowledgeBaseVersionService.java`
- Controller：`seahorse-agent-adapter-web/.../web/SeahorseKnowledgeBaseShareController.java`

---

**文档版本**：v1.0-final  
**最后更新**：2026-06-05  
**已确认决策**：
- 快照存储：**JSONB 存元数据**（< 100 文档用 JSONB；≥ 100 文档压缩后存对象存储，JSONB 只存索引）
- 权限继承：**MVP 仅个人权限**（无团队概念）；Phase 2 增加团队权限（优先级：团队 OWNER > 个人 OWNER > 团队 EDITOR > 个人 EDITOR）
