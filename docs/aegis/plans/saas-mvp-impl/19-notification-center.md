# 块I · 通知与消息中心 — 用户通知体系方案

> 文档定位：SaaS MVP 执行计划第 19 篇。功能增强系列之「用户体验」。  
> 关键属性：**P1 优先级、用户留存关键、独立可实施**。  
> 编写依据：2026-06-05 用户反馈 + SaaS 通知最佳实践。  
> 工作量口径：1 人 × 3 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 无站内消息系统（系统通知、告警看不到）
- ❌ 无邮件通知（注册、密码重置需手动发）
- ❌ 无 Webhook（第三方系统无法接收事件）
- ❌ 消息无模板（每次发通知硬编码文案）
- ❌ 无已读/未读（用户不知道哪些消息没看）

**用户痛点**：
- 😤 配额用完了不知道（没有提醒）
- 😤 支付成功没收到通知（不确定是否成功）
- 😤 系统维护无通知（突然无法访问）
- 😤 想接入企业微信但无 Webhook

**本方案价值**：
- ✅ 站内信（系统通知、告警通知、用户角标）
- ✅ 邮件通知（注册验证、密码重置、账单提醒）
- ✅ Webhook（第三方集成、事件推送）
- ✅ 消息模板（可配置、变量替换）
- ✅ 已读/未读（角标、列表、一键已读）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 |
|------|------|--------|------|
| G1 | 站内信系统（消息列表、角标、已读） | **P0** | 无 ❌ |
| G2 | 邮件通知（SMTP、模板、异步发送） | **P0** | 无 ❌ |
| G3 | Webhook（HTTP 回调、重试、签名） | P1 | 无 ❌ |
| G4 | 消息模板（配置化、变量替换） | **P0** | 硬编码 ⚠️ |
| G5 | 通知偏好设置（用户自定义开关） | P1 | 无 ❌ |

### 1.2 明确不做（后延）

- **不做** 短信通知（成本高，MVP 用邮件）
- **不做** Push 通知（移动端 App 未开发）
- **不做** 企业微信/钉钉机器人（P1，用 Webhook）
- **不做** 消息聚合（过于复杂）

### 1.3 验收信号

#### P0 验收

1. ✅ 配额用完时，用户收到站内信 + 邮件
2. ✅ 消息中心显示未读角标（数字）
3. ✅ 注册时发送验证邮件（包含激活链接）
4. ✅ 消息模板可在后台配置（无需改代码）

#### P1 验收

5. ⚠️ 配置 Webhook，支付成功后 POST 到指定 URL
6. ⚠️ 用户关闭邮件通知后不再发送

---

## 2. 现状（功能审查）

### 2.1 通知相关代码

**已有组件**：
- ✅ 05-observability 方案中的钉钉告警（`DingTalkAlertPort`）
- ⚠️ 无统一通知框架

**缺失功能**：
- ❌ 站内信存储
- ❌ 邮件发送
- ❌ Webhook 机制
- ❌ 消息模板引擎

---

## 3. 技术方案

### 3.1 站内信系统（P0）

#### 3.1.1 表设计

```sql
CREATE TABLE t_notification (
    notification_id BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,
    type            SMALLINT NOT NULL,
    -- 类型枚举：1=SYSTEM, 2=ALERT, 3=BILLING
    title           VARCHAR(256) NOT NULL,
    content         TEXT NOT NULL,
    link            VARCHAR(512),
    is_read         SMALLINT NOT NULL DEFAULT 0,
    -- 0=未读, 1=已读（SMALLINT 比 BOOLEAN 索引效率更可控）
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP WITH TIME ZONE
);

-- 索引（tenant_id 最左前缀，与 RLS 策略对齐）
CREATE INDEX idx_notification_tenant_time ON t_notification (tenant_id, created_at DESC);
CREATE INDEX idx_notification_user_read ON t_notification (user_id, is_read, created_at DESC);

-- CHECK 约束
ALTER TABLE t_notification ADD CONSTRAINT chk_notification_type
    CHECK (type IN (1, 2, 3));
ALTER TABLE t_notification ADD CONSTRAINT chk_notification_is_read
    CHECK (is_read IN (0, 1));
```

> **DDL 审查要点**：
> - ✅ `type` 从 `VARCHAR(32)` 改为 `SMALLINT`，存储开销从 ~32B 降到 2B
> - ✅ `is_read` 从 `BOOLEAN` 改为 `SMALLINT`（0/1），与项目其他布尔字段保持一致
> - ✅ 修正 MySQL 内联 `INDEX` 语法为 PostgreSQL `CREATE INDEX`
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`，支持国际化时区

#### 3.1.2 Domain 层

```java
// kernel/domain/notification/Notification.java
@Entity
@Table(name = "t_notification")
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;
    
    private String tenantId;
    private Long userId;
    
    // ✅ DDL 为 SMALLINT，使用 AttributeConverter 而非 @Enumerated(EnumType.STRING)
    @Convert(converter = NotificationTypeConverter.class)
    private NotificationType type;
    
    private String title;
    private String content;
    private String link;
    
    // ✅ DDL 为 SMALLINT(0/1)，不使用 Boolean（避免与 SMALLINT 不匹配）
    @Convert(converter = SmallIntBooleanConverter.class)
    private Boolean isRead = false;
    
    private Instant createdAt;
    private Instant readAt;
    
    public void markAsRead() {
        this.isRead = true;
        this.readAt = Instant.now();
    }
}

/**
 * NotificationType ↔ SMALLINT 转换器
 * 与 DDL CHECK (type IN (1, 2, 3)) 对齐
 */
@Converter
public class NotificationTypeConverter implements AttributeConverter<NotificationType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(NotificationType attr) {
        if (attr == null) return null;
        return switch (attr) {
            case SYSTEM -> 1;
            case ALERT -> 2;
            case BILLING -> 3;
        };
    }
    @Override
    public NotificationType convertToEntityAttribute(Integer dbData) {
        if (dbData == null) return null;
        return switch (dbData) {
            case 1 -> NotificationType.SYSTEM;
            case 2 -> NotificationType.ALERT;
            case 3 -> NotificationType.BILLING;
            default -> throw new IllegalArgumentException("Unknown type: " + dbData);
        };
    }
}

/**
 * Boolean ↔ SMALLINT(0/1) 转换器
 * 与 DDL SMALLINT DEFAULT 0 对齐
 */
@Converter
public class SmallIntBooleanConverter implements AttributeConverter<Boolean, Integer> {
    @Override
    public Integer convertToDatabaseColumn(Boolean attr) {
        return Boolean.TRUE.equals(attr) ? 1 : 0;
    }
    @Override
    public Boolean convertToEntityAttribute(Integer dbData) {
        return dbData != null && dbData == 1;
    }
}

public enum NotificationType {
    SYSTEM,   // 系统通知
    ALERT,    // 告警通知
    BILLING   // 账单通知
}
```

**Port**：
```java
public interface NotificationPort {
    void send(NotificationRequest request);
    List<Notification> getUnread(Long userId);
    int getUnreadCount(Long userId);
    void markAsRead(Long notificationId);
    void markAllAsRead(Long userId);
}
```

#### 3.1.3 Adapter 实现

```java
@Component
public class DatabaseNotificationAdapter implements NotificationPort {
    
    @Autowired
    private NotificationRepository repository;
    
    @Override
    public void send(NotificationRequest request) {
        Notification notification = Notification.builder()
            .tenantId(request.getTenantId())
            .userId(request.getUserId())
            .type(request.getType())
            .title(request.getTitle())
            .content(request.getContent())
            .link(request.getLink())
            .build();
        
        repository.save(notification);
    }
    
    @Override
    public List<Notification> getUnread(Long userId) {
        return repository.findByUserIdAndIsReadFalse(userId);
    }
    
    @Override
    public int getUnreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }
}
```

#### 3.1.4 前端组件

```typescript
// components/NotificationBell.tsx
import { Badge, Dropdown, List } from 'antd';
import { BellOutlined } from '@ant-design/icons';

export const NotificationBell = () => {
  const { data: notifications } = useNotifications();
  const unreadCount = notifications?.filter(n => !n.isRead).length || 0;
  
  const menu = (
    <List
      dataSource={notifications}
      renderItem={(item) => (
        <List.Item onClick={() => handleClick(item)}>
          <List.Item.Meta
            title={<span style={{ fontWeight: item.isRead ? 'normal' : 'bold' }}>
              {item.title}
            </span>}
            description={item.content}
          />
        </List.Item>
      )}
    />
  );
  
  return (
    <Dropdown overlay={menu} trigger={['click']}>
      <Badge count={unreadCount} offset={[-5, 5]}>
        <BellOutlined style={{ fontSize: 20, cursor: 'pointer' }} />
      </Badge>
    </Dropdown>
  );
};
```

---

### 3.2 邮件通知（P0）

#### 3.2.1 SMTP 配置

```properties
# application.properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# 发件人配置
seahorse.mail.from=noreply@seahorse.ai
seahorse.mail.from-name=Seahorse Agent
```

#### 3.2.2 邮件模板

**模板引擎**：Thymeleaf

**模板文件**（`resources/templates/email/welcome.html`）：
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>欢迎注册 Seahorse</title>
</head>
<body>
    <h2>欢迎，[[${username}]]！</h2>
    <p>感谢注册 Seahorse Agent 平台。</p>
    <p>请点击下方链接激活账号：</p>
    <a th:href="${activationUrl}">激活账号</a>
    <p>链接有效期：24 小时</p>
</body>
</html>
```

#### 3.2.3 邮件服务

```java
@Service
public class EmailNotificationService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private TemplateEngine templateEngine;
    
    @Value("${seahorse.mail.from}")
    private String from;
    
    @Async  // 异步发送
    public void sendWelcomeEmail(String to, String username, String activationUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // 渲染模板
            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("activationUrl", activationUrl);
            
            String html = templateEngine.process("email/welcome", context);
            
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("欢迎注册 Seahorse");
            helper.setText(html, true);
            
            mailSender.send(message);
            log.info("Welcome email sent to: {}", to);
            
        } catch (Exception ex) {
            log.error("Failed to send email to: {}", to, ex);
        }
    }
}
```

#### 3.2.4 常见邮件模板

| 模板 | 触发时机 | 变量 |
|------|---------|------|
| `welcome.html` | 用户注册 | username, activationUrl |
| `reset-password.html` | 忘记密码 | username, resetUrl, expiresIn |
| `quota-alert.html` | 配额告警 | username, quotaType, usage, limit |
| `payment-success.html` | 支付成功 | username, amount, planName, expireDate |

---

### 3.3 Webhook（P1）

#### 3.3.1 表设计

```sql
CREATE TABLE t_webhook (
    webhook_id   BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL,
    name         VARCHAR(128) NOT NULL,
    url          VARCHAR(512) NOT NULL,
    secret       VARCHAR(64) NOT NULL,
    events       TEXT[] NOT NULL,
    is_active    SMALLINT NOT NULL DEFAULT 1,
    -- 0=禁用, 1=启用
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_tenant ON t_webhook (tenant_id, is_active);

ALTER TABLE t_webhook ADD CONSTRAINT chk_webhook_is_active
    CHECK (is_active IN (0, 1));

CREATE TABLE t_webhook_log (
    log_id        BIGSERIAL PRIMARY KEY,
    tenant_id     VARCHAR(64) NOT NULL,
    webhook_id    BIGINT NOT NULL,
    event         VARCHAR(64) NOT NULL,
    request_body  TEXT NOT NULL,
    response_code SMALLINT,
    response_body TEXT,
    retry_count   SMALLINT NOT NULL DEFAULT 0,
    status        SMALLINT NOT NULL DEFAULT 0,
    -- 状态枚举：0=SUCCESS, 1=FAILED, 2=RETRYING
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_log_tenant ON t_webhook_log (tenant_id, webhook_id, created_at DESC);
CREATE INDEX idx_webhook_log_status ON t_webhook_log (status, created_at DESC);

ALTER TABLE t_webhook_log ADD CONSTRAINT chk_webhook_log_status
    CHECK (status IN (0, 1, 2));
```

> **DDL 审查要点**：
> - ✅ `is_active` 从 `BOOLEAN` 改为 `SMALLINT`（0/1），与项目统一
> - ✅ `status` 从 `VARCHAR(32)` 改为 `SMALLINT`，消除字符串比较
> - ✅ `response_code` 从 `INT` 改为 `SMALLINT`（HTTP 状态码最大 599）
> - ✅ `retry_count` 从 `INT` 改为 `SMALLINT`（重试次数有限）
> - ✅ `t_webhook_log` 补充 `tenant_id`，支持租户级日志查询
> - ✅ 修正 MySQL 内联 `INDEX` 为 PostgreSQL `CREATE INDEX`
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`

#### 3.3.2 Webhook 发送

```java
@Service
public class WebhookService {
    
    @Autowired
    private WebhookRepository webhookRepository;
    
    @Autowired
    private OkHttpClient httpClient;
    
    public void sendEvent(String tenantId, String event, Object payload) {
        List<Webhook> webhooks = webhookRepository
            .findByTenantIdAndEventsContaining(tenantId, event);
        
        webhooks.forEach(webhook -> 
            sendWebhook(webhook, event, payload)
        );
    }
    
    @Async
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void sendWebhook(Webhook webhook, String event, Object payload) {
        // ⚠️ @Retryable 必须标注在 public 方法上，Spring AOP 无法代理 private 方法
        try {
            String json = JSON.toJSONString(payload);
            String signature = generateSignature(json, webhook.getSecret());
            
            Request request = new Request.Builder()
                .url(webhook.getUrl())
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("X-Webhook-Event", event)
                .addHeader("X-Webhook-Signature", signature)
                .build();
            
            Response response = httpClient.newCall(request).execute();
            
            // 记录日志（✅ status 使用 Integer 与 DDL SMALLINT 对齐）
            webhookLogRepository.save(WebhookLog.builder()
                .webhookId(webhook.getId())
                .event(event)
                .requestBody(json)
                .responseCode(response.code())
                .responseBody(response.body().string())
                .status(response.isSuccessful() ? 0 : 1)  // 0=SUCCESS, 1=FAILED
                .build());
            
        } catch (Exception ex) {
            log.error("Webhook failed: webhookId={}, event={}", 
                webhook.getId(), event, ex);
            throw new WebhookException(ex);
        }
    }
    
    private String generateSignature(String payload, String secret) {
        return HmacUtils.hmacSha256Hex(secret, payload);
    }
}
```

#### 3.3.3 Webhook 验证（接收方）

```typescript
// 第三方系统接收 Webhook
import crypto from 'crypto';

app.post('/webhook', (req, res) => {
  const signature = req.headers['x-webhook-signature'];
  const payload = JSON.stringify(req.body);
  const secret = 'your-webhook-secret';
  
  // 验证签名
  const expected = crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('hex');
  
  if (signature !== expected) {
    return res.status(401).send('Invalid signature');
  }
  
  // 处理事件
  const event = req.headers['x-webhook-event'];
  console.log('Received event:', event, req.body);
  
  res.status(200).send('OK');
});
```

---

### 3.4 消息模板（P0）

#### 3.4.1 表设计

```sql
CREATE TABLE t_notification_template (
    template_id  BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64) NOT NULL,
    channel      SMALLINT NOT NULL,
    -- 渠道枚举：1=IN_APP（站内信）, 2=EMAIL, 3=WEBHOOK
    title        VARCHAR(256),
    content      TEXT NOT NULL,
    subject      VARCHAR(256),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 业务唯一约束：同一模板代码在同一渠道只有一条记录
    CONSTRAINT uk_template_code_channel UNIQUE (code, channel)
);

CREATE INDEX idx_notification_template_code ON t_notification_template (code, channel);

ALTER TABLE t_notification_template ADD CONSTRAINT chk_template_channel
    CHECK (channel IN (1, 2, 3));

-- 初始数据
INSERT INTO t_notification_template (code, channel, title, content) VALUES
('QUOTA_ALERT', 1, '配额告警', '您的 {{quotaType}} 配额已使用 {{usage}}/{{limit}}，请及时充值。'),
('QUOTA_ALERT', 2, NULL, '<p>尊敬的 {{username}}：</p><p>您的 {{quotaType}} 配额已使用 {{usage}}/{{limit}}。</p>');
```

> **DDL 审查要点**：
> - ✅ `channel` 从 `VARCHAR(32)` 改为 `SMALLINT`，消除字符串比较
> - ✅ `code` + `channel` 使用 `UNIQUE` 约束替代 `code` 单列 `UNIQUE`（同一代码可多渠道）
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`

#### 3.4.2 模板渲染

```java
@Service
public class NotificationTemplateService {
    
    @Autowired
    private NotificationTemplateRepository templateRepository;
    
    public String render(String templateCode, NotificationChannel channel, Map<String, Object> variables) {
        NotificationTemplate template = templateRepository
            .findByCodeAndChannel(templateCode, channel)
            .orElseThrow(() -> new TemplateNotFoundException(templateCode));
        
        String content = template.getContent();
        
        // 变量替换
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            content = content.replace(placeholder, String.valueOf(entry.getValue()));
        }
        
        return content;
    }
}
```

#### 3.4.3 使用示例

```java
// 发送配额告警
Map<String, Object> variables = Map.of(
    "username", user.getUsername(),
    "quotaType", "Token",
    "usage", "9000",
    "limit", "10000"
);

String content = templateService.render("QUOTA_ALERT", NotificationChannel.IN_APP, variables);

notificationPort.send(NotificationRequest.builder()
    .userId(user.getId())
    .type(NotificationType.ALERT)
    .title("配额告警")
    .content(content)
    .build());
```

---

### 3.5 通知偏好设置（P1）

#### 3.5.1 表设计

```sql
CREATE TABLE t_notification_preference (
    user_id       BIGINT NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL,
    email_system  SMALLINT NOT NULL DEFAULT 1,
    -- 0=关闭, 1=开启
    email_alert   SMALLINT NOT NULL DEFAULT 1,
    email_billing SMALLINT NOT NULL DEFAULT 1,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 每个用户只有一条偏好记录
    CONSTRAINT uk_notification_pref_user UNIQUE (user_id)
);

CREATE INDEX idx_notification_pref_tenant ON t_notification_preference (tenant_id);

-- CHECK 约束
ALTER TABLE t_notification_preference ADD CONSTRAINT chk_pref_email_system
    CHECK (email_system IN (0, 1));
ALTER TABLE t_notification_preference ADD CONSTRAINT chk_pref_email_alert
    CHECK (email_alert IN (0, 1));
ALTER TABLE t_notification_preference ADD CONSTRAINT chk_pref_email_billing
    CHECK (email_billing IN (0, 1));
```

> **DDL 审查要点**：
> - ✅ `BOOLEAN` 改为 `SMALLINT`（0/1），与项目其他布尔字段统一
> - ✅ 补充 `tenant_id` 字段，与多租户架构对齐
> - ✅ 主键改为 `UNIQUE` 约束（`user_id` 非自增主键但业务唯一）
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`

#### 3.5.2 发送时检查偏好

```java
@Service
public class NotificationService {
    
    public void sendNotification(NotificationRequest request) {
        // 站内信（总是发）
        notificationPort.send(request);
        
        // 邮件（检查用户偏好）
        NotificationPreference pref = preferenceRepository
            .findByUserId(request.getUserId())
            .orElse(NotificationPreference.defaultPreference());
        
        if (shouldSendEmail(request.getType(), pref)) {
            emailService.send(request);
        }
    }
    
    private boolean shouldSendEmail(NotificationType type, NotificationPreference pref) {
        return switch (type) {
            case SYSTEM -> pref.getEmailSystem();
            case ALERT -> pref.getEmailAlert();
            case BILLING -> pref.getEmailBilling();
        };
    }
}
```

---

### 3.6 站内信清理策略

> **问题**：站内信只写不清，长期运行后 `t_notification` 表行数可能达百万级，影响查询性能。

```java
@Component
public class NotificationCleanupJob {
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    /**
     * 每天凌晨 3 点清理已读且超过 90 天的站内信
     * 未读消息不删除（用户可能还没看）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        
        // 分批删除（每批 1000 条，避免长事务锁表）
        int deleted;
        int total = 0;
        do {
            deleted = notificationRepository.deleteReadBefore(cutoff, 1000);
            total += deleted;
        } while (deleted > 0);
        
        log.info("Cleaned up {} old notifications (cutoff: {})", total, cutoff);
    }
}
```

```java
// Repository 方法
@Modifying
@Query("""
    DELETE FROM Notification n 
    WHERE n.isRead = true AND n.createdAt < :cutoff
""")
int deleteReadBefore(@Param("cutoff") Instant cutoff, int limit);
// ⚠️ 注意：JPA 不支持 LIMIT 子句，实际实现需用 nativeQuery 或在 Service 层分页
```

> **策略说明**：
> - 只清理已读消息，未读消息永久保留（直到用户阅读后进入清理周期）
> - 90 天保留期与企业审计要求对齐（参见 20-audit-compliance）
> - 分批删除避免长事务（参见 15-data-consistency 事务管理策略）

---

## 4. 实施步骤

### Day 1：站内信 + 邮件
- 上午：站内信表设计 + 接口（3h）
- 下午：邮件服务 + 模板（2h）

### Day 2：Webhook + 模板
- 上午：Webhook 发送 + 签名（3h）
- 下午：消息模板引擎（2h）

### Day 3：前端 + 验收
- 上午：通知中心 UI（3h）
- 下午：集成测试（2h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 邮件送达率 > 95%
✅ Webhook 重试成功率 > 90%

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06
