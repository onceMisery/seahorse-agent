# 块G · 前端交互体验增强 — 用户体验优化方案

> 文档定位：SaaS MVP 执行计划第 11 篇。功能增强系列之「前端体验」。  
> 关键属性：**P1 优先级、用户体验直接影响、独立可实施**。  
> 编写依据：2026-06-05 前端代码审查 + 现代 Web 最佳实践。  
> 工作量口径：1 人 × 3-4 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 流式对话体验差（断线后无法恢复、无打字机效果）
- ❌ Markdown 内容显示为纯文本（代码高亮缺失）
- ❌ 文件上传无进度（用户不知道是否在传输）
- ❌ 加载状态不一致（有些用 Spin，有些用 Loading...）
- ❌ 错误提示不友好（"Network Error" 用户看不懂）
- ❌ 移动端布局错位

**用户痛点**：
- 😤 AI 回复到一半断开，无法继续
- 😤 代码块无法复制、无高亮
- 😤 上传大文件不知道进度，以为卡死
- 😤 网络错误后不知道该做什么
- 😤 手机访问布局错乱

**本方案价值**：
- ✅ 流式对话优化（fetch SSE 长连接、断线重连、打字机效果）
- ✅ 富文本编辑器（Markdown 预览、代码高亮、一键复制）
- ✅ 文件上传体验（拖拽上传、进度显示、断点续传）
- ✅ 加载状态统一（骨架屏、Loading 动画规范）
- ✅ 错误提示友好化（网络错误、超时、权限不足的中文提示）
- ✅ 响应式布局完善（移动端适配、平板适配）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 |
|------|------|--------|------|
| G1 | 流式对话优化（fetch SSE 长连接、断线重连） | **P0** | 部分实现 ⚠️ |
| G2 | 富文本编辑器（Markdown 预览、代码高亮） | **P0** | 无 ❌ |
| G3 | 文件上传体验（拖拽、进度、断点续传） | **P0** | 基础实现 ⚠️ |
| G4 | 加载状态统一（骨架屏、Loading 规范） | P1 | 不统一 ⚠️ |
| G5 | 错误提示友好化（中文提示、操作指引） | **P0** | 英文错误 ⚠️ |
| G6 | 响应式布局完善（移动端、平板适配） | P1 | 部分适配 ⚠️ |

### 1.2 明确不做（后延）

- **不做** 暗黑模式（Dark Mode）— Phase 2
- **不做** 多标签页管理（Tab）— Phase 2
- **不做** 键盘快捷键（Hotkey）— Phase 2
- **不做** 语音输入 — Phase 2

### 1.3 验收信号

#### P0 验收

1. ✅ 流式对话：AI 回复时显示打字机效果，断线自动重连（3 次重试）
2. ✅ Markdown 渲染：代码块高亮，一键复制代码
3. ✅ 文件上传：拖拽上传 10MB 文件，显示进度条
4. ✅ 错误提示：网络断开显示"网络连接失败，请检查网络后重试"（非 "Network Error"）

#### P1 验收

5. ⚠️ 骨架屏：列表页面首次加载显示骨架屏（非 Spin）
6. ⚠️ 移动端：手机访问布局正常，按钮可点击

---

## 2. 现状（代码级审查）

### 2.1 流式对话现状

**当前实现**（`ChatWindow.tsx`）：
```typescript
const eventSource = new EventSource(`/api/chat/stream?sessionId=${sessionId}`);

eventSource.onmessage = (event) => {
  setMessages(prev => [...prev, JSON.parse(event.data)]);
};

eventSource.onerror = () => {
  eventSource.close();
  message.error('连接失败');
};
```

**问题**：
- ❌ 无断线重连（onerror 直接关闭）
- ❌ 无打字机效果（整条消息一次性显示）
- ❌ 无超时检测（如果后端卡住，前端无限等待）

### 2.2 Markdown 渲染现状

**当前实现**：
```typescript
<div>{message.content}</div>  {/* 纯文本显示 */}
```

**问题**：
- ❌ Markdown 不渲染（`**粗体**` 显示为原始文本）
- ❌ 代码块无高亮（Python/Java 代码无法区分）
- ❌ 无复制按钮（用户需手动选择复制）

### 2.3 文件上传现状

**当前实现**（`Upload` 组件）：
```typescript
<Upload action="/api/upload">
  <Button>上传文件</Button>
</Upload>
```

**问题**：
- ❌ 无拖拽支持（只能点击上传）
- ❌ 无进度显示（Ant Design 默认进度条不明显）
- ❌ 无断点续传（网络中断后需重新上传）

---

## 3. 技术方案

### 3.1 流式对话优化（P0）

#### 3.1.1 SSE 断线重连（使用 fetch + ReadableStream）

> **架构决策**：不使用原生 `EventSource`，因为它不支持自定义 Header（无法携带 Sa-Token 认证信息）。
> 改用 `@microsoft/fetch-event-source` 库，它基于 `fetch` API，支持自定义 Header 和 POST 请求。

**依赖**：
```bash
npm install @microsoft/fetch-event-source
```

```typescript
// hooks/useStreamingChat.ts
import { useEffect, useRef, useState } from 'react';
import { fetchEventSource } from '@microsoft/fetch-event-source';
import { message } from 'antd';
import { useUserStore } from '@/stores/useUserStore';

export const useStreamingChat = (sessionId: string) => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const abortControllerRef = useRef<AbortController | null>(null);
  const retryCountRef = useRef(0);
  const maxRetries = 3;
  
  const connect = async () => {
    // 取消上一次连接
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    
    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    
    const token = useUserStore.getState().token;
    
    await fetchEventSource(`/api/chat/stream?sessionId=${sessionId}`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`,  // ✅ 支持自定义 Header
        'Accept': 'text/event-stream',
      },
      signal: abortController.signal,
      
      onopen: async (response) => {
        if (response.ok) {
          setIsConnected(true);
          retryCountRef.current = 0;
        } else {
          throw new Error(`SSE connection failed: ${response.status}`);
        }
      },
      
      onmessage: (event) => {
        if (!event.data) return;
        const chunk = JSON.parse(event.data);
        
        // 追加消息片段（打字机效果）
        setMessages(prev => {
          const last = prev[prev.length - 1];
          if (last && last.id === chunk.messageId) {
            return [
              ...prev.slice(0, -1),
              { ...last, content: last.content + chunk.content }
            ];
          } else {
            return [...prev, { id: chunk.messageId, content: chunk.content, role: 'assistant' }];
          }
        });
      },
      
      onerror: (err) => {
        setIsConnected(false);
        
        // 自动重连（指数退避）
        if (retryCountRef.current < maxRetries) {
          const delay = Math.pow(2, retryCountRef.current) * 1000;  // 1s, 2s, 4s
          retryCountRef.current++;
          
          console.log(`Reconnecting in ${delay}ms (attempt ${retryCountRef.current})`);
          return delay;  // fetch-event-source 使用返回值作为重连延迟
        } else {
          message.error('连接失败，请刷新页面重试');
          throw err;  // 停止重连
        }
      },
    });
  };
  
  useEffect(() => {
    connect();
    
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, [sessionId]);
  
  return { messages, isConnected };
};
```

#### 3.1.2 打字机效果（已实现）

上述 `onmessage` 逻辑已实现打字机效果（逐字符追加）。

#### 3.1.3 超时检测

```typescript
const [lastMessageTime, setLastMessageTime] = useState(Date.now());

eventSource.onmessage = (event) => {
  setLastMessageTime(Date.now());
  // ... 处理消息
};

// 超时检测（30 秒无消息视为超时）
useEffect(() => {
  const timer = setInterval(() => {
    if (isConnected && Date.now() - lastMessageTime > 30000) {
      message.warning('连接超时，正在重连...');
      connect();
    }
  }, 5000);
  
  return () => clearInterval(timer);
}, [isConnected, lastMessageTime]);
```

---

### 3.2 富文本编辑器（P0）

#### 3.2.1 Markdown 渲染

**依赖**：
```bash
npm install react-markdown remark-gfm rehype-highlight
```

**组件**：
```typescript
// components/MarkdownRenderer.tsx
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeHighlight from 'rehype-highlight';
import 'highlight.js/styles/github-dark.css';

export const MarkdownRenderer = ({ content }: { content: string }) => {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeHighlight]}
      components={{
        code({ node, inline, className, children, ...props }) {
          const match = /language-(\w+)/.exec(className || '');
          const language = match ? match[1] : '';
          
          return !inline ? (
            <CodeBlock language={language} code={String(children)} />
          ) : (
            <code className={className} {...props}>
              {children}
            </code>
          );
        }
      }}
    >
      {content}
    </ReactMarkdown>
  );
};
```

#### 3.2.2 代码块一键复制

```typescript
// components/CodeBlock.tsx
import { useState } from 'react';
import { Button, message } from 'antd';
import { CopyOutlined, CheckOutlined } from '@ant-design/icons';

export const CodeBlock = ({ language, code }: { language: string; code: string }) => {
  const [copied, setCopied] = useState(false);
  
  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    message.success('代码已复制');
    
    setTimeout(() => setCopied(false), 2000);
  };
  
  return (
    <div className="code-block">
      <div className="code-header">
        <span className="language">{language}</span>
        <Button 
          type="text" 
          size="small" 
          icon={copied ? <CheckOutlined /> : <CopyOutlined />}
          onClick={handleCopy}
        >
          {copied ? '已复制' : '复制'}
        </Button>
      </div>
      <pre>
        <code className={`language-${language}`}>{code}</code>
      </pre>
    </div>
  );
};
```

**样式**：
```css
.code-block {
  position: relative;
  margin: 16px 0;
  border-radius: 8px;
  overflow: hidden;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #1e1e1e;
  border-bottom: 1px solid #333;
}

.language {
  color: #888;
  font-size: 12px;
  text-transform: uppercase;
}
```

---

### 3.3 文件上传体验（P0）

#### 3.3.1 拖拽上传

```typescript
// components/DragUpload.tsx
import { Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';

const { Dragger } = Upload;

export const DragUpload = () => {
  const props: UploadProps = {
    name: 'file',
    multiple: true,
    action: '/api/upload',
    maxCount: 10,
    accept: '.pdf,.docx,.txt,.md',
    
    onChange(info) {
      const { status } = info.file;
      if (status === 'done') {
        message.success(`${info.file.name} 上传成功`);
      } else if (status === 'error') {
        message.error(`${info.file.name} 上传失败`);
      }
    },
    
    onDrop(e) {
      console.log('Dropped files', e.dataTransfer.files);
    },
  };
  
  return (
    <Dragger {...props}>
      <p className="ant-upload-drag-icon">
        <InboxOutlined />
      </p>
      <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
      <p className="ant-upload-hint">
        支持单个或批量上传，最多 10 个文件
      </p>
    </Dragger>
  );
};
```

#### 3.3.2 自定义进度条

```typescript
<Upload
  showUploadList={{
    showProgress: true,
    progressProps: {
      strokeColor: {
        '0%': '#108ee9',
        '100%': '#87d068',
      },
      strokeWidth: 3,
      format: (percent) => `${Math.round(percent!)}%`,
    },
  }}
>
  <Button icon={<UploadOutlined />}>上传文件</Button>
</Upload>
```

#### 3.3.3 分片上传（可选 P2）

> **架构决策**：MVP 阶段不使用 tus 断点续传协议（需后端部署 tusd 服务或实现 tus 协议，成本过高）。
> 改为前端分片上传（`Blob.slice`）+ 后端合并，满足大文件上传需求。

```typescript
// utils/chunkedUpload.ts
import { message } from 'antd';

const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB 每片

export const chunkedUpload = async (
  file: File,
  onProgress?: (percent: number) => void
) => {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  const uploadId = crypto.randomUUID();
  
  for (let i = 0; i < totalChunks; i++) {
    const chunk = file.slice(i * CHUNK_SIZE, (i + 1) * CHUNK_SIZE);
    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('uploadId', uploadId);
    formData.append('chunkIndex', String(i));
    formData.append('totalChunks', String(totalChunks));
    formData.append('fileName', file.name);
    
    await fetch('/api/upload/chunk', {
      method: 'POST',
      body: formData,
      headers: {
        'Authorization': `Bearer ${useUserStore.getState().token}`,
      },
    });
    
    onProgress?.(Math.round(((i + 1) / totalChunks) * 100));
  }
  
  // 通知后端合并分片
  await fetch('/api/upload/merge', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${useUserStore.getState().token}`,
    },
    body: JSON.stringify({ uploadId, fileName: file.name, totalChunks }),
  });
  
  message.success('上传成功');
};
```

---

### 3.4 加载状态统一（P1）

#### 3.4.1 骨架屏

```typescript
// components/KnowledgeBaseSkeleton.tsx
import { Skeleton, Card } from 'antd';

export const KnowledgeBaseSkeleton = () => {
  return (
    <div className="kb-list">
      {[1, 2, 3].map(i => (
        <Card key={i}>
          <Skeleton active paragraph={{ rows: 3 }} />
        </Card>
      ))}
    </div>
  );
};
```

**使用**：
```typescript
const { data, isLoading } = useKnowledgeBases();

if (isLoading) {
  return <KnowledgeBaseSkeleton />;
}

return <KnowledgeBaseList data={data} />;
```

#### 3.4.2 Loading 规范

**全局 Loading**：
```typescript
// App.tsx
import { Spin } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';

Spin.setDefaultIndicator(<LoadingOutlined style={{ fontSize: 24 }} spin />);
```

**局部 Loading**：
```typescript
<Button loading={isSubmitting}>
  提交
</Button>

<Spin spinning={isLoading}>
  <YourContent />
</Spin>
```

---

### 3.5 错误提示友好化（P0）

#### 3.5.1 错误码映射

```typescript
// utils/errorMessages.ts
export const ERROR_MESSAGES: Record<string, string> = {
  // 网络错误
  'ERR_NETWORK': '网络连接失败，请检查网络后重试',
  'ECONNABORTED': '请求超时，请稍后重试',
  'ERR_CANCELED': '请求已取消',
  
  // 业务错误
  'QUOTA_EXCEEDED': '配额已用完，请升级套餐',
  'RESOURCE_NOT_FOUND': '资源不存在',
  'INVALID_INPUT': '输入参数有误，请检查后重试',
  'UNAUTHORIZED': '登录已过期，请重新登录',
  'FORBIDDEN': '权限不足',
  
  // 默认
  'DEFAULT': '操作失败，请稍后重试',
};

export const getErrorMessage = (error: any): string => {
  // 1. 后端返回的错误码
  if (error.response?.data?.code) {
    return ERROR_MESSAGES[error.response.data.code] || error.response.data.message;
  }
  
  // 2. Axios 错误码
  if (error.code) {
    return ERROR_MESSAGES[error.code] || ERROR_MESSAGES.DEFAULT;
  }
  
  // 3. HTTP 状态码
  if (error.response?.status) {
    switch (error.response.status) {
      case 401: return ERROR_MESSAGES.UNAUTHORIZED;
      case 403: return ERROR_MESSAGES.FORBIDDEN;
      case 404: return ERROR_MESSAGES.RESOURCE_NOT_FOUND;
      case 429: return ERROR_MESSAGES.QUOTA_EXCEEDED;
      case 500: return '服务器内部错误，请联系管理员';
      case 503: return '服务暂不可用，请稍后重试';
      default: return ERROR_MESSAGES.DEFAULT;
    }
  }
  
  return error.message || ERROR_MESSAGES.DEFAULT;
};
```

#### 3.5.2 统一错误处理

```typescript
// utils/request.ts
import axios from 'axios';
import { message } from 'antd';
import { getErrorMessage } from './errorMessages';

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const errorMessage = getErrorMessage(error);
    message.error(errorMessage);
    
    return Promise.reject(error);
  }
);

export default request;
```

---

### 3.6 响应式布局（P1）

#### 3.6.1 Ant Design 栅格系统

```typescript
<Row gutter={[16, 16]}>
  <Col xs={24} sm={12} md={8} lg={6}>
    <KnowledgeBaseCard />
  </Col>
  {/* xs: 手机（< 576px）, sm: 平板（≥ 576px）, md: 桌面（≥ 768px） */}
</Row>
```

#### 3.6.2 媒体查询

```css
/* 桌面端（默认） */
.chat-window {
  max-width: 1200px;
  padding: 24px;
}

/* 平板端 */
@media (max-width: 768px) {
  .chat-window {
    padding: 16px;
  }
}

/* 手机端 */
@media (max-width: 576px) {
  .chat-window {
    padding: 12px;
  }
  
  .chat-input {
    font-size: 16px;  /* 防止 iOS 自动缩放 */
  }
}
```

---

## 4. 实施步骤

### Day 1：流式对话 + Markdown
- 上午：EventSource 断线重连（2h）
- 下午：Markdown 渲染 + 代码高亮（3h）

### Day 2：文件上传 + 错误处理
- 上午：拖拽上传 + 进度条（2h）
- 下午：错误提示友好化（2h）

### Day 3：加载状态 + 响应式
- 上午：骨架屏 + Loading 规范（2h）
- 下午：响应式布局（2h）

### Day 4：验收测试
- 全天：集成测试 + Bug 修复（6h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 移动端测试：iPhone/Android 浏览器正常显示
✅ 代码覆盖率 ≥ 70%（前端单元测试）

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06  
**修订说明**：EventSource → fetch SSE（支持认证 Header）；tus 断点续传降级为分片上传（P2）
