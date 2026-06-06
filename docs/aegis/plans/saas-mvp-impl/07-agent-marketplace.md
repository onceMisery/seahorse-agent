# 07 · Agent 市场（发布/订阅/评分）— 可落地技术方案

> 状态：已定稿 ｜ 作者：架构组 ｜ 日期：2026-06-05 ｜ 定位：主线功能增强
> **依赖**：01-多租户、03-用户体系、04-计费（付费 Agent）

---

## 1. 目标与范围

### 1.1 要做什么（MVP）

| 编号 | 目标 | 优先级 |
|------|------|--------|
| G1 | 发布流程：私有 Agent → 申请发布 → 审核 → 上架 | P0 |
| G2 | 订阅关系：用户订阅 Agent、权限检查、订阅统计 | P0 |
| G3 | 评分评论：5 星评分、文字评论、评论审核、评分聚合 | P0 |
| G4 | 热度排行：综合算法（订阅数+评分+活跃度）、分类排行 | P1 |
| G5 | 付费 Agent：定价（免费/一次性/订阅制）、收益分成 | P1 |

### 1.2 明确不做（后延）

- **不做** Agent 模板市场
- **不做**自动化测试沙箱
- **不做**版本兼容性检查

### 1.3 验收信号

1. ✅ 用户 A 发布 Agent"客服助手"，审核通过后出现在市场首页
2. ✅ 用户 B 订阅"客服助手"，可在"我的 Agent"列表看到并使用
3. ✅ 用户 B 给"客服助手"打 5 星并评论，评分更新为 4.8 星（10 人评价）
4. ✅ 热度排行显示"客服助手"第 1 名（100 订阅 + 4.8 星）

---

## 2. 现状（代码级审查）

### 2.1 AgentDefinition 模型

**表**：`sa_agent_definition`

```sql
CREATE TABLE sa_agent_definition (
    agent_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    version VARCHAR(32),
    status VARCHAR(32) DEFAULT 'DRAFT',  -- DRAFT/PUBLISHED
    tenant_id VARCHAR(64) DEFAULT 'default',
    created_by BIGINT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**缺口**：
- 无发布审核状态（PENDING_REVIEW/PUBLISHED/REJECTED/DELISTED）
- 无市场相关字段（价格、分类、标签）
- 无订阅表
- 无评分评论表

### 2.2 Service 现状

**类**：`KernelAgentService`  
**功能**：基础 Agent CRUD

**缺口**：无发布审核、订阅、评分功能

---

## 3. 技术方案

### 3.1 发布审核流程（P0）

#### 3.1.1 状态机

```
PRIVATE(私有) 
  → PENDING_REVIEW(待审核) 
  → PUBLISHED(已发布) / REJECTED(已拒绝)
  → DELISTED(已下架)
```

#### 3.1.2 数据模型

**扩展 `sa_agent_definition` 表**：

```sql
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS visibility VARCHAR(32) DEFAULT 'PRIVATE';
-- PRIVATE(私有)/PENDING_REVIEW(待审核)/PUBLISHED(已发布)/REJECTED(已拒绝)/DELISTED(已下架)

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS category VARCHAR(64);
-- 分类：客服/销售/HR/研发/通用

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS tags TEXT[];
-- 标签数组：['客服', '多轮对话', '知识库']

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS icon_url VARCHAR(512);

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS pricing_type VARCHAR(32) DEFAULT 'FREE';
-- FREE(免费)/ONE_TIME(一次性付费)/SUBSCRIPTION(订阅制)

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS price NUMERIC(10, 2) DEFAULT 0.00;

ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS review_status VARCHAR(32);
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS review_comment TEXT;
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE sa_agent_definition ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
```

**发布审核记录表**：`sa_agent_publish_review`

```sql
CREATE TABLE sa_agent_publish_review (
    review_id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    applicant_id BIGINT NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED
    reviewer_id BIGINT,
    review_comment TEXT,
    apply_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    review_time TIMESTAMP
);

CREATE INDEX idx_agent_review_agent ON sa_agent_publish_review(agent_id);
CREATE INDEX idx_agent_review_status ON sa_agent_publish_review(status);
```

#### 3.1.3 Service 实现

```java
public class KernelAgentMarketplaceService {
    
    public Long applyForPublish(String agentId, PublishApplication application, Long applicantId) {
        // 1. 检查 Agent 是否存在且为私有
        AgentDefinition agent = agentRepository.findById(agentId);
        if (agent.visibility() != Visibility.PRIVATE) {
            throw new IllegalStateException("只能发布私有 Agent");
        }
        
        // 2. 更新 Agent 状态
        agentRepository.updateVisibility(agentId, Visibility.PENDING_REVIEW);
        
        // 3. 创建审核记录
        return reviewRepository.create(agentId, applicantId, application.category(), application.tags());
    }
    
    public void approvePublish(Long reviewId, Long reviewerId, String comment) {
        ReviewRecord review = reviewRepository.findById(reviewId);
        
        // 更新审核状态
        reviewRepository.approve(reviewId, reviewerId, comment);
        
        // 发布 Agent
        agentRepository.updateVisibility(review.agentId(), Visibility.PUBLISHED);
        agentRepository.updateReviewInfo(review.agentId(), reviewerId, comment);
        
        // 发送通知给申请人
        notificationService.send(review.applicantId(), "您的 Agent 已通过审核");
    }
    
    public void rejectPublish(Long reviewId, Long reviewerId, String reason) {
        ReviewRecord review = reviewRepository.findById(reviewId);
        
        reviewRepository.reject(reviewId, reviewerId, reason);
        agentRepository.updateVisibility(review.agentId(), Visibility.REJECTED);
        
        notificationService.send(review.applicantId(), "您的 Agent 未通过审核：" + reason);
    }
}
```

### 3.2 订阅关系（P0）

#### 3.2.1 数据模型

**订阅表**：`sa_agent_subscription`

```sql
CREATE TABLE sa_agent_subscription (
    subscription_id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) DEFAULT 'default',
    subscription_type VARCHAR(32) DEFAULT 'FREE',  -- FREE/PAID
    payment_id BIGINT,  -- 关联 04-计费的订单
    subscribe_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_time TIMESTAMP,  -- 订阅制 Agent 的过期时间
    CONSTRAINT uk_agent_user_sub UNIQUE (agent_id, user_id)
);

CREATE INDEX idx_agent_sub_agent ON sa_agent_subscription(agent_id);
CREATE INDEX idx_agent_sub_user ON sa_agent_subscription(user_id);
CREATE INDEX idx_agent_sub_tenant ON sa_agent_subscription(tenant_id);
```

#### 3.2.2 Service 实现

```java
public class KernelAgentSubscriptionService {
    
    public void subscribe(String agentId, Long userId) {
        // 1. 检查 Agent 是否已发布
        AgentDefinition agent = agentRepository.findById(agentId);
        if (agent.visibility() != Visibility.PUBLISHED) {
            throw new IllegalStateException("只能订阅已发布的 Agent");
        }
        
        // 2. 检查是否已订阅
        if (subscriptionRepository.exists(agentId, userId)) {
            throw new IllegalStateException("已订阅该 Agent");
        }
        
        // 3. 付费 Agent 检查支付
        if (agent.pricingType() != PricingType.FREE) {
            // 跳转到支付流程（集成 04-计费）
            throw new PaymentRequiredException("需要支付");
        }
        
        // 4. 创建订阅
        subscriptionRepository.create(agentId, userId, SubscriptionType.FREE, null);
        
        // 5. 更新订阅统计
        agentRepository.incrementSubscriptionCount(agentId);
    }
    
    public void unsubscribe(String agentId, Long userId) {
        subscriptionRepository.delete(agentId, userId);
        agentRepository.decrementSubscriptionCount(agentId);
    }
    
    public boolean hasSubscribed(String agentId, Long userId) {
        return subscriptionRepository.exists(agentId, userId);
    }
    
    public PageResult<AgentDefinition> listMySubscriptions(Long userId, int page, int size) {
        List<SubscriptionRecord> subs = subscriptionRepository.findByUser(userId, page, size);
        List<String> agentIds = subs.stream().map(SubscriptionRecord::agentId).toList();
        List<AgentDefinition> agents = agentRepository.findByIds(agentIds);
        return new PageResult<>(agents, page, size, countUserSubscriptions(userId));
    }
}
```

#### 3.2.3 权限检查

**使用 Agent 前检查订阅**：

```java
@PreAuthorize("@agentSubscriptionService.hasSubscribed(#agentId, #userId)")
public AgentRunResult runAgent(String agentId, Long userId, AgentRunCommand command) {
    // 只有订阅了才能运行
    return agentRunService.run(agentId, command);
}
```

### 3.3 评分评论（P0）

#### 3.3.1 数据模型

**评分表**：`sa_agent_rating`

```sql
CREATE TABLE sa_agent_rating (
    rating_id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    status VARCHAR(32) DEFAULT 'APPROVED',  -- PENDING/APPROVED/REJECTED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_user_rating UNIQUE (agent_id, user_id)
);

CREATE INDEX idx_agent_rating_agent ON sa_agent_rating(agent_id);
CREATE INDEX idx_agent_rating_status ON sa_agent_rating(status);
```

**评分聚合表**：`sa_agent_rating_summary`

```sql
CREATE TABLE sa_agent_rating_summary (
    agent_id VARCHAR(64) PRIMARY KEY,
    avg_rating NUMERIC(3, 2) DEFAULT 0.00,
    rating_count INT DEFAULT 0,
    rating_distribution JSONB DEFAULT '{}'::jsonb,  -- {"5": 10, "4": 5, "3": 2, "2": 1, "1": 0}
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3.3.2 Service 实现

```java
public class KernelAgentRatingService {
    
    public void rate(String agentId, Long userId, int rating, String comment) {
        // 1. 检查是否已订阅
        if (!subscriptionService.hasSubscribed(agentId, userId)) {
            throw new ForbiddenException("只有订阅者可以评分");
        }
        
        // 2. 保存评分
        ratingRepository.upsert(agentId, userId, rating, comment);
        
        // 3. 更新聚合数据
        updateRatingSummary(agentId);
    }
    
    private void updateRatingSummary(String agentId) {
        List<RatingRecord> ratings = ratingRepository.findApprovedByAgent(agentId);
        
        double avgRating = ratings.stream()
            .mapToInt(RatingRecord::rating)
            .average()
            .orElse(0.0);
        
        Map<Integer, Long> distribution = ratings.stream()
            .collect(Collectors.groupingBy(RatingRecord::rating, Collectors.counting()));
        
        summaryRepository.update(agentId, avgRating, ratings.size(), distribution);
    }
    
    public PageResult<RatingRecord> listRatings(String agentId, int page, int size) {
        List<RatingRecord> ratings = ratingRepository.findApprovedByAgent(agentId, page, size);
        return new PageResult<>(ratings, page, size, countRatings(agentId));
    }
}
```

### 3.4 热度排行（P1）

#### 3.4.1 排行算法

**综合得分**：
```
score = subscription_count * 0.4 
      + (avg_rating / 5) * 0.3 
      + (active_run_count_7d / 100) * 0.2
      + (days_since_publish < 30 ? 0.1 : 0)  // 新上架加成
```

#### 3.4.2 数据模型

**热度表**：`sa_agent_popularity`

```sql
CREATE TABLE sa_agent_popularity (
    agent_id VARCHAR(64) PRIMARY KEY,
    score NUMERIC(10, 4) DEFAULT 0.0000,
    subscription_count INT DEFAULT 0,
    avg_rating NUMERIC(3, 2) DEFAULT 0.00,
    active_run_count_7d INT DEFAULT 0,
    last_calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_popularity_score ON sa_agent_popularity(score DESC);
```

#### 3.4.3 定时计算

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void recalculatePopularity() {
    List<AgentDefinition> publishedAgents = agentRepository.findAllPublished();
    
    publishedAgents.forEach(agent -> {
        int subscriptionCount = subscriptionRepository.count(agent.agentId());
        double avgRating = summaryRepository.getAvgRating(agent.agentId());
        int activeRunCount = runLogRepository.countLast7Days(agent.agentId());
        
        double score = subscriptionCount * 0.4
                     + (avgRating / 5.0) * 0.3
                     + (activeRunCount / 100.0) * 0.2
                     + (isNew(agent) ? 0.1 : 0);
        
        popularityRepository.updateScore(agent.agentId(), score, subscriptionCount, avgRating, activeRunCount);
    });
}
```

### 3.5 付费 Agent（P1）

#### 3.5.1 定价模型

| 类型 | 说明 | 价格字段 |
|------|------|----------|
| FREE | 免费 | price = 0 |
| ONE_TIME | 一次性付费 | price = 99.00（终身使用）|
| SUBSCRIPTION | 订阅制 | price = 9.90/月 |

#### 3.5.2 支付流程

```java
public void subscribePaidAgent(String agentId, Long userId) {
    AgentDefinition agent = agentRepository.findById(agentId);
    
    if (agent.pricingType() == PricingType.FREE) {
        subscriptionService.subscribe(agentId, userId);
        return;
    }
    
    // 1. 创建订单（集成 04-计费）
    Long orderId = paymentService.createOrder(userId, OrderType.AGENT_SUBSCRIPTION, agent.price());
    
    // 2. 跳转支付
    String paymentUrl = paymentService.getPaymentUrl(orderId);
    throw new PaymentRedirectException(paymentUrl);
}

// 支付回调
@PostMapping("/payment/callback")
public void handlePaymentCallback(@RequestBody PaymentCallbackData callback) {
    if (callback.status() == PaymentStatus.SUCCESS) {
        // 创建订阅
        subscriptionRepository.create(
            callback.agentId(), 
            callback.userId(), 
            SubscriptionType.PAID,
            callback.orderId()
        );
        
        // 分成：平台 20%，创作者 80%
        revenueService.distributeRevenue(callback.orderId(), 0.8);
    }
}
```

---

## 4. 前端实现

### 4.1 市场首页

```tsx
export const AgentMarketplacePage = () => {
  const [agents, setAgents] = useState([]);
  const [category, setCategory] = useState('all');
  
  useEffect(() => {
    fetch(`/api/marketplace/agents?category=${category}&sort=popularity`)
      .then(res => res.json())
      .then(({ data }) => setAgents(data.items));
  }, [category]);
  
  return (
    <div>
      <Tabs activeKey={category} onChange={setCategory}>
        <TabPane tab="全部" key="all" />
        <TabPane tab="客服" key="customer-service" />
        <TabPane tab="销售" key="sales" />
        <TabPane tab="研发" key="development" />
      </Tabs>
      
      <Row gutter={[16, 16]}>
        {agents.map(agent => (
          <Col span={6} key={agent.agentId}>
            <Card
              cover={<img src={agent.iconUrl} />}
              actions={[
                <StarFilled /> {agent.avgRating},
                <UserOutlined /> {agent.subscriptionCount},
                <Button type="primary" onClick={() => subscribe(agent.agentId)}>订阅</Button>
              ]}
            >
              <Card.Meta
                title={agent.name}
                description={agent.description}
              />
              {agent.pricingType !== 'FREE' && (
                <Tag color="orange">¥{agent.price}</Tag>
              )}
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
};
```

### 4.2 Agent 详情页

```tsx
export const AgentDetailPage = () => {
  const { agentId } = useParams();
  const [agent, setAgent] = useState(null);
  const [ratings, setRatings] = useState([]);
  
  const handleSubscribe = async () => {
    if (agent.pricingType === 'FREE') {
      await fetch(`/api/marketplace/agents/${agentId}/subscribe`, { method: 'POST' });
      message.success('订阅成功');
    } else {
      // 跳转支付
      const res = await fetch(`/api/marketplace/agents/${agentId}/subscribe`, { method: 'POST' });
      window.location.href = res.data.paymentUrl;
    }
  };
  
  return (
    <div>
      <Row gutter={24}>
        <Col span={6}>
          <img src={agent.iconUrl} style={{ width: '100%' }} />
        </Col>
        <Col span={18}>
          <h1>{agent.name}</h1>
          <p>{agent.description}</p>
          <Space>
            <Rate disabled value={agent.avgRating} />
            <span>{agent.avgRating} 星 ({agent.ratingCount} 人评价)</span>
          </Space>
          <div style={{ marginTop: 16 }}>
            <Button type="primary" size="large" onClick={handleSubscribe}>
              {agent.pricingType === 'FREE' ? '免费订阅' : `¥${agent.price} 订阅`}
            </Button>
          </div>
        </Col>
      </Row>
      
      <Divider />
      
      <h3>用户评价</h3>
      <List
        dataSource={ratings}
        renderItem={r => (
          <List.Item>
            <List.Item.Meta
              avatar={<Avatar src={r.userAvatar} />}
              title={<><Rate disabled value={r.rating} /> {r.username}</>}
              description={r.comment}
            />
            <div>{r.createdAt}</div>
          </List.Item>
        )}
      />
    </div>
  );
};
```

### 4.3 我的订阅页

```tsx
export const MySubscriptionsPage = () => {
  const [subscriptions, setSubscriptions] = useState([]);
  
  const handleRate = (agentId: string) => {
    Modal.confirm({
      title: '评分',
      content: (
        <Form id="rating-form">
          <Form.Item name="rating" label="评分">
            <Rate />
          </Form.Item>
          <Form.Item name="comment" label="评论">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      ),
      onOk: async () => {
        const formData = new FormData(document.getElementById('rating-form'));
        await fetch(`/api/marketplace/agents/${agentId}/ratings`, {
          method: 'POST',
          body: JSON.stringify(Object.fromEntries(formData))
        });
        message.success('评分成功');
      }
    });
  };
  
  return (
    <List
      dataSource={subscriptions}
      renderItem={sub => (
        <List.Item
          actions={[
            <Button onClick={() => navigate(`/agents/${sub.agentId}/run`)}>使用</Button>,
            <Button onClick={() => handleRate(sub.agentId)}>评分</Button>,
            <Button danger onClick={() => unsubscribe(sub.agentId)}>取消订阅</Button>
          ]}
        >
          <List.Item.Meta
            avatar={<Avatar src={sub.iconUrl} size={64} />}
            title={sub.name}
            description={sub.description}
          />
        </List.Item>
      )}
    />
  );
};
```

---

## 5. 任务清单

### Phase 1 — 发布审核（P0，第 1 周）

- [ ] **数据模型**
  - [ ] 扩展 `sa_agent_definition` 表
  - [ ] 创建 `sa_agent_publish_review` 表

- [ ] **核心功能**
  - [ ] `KernelAgentMarketplaceService` 实现
  - [ ] 申请发布 API
  - [ ] 审核通过/拒绝 API
  - [ ] 管理后台审核页面

### Phase 2 — 订阅与权限（P0，第 2 周）

- [ ] **数据模型**
  - [ ] 创建 `sa_agent_subscription` 表

- [ ] **核心功能**
  - [ ] `KernelAgentSubscriptionService` 实现
  - [ ] 订阅/取消订阅 API
  - [ ] 权限检查集成到 Agent 运行
  - [ ] 前端"我的订阅"页面

### Phase 3 — 评分与排行（P0，第 3 周）

- [ ] **数据模型**
  - [ ] 创建 `sa_agent_rating` 表
  - [ ] 创建 `sa_agent_rating_summary` 表
  - [ ] 创建 `sa_agent_popularity` 表

- [ ] **核心功能**
  - [ ] `KernelAgentRatingService` 实现
  - [ ] 评分/评论 API
  - [ ] 热度计算定时任务
  - [ ] 前端市场首页 + 排行榜

### Phase 4 — 付费 Agent（P1，第 4 周）

- [ ] **支付集成**
  - [ ] 集成 04-计费的支付流程
  - [ ] 支付回调处理
  - [ ] 收益分成逻辑

---

## 6. 验收标准

1. ✅ 用户 A 发布 Agent，状态变为 PENDING_REVIEW，管理员审核通过后市场可见
2. ✅ 用户 B 订阅该 Agent，在"我的订阅"看到，可点击"使用"跳转运行页
3. ✅ 用户 B 评 5 星 + 评论，Agent 评分更新，前端显示 4.8 星（10 人评价）
4. ✅ 热度排行第 1 名是订阅数最多且评分最高的 Agent

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **刷榜行为** | 热度排行失真 | ① 检测异常订阅（同 IP 大量订阅）② 评分权重降低 ③ 人工审核 |
| **恶意评论** | 影响 Agent 声誉 | ① 评论审核（敏感词过滤）② 举报机制 ③ 只有订阅者可评 |
| **付费纠纷** | 用户投诉 | ① 7 天无理由退款 ② 清晰的定价说明 ③ 试用功能 |

---

**文档版本**：v1.0-final  
**最后更新**：2026-06-05  
**已确认决策**：
- 收益分成：**创作者 80%，平台 20%**（对标 App Store 70/30，更有吸引力）
- 审核流程：**MVP 人工审核**（管理后台审核页面），Phase 2 增加自动审核（敏感词 + 合规检查）
