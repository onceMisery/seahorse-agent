# Web 功能改进实施手册（精简版）

版本：v1.0  
日期：2026-06-02  
目标：提供可直接执行的实施步骤

---

## 快速导航

- [改进 1：模块化 AI 组件库](#改进-1模块化-ai-组件库)
- [改进 2：交互式代码编辑器](#改进-2交互式代码编辑器)
- [改进 3：骨架屏动画](#改进-3骨架屏动画)
- [改进 4：可视化工作流](#改进-4可视化工作流)
- [改进 5：流式渲染优化](#改进-5流式渲染优化)
- [改进 6：智能输入框](#改进-6智能输入框)

---

## 改进 1：模块化 AI 组件库

### 执行命令

```bash
cd frontend/src/components

# 1. 创建目录结构
mkdir -p ai-elements/{message,thinking,artifact,source,input,feedback,renderer,loading,workflow}

# 2. 创建核心文件
cat > ai-elements/index.ts << 'EOF'
// Message
export { Message } from './message/Message';
export { MessageHeader } from './message/MessageHeader';
export { MessageContent } from './message/MessageContent';

// Thinking
export { ThinkingIndicator } from './thinking/ThinkingIndicator';

// Artifact
export { ArtifactPanel } from './artifact/ArtifactPanel';

// Source
export { SourceList } from './source/SourceList';

// Input
export { ChatInput } from './input/ChatInput';

// Feedback
export { FeedbackButtons } from './feedback/FeedbackButtons';

// Renderer
export { MarkdownRenderer } from './renderer/MarkdownRenderer';
export { CodeBlock } from './renderer/CodeBlock';
export { CodeEditor } from './renderer/CodeEditor';

// Loading
export { Shimmer } from './loading/Shimmer';

// Workflow
export { WorkflowCanvas } from './workflow/WorkflowCanvas';

export type * from './types';
EOF

# 3. 创建类型定义
cat > ai-elements/types.ts << 'EOF'
export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  metadata?: MessageMetadata;
}

export interface MessageMetadata {
  thinking?: ThinkingProcess;
  sources?: Source[];
  artifacts?: Artifact[];
}

export interface ThinkingProcess {
  steps: Array<{ content: string; duration?: number }>;
}

export interface Source {
  id: string;
  title: string;
  url?: string;
  snippet: string;
}

export interface Artifact {
  id: string;
  type: 'code' | 'document';
  title: string;
  content: string;
  language?: string;
}
EOF
```

### 核心组件实现（Message.tsx）

```bash
cat > ai-elements/message/Message.tsx << 'EOF'
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import type { Message as MessageType } from '../types';

interface MessageProps {
  message: MessageType;
  className?: string;
}

export function Message({ message, className }: MessageProps) {
  const isUser = message.role === 'user';

  return (
    <div className={cn(
      'flex gap-3 px-4 py-3',
      isUser ? 'bg-muted/50' : 'bg-background',
      className
    )}>
      <div className="flex-1">
        <div className="prose prose-sm max-w-none">
          {message.content}
        </div>
      </div>
    </div>
  );
}
EOF
```

---

## 改进 2：交互式代码编辑器

### 安装依赖

```bash
cd frontend

npm install \
  @uiw/react-codemirror@4.25.4 \
  @codemirror/lang-javascript@6.2.4 \
  @codemirror/lang-python@6.2.1 \
  @codemirror/lang-json@6.0.2 \
  @uiw/codemirror-theme-monokai@4.25.4
```

### 核心组件实现

```bash
cat > components/ai-elements/renderer/CodeEditor.tsx << 'EOF'
'use client';

import React, { useMemo } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { json } from '@codemirror/lang-json';
import { monokaiInit } from '@uiw/codemirror-theme-monokai';
import { useTheme } from 'next-themes';

type Language = 'javascript' | 'python' | 'json';

interface CodeEditorProps {
  value: string;
  onChange?: (value: string) => void;
  language?: Language;
  readonly?: boolean;
  height?: string;
}

export function CodeEditor({
  value,
  onChange,
  language = 'python',
  readonly = false,
  height = '300px'
}: CodeEditorProps) {
  const { resolvedTheme } = useTheme();

  const extensions = useMemo(() => {
    switch (language) {
      case 'javascript':
        return [javascript()];
      case 'python':
        return [python()];
      case 'json':
        return [json()];
      default:
        return [];
    }
  }, [language]);

  const theme = useMemo(() => {
    return resolvedTheme === 'dark' 
      ? monokaiInit({ settings: { background: 'transparent' } }) 
      : undefined;
  }, [resolvedTheme]);

  return (
    <CodeMirror
      value={value}
      onChange={onChange}
      extensions={extensions}
      theme={theme}
      readOnly={readonly}
      height={height}
    />
  );
}
EOF
```

### 集成到 ArtifactPanel

```typescript
// 在 ArtifactPanel 中使用
import { CodeEditor } from '../renderer/CodeEditor';

<CodeEditor
  value={artifact.content}
  onChange={(newContent) => onUpdate({ ...artifact, content: newContent })}
  language={artifact.language}
  readonly={false}
/>
```

---

## 改进 3：骨架屏动画

### 组件实现

```bash
cat > components/ai-elements/loading/Shimmer.tsx << 'EOF'
'use client';

import React from 'react';
import { cn } from '@/lib/utils';

interface ShimmerProps {
  className?: string;
  lines?: number;
}

export function Shimmer({ className, lines = 3 }: ShimmerProps) {
  return (
    <div className={cn('space-y-2', className)}>
      {Array.from({ length: lines }).map((_, i) => (
        <div
          key={i}
          className="h-4 bg-muted rounded animate-pulse"
          style={{ width: `${100 - i * 10}%` }}
        />
      ))}
    </div>
  );
}
EOF
```

### CSS 动画（globals.css）

```css
@keyframes shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

.animate-shimmer {
  animation: shimmer 1.5s infinite;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.1) 50%,
    transparent 100%
  );
  background-size: 200% 100%;
}
```

### 使用示例

```typescript
// 替换 Loading Spinner
{isLoading ? (
  <Shimmer lines={3} />
) : (
  <MessageContent content={message.content} />
)}
```

---

## 改进 4：可视化工作流

### 安装依赖

```bash
npm install @xyflow/react@12.10.0
```

### 组件实现

```bash
cat > components/ai-elements/workflow/WorkflowCanvas.tsx << 'EOF'
'use client';

import React from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Node,
  type Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

interface WorkflowCanvasProps {
  nodes: Node[];
  edges: Edge[];
}

export function WorkflowCanvas({ nodes, edges }: WorkflowCanvasProps) {
  return (
    <div className="h-[600px] w-full border rounded-lg">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        fitView
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
}
EOF
```

### 数据转换（Agent Run → 节点）

```typescript
// 转换 Agent Run 执行步骤为节点
function convertRunToNodes(runSteps: AgentRunStep[]): { nodes: Node[], edges: Edge[] } {
  const nodes = runSteps.map((step, index) => ({
    id: step.id,
    position: { x: index * 250, y: 100 },
    data: { 
      label: step.name,
      status: step.status 
    },
    type: 'default'
  }));

  const edges = runSteps.slice(0, -1).map((step, index) => ({
    id: `e${index}`,
    source: step.id,
    target: runSteps[index + 1].id,
    animated: true
  }));

  return { nodes, edges };
}

// 使用
const { nodes, edges } = convertRunToNodes(agentRun.steps);
<WorkflowCanvas nodes={nodes} edges={edges} />
```

---

## 改进 5：流式渲染优化

### 安装依赖

```bash
npm install react-virtuoso@5.0.3
```

### 实现渲染缓冲器

```bash
cat > lib/renderBuffer.ts << 'EOF'
export class RenderBuffer<T> {
  private buffer: T[] = [];
  private flushTimer: NodeJS.Timeout | null = null;
  private flushInterval: number;

  constructor(interval = 50) {
    this.flushInterval = interval;
  }

  add(item: T): void {
    this.buffer.push(item);
    this.scheduleFlush();
  }

  private scheduleFlush(): void {
    if (this.flushTimer) return;
    
    this.flushTimer = setTimeout(() => {
      this.flushTimer = null;
      if (this.buffer.length > 0) {
        this.onFlush?.(this.buffer.splice(0));
      }
    }, this.flushInterval);
  }

  onFlush?: (items: T[]) => void;
}
EOF
```

### 在 chatStore 中使用

```typescript
// store/chatStore.ts
import { RenderBuffer } from '@/lib/renderBuffer';

const buffer = new RenderBuffer<StreamEvent>(50);
buffer.onFlush = (events) => {
  set((draft) => {
    events.forEach(event => processEvent(draft, event));
  });
};

// SSE 事件处理
eventSource.addEventListener('message', (event) => {
  buffer.add(parseEvent(event.data));
});
```

### 虚拟滚动

```typescript
import { Virtuoso } from 'react-virtuoso';

export function MessageList({ messages }: { messages: Message[] }) {
  return (
    <Virtuoso
      data={messages}
      itemContent={(index, message) => (
        <Message key={message.id} message={message} />
      )}
      style={{ height: 'calc(100vh - 200px)' }}
      followOutput="smooth"
    />
  );
}
```

---

## 改进 6：智能输入框

### 拖拽上传

```typescript
const handleDrop = (e: React.DragEvent) => {
  e.preventDefault();
  const files = Array.from(e.dataTransfer.files);
  uploadFiles(files);
};

return (
  <div
    onDrop={handleDrop}
    onDragOver={(e) => e.preventDefault()}
    className="relative"
  >
    <textarea {...props} />
  </div>
);
```

### 语音输入

```typescript
const startVoiceInput = () => {
  if (!('webkitSpeechRecognition' in window)) {
    toast.error('浏览器不支持语音输入');
    return;
  }

  const recognition = new webkitSpeechRecognition();
  recognition.lang = 'zh-CN';
  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    setInput(prev => prev + transcript);
  };
  recognition.start();
};
```

---

## 实施优先级

| 改进项 | 优先级 | 工作量 | 依赖 |
|-------|--------|--------|------|
| 1. 组件库重组 | P0 | 3天 | 无 |
| 2. 代码编辑器 | P0 | 2天 | 无 |
| 3. 骨架屏 | P1 | 1天 | 无 |
| 4. 工作流可视化 | P0 | 5天 | 后端 API |
| 5. 流式优化 | P0 | 3天 | 无 |
| 6. 智能输入框 | P1 | 4天 | 无 |

**总计**：约 18 人天（2-3 周）

---

## 验收 Checklist

### Phase 1（Week 1）
- [ ] AI 组件库目录结构已创建
- [ ] 核心组件已迁移（Message、Artifact、Source）
- [ ] CodeEditor 集成完成，支持 3+ 种语言
- [ ] 骨架屏在所有加载场景替换 Spinner

### Phase 2（Week 2-3）
- [ ] WorkflowCanvas 集成完成
- [ ] Agent Run 可视化展示
- [ ] 流式渲染缓冲器实现
- [ ] 虚拟滚动在消息列表启用
- [ ] 1000+ 消息滚动流畅

### Phase 3（Week 3-4）
- [ ] 拖拽上传文件正常
- [ ] 语音输入在 Chrome 可用
- [ ] Markdown 预览实时
- [ ] 所有测试通过
- [ ] 代码覆盖率 > 70%

---

## 常见问题

**Q: CodeMirror 初始化慢怎么办？**  
A: 使用动态导入 `dynamic(() => import('./CodeEditor'), { ssr: false })`

**Q: 虚拟滚动后定位到最新消息失败？**  
A: 使用 `followOutput="smooth"` 属性

**Q: 语音输入在 Safari 不工作？**  
A: Safari 不支持 webkitSpeechRecognition，隐藏该功能

**Q: ReactFlow 节点布局混乱？**  
A: 使用自动布局库 `dagre` 或 `elkjs`

---

**完成日期目标**：2026-07-30（8 周后）

