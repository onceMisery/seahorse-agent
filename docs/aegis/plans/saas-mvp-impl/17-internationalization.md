# 块M · 多语言国际化 — i18n 全球化方案

> 文档定位：SaaS MVP 执行计划第 17 篇。功能增强系列之「全球化」。  
> 关键属性：**P2 优先级、国际化必需、独立可实施**。  
> 编写依据：2026-06-05 国际化需求 + i18n 最佳实践。  
> 工作量口径：1 人 × 2 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 界面硬编码中文（无法切换语言）
- ❌ 无语言包管理（翻译散落各处）
- ❌ 时区固定（东八区，海外用户不友好）
- ❌ 货币格式固定（¥，海外用户需要 $）
- ❌ 日期格式固定（YYYY-MM-DD，美国习惯 MM/DD/YYYY）

**用户痛点**：
- 😤 海外用户无法使用（全中文界面）
- 😤 时间显示混乱（显示北京时间，实际在美国）
- 😤 价格显示不友好（$9.99 显示为 ¥9.99）

**本方案价值**：
- ✅ 多语言支持（中文、英文，可扩展）
- ✅ 动态切换语言（无需刷新页面）
- ✅ 时区自动转换（显示用户本地时间）
- ✅ 货币格式化（¥、$、€）
- ✅ 日期格式化（根据语言自适应）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | 前端 i18n（react-i18next） | **P0** | 硬编码 ❌ | 支持中英文 |
| G2 | 后端 i18n（MessageSource） | P1 | 硬编码 ❌ | 支持中英文 |
| G3 | 语言包管理（集中管理） | **P0** | 无 ❌ | JSON 文件 |
| G4 | 动态切换语言 | **P0** | 无 ❌ | 下拉切换 |
| G5 | 时区处理（dayjs） | P1 | 固定东八区 ⚠️ | 自动检测 |
| G6 | 货币格式化（Intl.NumberFormat） | P1 | 固定 ¥ ⚠️ | $、€、¥ |

### 1.2 支持的语言

| 语言 | 代码 | 优先级 | 完成度 |
|------|------|--------|--------|
| 简体中文 | zh-CN | P0 | 100% |
| English | en-US | P0 | 0% → 100% |
| 日本語 | ja-JP | P2 | 待定 |
| 한국어 | ko-KR | P2 | 待定 |

### 1.3 验收信号

#### P0 验收

1. ✅ 切换语言后界面立即更新（中文 ↔ 英文）
2. ✅ 所有 UI 文案支持中英文
3. ✅ 语言偏好持久化（刷新后保持）
4. ✅ API 错误提示支持中英文

#### P1 验收

5. ⚠️ 时间显示本地时区（美国用户看到 PST）
6. ⚠️ 价格显示本地货币（$9.99）

---

## 2. 现状（国际化审查）

### 2.1 硬编码文案

**前端示例**：
```typescript
// ❌ 硬编码中文
<Button>创建知识库</Button>
<Modal title="确认删除">确定要删除吗？</Modal>
```

**后端示例**：
```java
// ❌ 硬编码中文
throw new BusinessException("用户名不能为空");
message.success("保存成功");
```

---

## 3. 技术方案

### 3.1 前端 i18n（P0）

#### 3.1.1 安装依赖

```bash
npm install react-i18next i18next i18next-browser-languagedetector
```

#### 3.1.2 配置 i18next

```typescript
// i18n/index.ts
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

import zhCN from './locales/zh-CN.json';
import enUS from './locales/en-US.json';

i18n
  .use(LanguageDetector)  // 自动检测语言
  .use(initReactI18next)  // React 集成
  .init({
    resources: {
      'zh-CN': { translation: zhCN },
      'en-US': { translation: enUS },
    },
    fallbackLng: 'zh-CN',  // 默认语言
    interpolation: {
      escapeValue: false,   // React 已转义
    },
  });

export default i18n;
```

#### 3.1.3 语言包

**中文语言包**（`locales/zh-CN.json`）：
```json
{
  "common": {
    "save": "保存",
    "cancel": "取消",
    "delete": "删除",
    "edit": "编辑",
    "search": "搜索",
    "loading": "加载中..."
  },
  "knowledgeBase": {
    "title": "知识库",
    "create": "创建知识库",
    "edit": "编辑知识库",
    "delete": "删除知识库",
    "confirmDelete": "确定要删除知识库「{{name}}」吗？",
    "deleteSuccess": "删除成功",
    "createSuccess": "创建成功"
  },
  "chat": {
    "title": "对话",
    "inputPlaceholder": "输入消息...",
    "send": "发送",
    "newChat": "新建对话"
  }
}
```

**英文语言包**（`locales/en-US.json`）：
```json
{
  "common": {
    "save": "Save",
    "cancel": "Cancel",
    "delete": "Delete",
    "edit": "Edit",
    "search": "Search",
    "loading": "Loading..."
  },
  "knowledgeBase": {
    "title": "Knowledge Base",
    "create": "Create Knowledge Base",
    "edit": "Edit Knowledge Base",
    "delete": "Delete Knowledge Base",
    "confirmDelete": "Are you sure to delete knowledge base \"{{name}}\"?",
    "deleteSuccess": "Deleted successfully",
    "createSuccess": "Created successfully"
  },
  "chat": {
    "title": "Chat",
    "inputPlaceholder": "Type a message...",
    "send": "Send",
    "newChat": "New Chat"
  }
}
```

#### 3.1.4 使用 i18n

```typescript
// index.tsx
import './i18n';

// App.tsx
import { useTranslation } from 'react-i18next';

function App() {
  const { t, i18n } = useTranslation();
  
  return (
    <>
      <h1>{t('knowledgeBase.title')}</h1>
      <Button>{t('knowledgeBase.create')}</Button>
      
      {/* 带参数 */}
      <Modal title={t('knowledgeBase.confirmDelete', { name: 'My KB' })}>
        ...
      </Modal>
    </>
  );
}
```

#### 3.1.5 语言切换器

```typescript
// components/LanguageSwitcher.tsx
import { Select } from 'antd';
import { useTranslation } from 'react-i18next';

export const LanguageSwitcher = () => {
  const { i18n } = useTranslation();
  
  const handleChange = (lang: string) => {
    i18n.changeLanguage(lang);
    localStorage.setItem('language', lang);
  };
  
  return (
    <Select
      value={i18n.language}
      onChange={handleChange}
      options={[
        { label: '简体中文', value: 'zh-CN' },
        { label: 'English', value: 'en-US' },
      ]}
    />
  );
};
```

#### 3.1.6 Ant Design 国际化

```typescript
// App.tsx
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import enUS from 'antd/locale/en_US';
import { useTranslation } from 'react-i18next';

function App() {
  const { i18n } = useTranslation();
  
  const antdLocale = i18n.language === 'zh-CN' ? zhCN : enUS;
  
  return (
    <ConfigProvider locale={antdLocale}>
      {/* ... */}
    </ConfigProvider>
  );
}
```

---

### 3.2 后端 i18n（P1）

#### 3.2.1 MessageSource 配置

```java
// configuration/I18nConfiguration.java
@Configuration
public class I18nConfiguration {
    
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }
    
    @Bean
    public LocaleResolver localeResolver() {
        // ⚠️ 注意：本项目使用 Sa-Token 而非 Spring Security，
        // SessionLocaleResolver 依赖 HttpSession，但 Sa-Token 默认不使用 Session。
        // 改用自定义 HeaderLocaleResolver，从 Accept-Language Header 读取语言
        return new HeaderLocaleResolver();
    }
    
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");  // ?lang=en-US
        return interceptor;
    }
}

/**
 * 自定义 LocaleResolver：从 Accept-Language Header 读取语言
 * 适配 Sa-Token（不依赖 HttpSession）
 */
public class HeaderLocaleResolver implements LocaleResolver {
    
    private static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;
    
    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String lang = request.getHeader("Accept-Language");
        if (lang != null && !lang.isEmpty()) {
            // 解析 "zh-CN,zh;q=0.9,en-US;q=0.8" → 取第一个
            String primary = lang.split(",")[0].split(";")[0].trim();
            return Locale.forLanguageTag(primary.replace("_", "-"));
        }
        return DEFAULT_LOCALE;
    }
    
    @Override
    public void setLocale(HttpServletRequest request, 
                          HttpServletResponse response, Locale locale) {
        response.setHeader("Content-Language", locale.toLanguageTag());
    }
}
```

#### 3.2.2 语言包

**中文**（`resources/i18n/messages_zh_CN.properties`）：
```properties
error.user.not_found=用户不存在
error.quota.exceeded=配额已用完
error.invalid_input=输入参数有误

success.created=创建成功
success.updated=更新成功
success.deleted=删除成功
```

**英文**（`resources/i18n/messages_en_US.properties`）：
```properties
error.user.not_found=User not found
error.quota.exceeded=Quota exceeded
error.invalid_input=Invalid input

success.created=Created successfully
success.updated=Updated successfully
success.deleted=Deleted successfully
```

#### 3.2.3 使用 MessageSource

```java
@Service
public class UserService {
    
    @Autowired
    private MessageSource messageSource;
    
    public void deleteUser(Long userId) {
        // ...
        
        String message = messageSource.getMessage(
            "success.deleted", 
            null, 
            LocaleContextHolder.getLocale()
        );
        
        // 返回国际化消息
        return ResponseEntity.ok(Map.of("message", message));
    }
}
```

#### 3.2.4 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @Autowired
    private MessageSource messageSource;
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        
        Locale locale = LocaleContextHolder.getLocale();
        String message = messageSource.getMessage(
            "error." + ex.getErrorCode(), 
            ex.getArgs(), 
            locale
        );
        
        return ResponseEntity.status(404)
            .body(ErrorResponse.of(ex.getErrorCode(), message));
    }
}
```

---

### 3.3 时区处理（P1）

#### 3.3.1 前端时区转换

```typescript
// utils/dateTime.ts
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

// 获取用户时区
const userTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;

// 转换 UTC 时间到本地时区
export const toLocalTime = (utcTime: string) => {
  return dayjs.utc(utcTime).tz(userTimezone).format('YYYY-MM-DD HH:mm:ss');
};

// 转换本地时间到 UTC
export const toUTC = (localTime: string) => {
  return dayjs.tz(localTime, userTimezone).utc().toISOString();
};
```

**使用**：
```typescript
<div>
  创建时间：{toLocalTime(document.createdAt)}
</div>
```

#### 3.3.2 后端时区处理

**统一使用 UTC 存储**：
```java
// Entity
@Entity
public class Document {
    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;  // 使用 Instant（UTC）
}

// API 返回时转换
@GetMapping("/documents/{id}")
public DocumentResponse getDocument(@PathVariable Long id) {
    Document doc = documentRepository.findById(id).orElseThrow();
    
    // 转换为用户时区
    String userTimezone = request.getHeader("X-Timezone");  // 前端传递
    ZonedDateTime localTime = doc.getCreatedAt()
        .atZone(ZoneId.of(userTimezone));
    
    return DocumentResponse.builder()
        .createdAt(localTime.toString())
        .build();
}
```

---

### 3.4 货币格式化（P1）

#### 3.4.1 前端货币格式化

```typescript
// utils/currency.ts
export const formatCurrency = (amount: number, currency: string, locale: string) => {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency: currency,
  }).format(amount);
};

// 使用
formatCurrency(9.99, 'USD', 'en-US');  // $9.99
formatCurrency(9.99, 'CNY', 'zh-CN');  // ¥9.99
formatCurrency(9.99, 'EUR', 'en-US');  // €9.99
```

**组件**：
```typescript
// components/Price.tsx
import { useTranslation } from 'react-i18next';

export const Price = ({ amount }: { amount: number }) => {
  const { i18n } = useTranslation();
  
  const currency = i18n.language === 'zh-CN' ? 'CNY' : 'USD';
  const formatted = formatCurrency(amount, currency, i18n.language);
  
  return <span>{formatted}</span>;
};
```

#### 3.4.2 后端货币转换

```java
@Service
public class CurrencyService {
    
    @Autowired
    private RedissonClient redis;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 汇率转换（优先从 Redis 缓存读取，缓存失效时调用外部 API）
     * 推荐汇率 API：https://exchangerate.host（免费）或 https://openexchangerates.org（企业级）
     */
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        BigDecimal rate = getExchangeRate(from, to);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal getExchangeRate(String from, String to) {
        String cacheKey = "exchange_rate:" + from + ":" + to;
        
        // 1. 尝试 Redis 缓存（TTL 1 小时）
        RBucket<String> bucket = redis.getBucket(cacheKey);
        String cached = bucket.get();
        if (cached != null) {
            return new BigDecimal(cached);
        }
        
        // 2. 调用外部汇率 API
        // TODO: 替换为实际 API Key，建议使用 @Value 注入
        String url = "https://api.exchangerate.host/convert?from=" + from + "&to=" + to;
        try {
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);
            BigDecimal rate = new BigDecimal(response.get("result").asText());
            
            // 3. 缓存 1 小时
            bucket.set(rate.toPlainString(), 1, TimeUnit.HOURS);
            return rate;
        } catch (Exception ex) {
            log.warn("Failed to fetch exchange rate, using fallback", ex);
            // 降级：返回固定汇率（仅在 API 不可用时使用）
            return getFallbackRate(from, to);
        }
    }
    
    private BigDecimal getFallbackRate(String from, String to) {
        // 降级汇率（最后更新：2026-06），仅供 API 不可用时兜底
        Map<String, BigDecimal> fallback = Map.of(
            "USD:CNY", new BigDecimal("7.2"),
            "CNY:USD", new BigDecimal("0.139"),
            "USD:EUR", new BigDecimal("0.92"),
            "EUR:USD", new BigDecimal("1.087")
        );
        return fallback.getOrDefault(from + ":" + to, BigDecimal.ONE);
    }
}
```

> **汇率数据来源说明**：
> - MVP 阶段可使用 `exchangerate.host`（免费，无需 API Key）
> - 生产环境建议切换到 `openexchangerates.org` 或银行数据源
> - 缓存 1 小时可平衡实时性和 API 调用频率
> - 降级策略确保 API 不可用时系统仍能运行

---

### 3.5 日期格式化（P1）

#### 3.5.1 根据语言自适应

```typescript
// utils/dateFormat.ts
import dayjs from 'dayjs';
import localizedFormat from 'dayjs/plugin/localizedFormat';
import 'dayjs/locale/zh-cn';
import 'dayjs/locale/en';

dayjs.extend(localizedFormat);

export const formatDate = (date: string, locale: string) => {
  const dayjsLocale = locale === 'zh-CN' ? 'zh-cn' : 'en';
  return dayjs(date).locale(dayjsLocale).format('LL');
};

// zh-CN: 2026年6月5日
// en-US: June 5, 2026
```

---

### 3.6 Admin 后台 i18n（P2）

> **注意**：Admin 管理后台（10-admin-ops）独立于 C 端前端，需单独处理 i18n。

**Admin 语言包扩展**（`locales/zh-CN.json` 中追加）：
```json
{
  "admin": {
    "dashboard": "管理后台",
    "userManagement": "用户管理",
    "tenantManagement": "租户管理",
    "quotaManagement": "配额管理",
    "auditLog": "审计日志",
    "systemConfig": "系统配置",
    "subscriptionPlan": "订阅套餐",
    "statistics": "数据统计"
  }
}
```

> **实施建议**：
> - Admin 后台文案较少（约 50-80 条），可在 P2 阶段补充
> - Admin 与 C 端共享 i18next 配置，仅需追加 `admin.*` 命名空间
> - 如果 Admin 后续独立部署，可拆分为独立的语言包文件

---

## 4. 实施步骤

### Day 1：前端 i18n
- 上午：配置 react-i18next（2h）
- 下午：创建语言包 + 替换硬编码文案（3h）

### Day 2：后端 i18n + 时区
- 上午：后端 MessageSource（2h）
- 下午：时区处理 + 货币格式化（2h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 所有 UI 文案支持中英文
✅ 语言切换无刷新

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06
