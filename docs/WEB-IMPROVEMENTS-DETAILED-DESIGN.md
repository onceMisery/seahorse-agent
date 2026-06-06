# Seahorse Agent Web 功能改进详细设计方案（借鉴 DeerFlow）

版本：v2.0 - 详细设计版  
日期：2026-06-02  
状态：Ready for Implementation

---

## 文档说明

本文档提供**可直接实施的详细设计方案**，包括：
- 完整的代码实现（前端 + 后端）
- 详细的文件结构和目录组织
- 数据流图和交互时序图
- 单元测试和集成测试用例
- 数据库变更脚本（如需要）
- 配置文件示例

**目标受众**：前端工程师、后端工程师  
**实施周期**：8 周（分 3 个 Phase）

---

## 目录

- [Phase 1: 快速见效项（Week 1-2）](#phase-1-快速见效项)
  - [1.1 模块化 AI 组件库重组](#11-模块化-ai-组件库重组)
  - [1.2 交互式代码编辑器集成](#12-交互式代码编辑器集成)
  - [1.3 骨架屏加载动画](#13-骨架屏加载动画)
- [Phase 2: 核心能力项（Week 3-6）](#phase-2-核心能力项)
  - [2.1 可视化工作流编排](#21-可视化工作流编排)
  - [2.2 流式渲染性能优化](#22-流式渲染性能优化)
  - [2.3 智能输入框增强](#23-智能输入框增强)
- [Phase 3: 高级特性项（Week 7-8）](#phase-3-高级特性项)
  - [3.1 多通道集成（可选）](#31-多通道集成)

---

## Phase 1: 快速见效项（Week 1-2）

### 1.1 模块化 AI 组件库重组

#### 设计目标

将分散在 `chat/`, `workbench/` 等目录的 AI 相关组件统一迁移到 `ai-elements/` 目录，建立清晰的组件职责边界。

#### 详细文件结构

```
frontend/src/components/
├── ai-elements/                          # 新建：AI 专用组件库
│   ├── index.ts                          # 统一导出
│   ├── types.ts                          # 类型定义
│   ├── hooks/                            # AI 组件专用 Hooks
│   │   ├── useMessageRenderer.ts         # 消息渲染逻辑
│   │   ├── useThinkingState.ts           # 思考状态管理
│   │   └── useStreamingText.ts           # 流式文本处理
│   ├── message/                          # 消息相关组件
│   │   ├── Message.tsx                   # 单条消息容器
│   │   ├── MessageHeader.tsx             # 消息头（头像、时间）
│   │   ├── MessageContent.tsx            # 消息内容解析
│   │   ├── MessageActions.tsx            # 消息操作按钮
│   │   └── MessageFooter.tsx             # 消息底部（反馈、源）
│   ├── thinking/                         # 思考过程相关
│   │   ├── ThinkingIndicator.tsx         # 思考状态指示器
│   │   ├── ChainOfThought.tsx            # 思考链可视化
│   │   └── ReasoningSteps.tsx            # 推理步骤展示
│   ├── artifact/                         # 工件相关
│   │   ├── ArtifactPanel.tsx             # 工件面板容器
│   │   ├── ArtifactHeader.tsx            # 工件头部
│   │   ├── ArtifactClose.tsx             # 关闭按钮
│   │   └── ArtifactContent.tsx           # 工件内容渲染
│   ├── source/                           # 源引用相关
│   │   ├── SourceList.tsx                # 源列表
│   │   ├── SourceItem.tsx                # 单个源引用
│   │   └── SourcePreview.tsx             # 源内容预览
│   ├── input/                            # 输入相关
│   │   ├── ChatInput.tsx                 # 智能输入框（重构）
│   │   ├── InputToolbar.tsx              # 输入工具栏
│   │   ├── FileUploadZone.tsx            # 文件上传区域
│   │   └── VoiceInput.tsx                # 语音输入（新增）
│   ├── feedback/                         # 反馈相关
│   │   ├── FeedbackButtons.tsx           # 反馈按钮
│   │   ├── FeedbackDialog.tsx            # 反馈对话框
│   │   └── RatingStars.tsx               # 评分星星
│   ├── renderer/                         # 渲染相关
│   │   ├── MarkdownRenderer.tsx          # Markdown 渲染器
│   │   ├── CodeBlock.tsx                 # 代码块（只读）
│   │   ├── CodeEditor.tsx                # 代码编辑器（新增）
│   │   └── MathRenderer.tsx              # 数学公式渲染
│   ├── loading/                          # 加载状态
│   │   ├── Shimmer.tsx                   # 骨架屏动画（新增）
│   │   ├── LoadingSpinner.tsx            # 加载旋转器
│   │   └── ProgressBar.tsx               # 进度条
│   └── workflow/                         # 工作流相关（新增）
│       ├── WorkflowCanvas.tsx            # 工作流画布
│       ├── WorkflowNode.tsx              # 工作流节点
│       └── WorkflowEdge.tsx              # 工作流连线
├── chat/                                 # 保留：聊天容器
│   ├── ChatBox.tsx                       # 聊天容器（简化）
│   ├── MessageList.tsx                   # 消息列表容器
│   └── ConversationHeader.tsx            # 对话头部
├── common/                               # 通用组件
└── layout/                               # 布局组件
```

#### 迁移计划表

| 原路径 | 新路径 | 改动说明 | 工作量 |
|--------|--------|---------|--------|
| `chat/MessageItem.tsx` | `ai-elements/message/Message.tsx` | 拆分为多个子组件 | 2h |
| `chat/ChatInput.tsx` | `ai-elements/input/ChatInput.tsx` | 重构并增强 | 4h |
| `workbench/ArtifactPanel.tsx` | `ai-elements/artifact/ArtifactPanel.tsx` | 拆分头部和内容 | 2h |
| `workbench/SourceList.tsx` | `ai-elements/source/SourceList.tsx` | 增加预览功能 | 1h |
| `common/MarkdownRenderer.tsx` | `ai-elements/renderer/MarkdownRenderer.tsx` | 无改动 | 0.5h |

#### 详细实现：统一导出文件

**文件**：`frontend/src/components/ai-elements/index.ts`

```typescript
// Message 相关
export { Message } from './message/Message';
export { MessageHeader } from './message/MessageHeader';
export { MessageContent } from './message/MessageContent';
export { MessageActions } from './message/MessageActions';
export { MessageFooter } from './message/MessageFooter';

// Thinking 相关
export { ThinkingIndicator } from './thinking/ThinkingIndicator';
export { ChainOfThought } from './thinking/ChainOfThought';
export { ReasoningSteps } from './thinking/ReasoningSteps';

// Artifact 相关
export { ArtifactPanel } from './artifact/ArtifactPanel';
export { ArtifactHeader } from './artifact/ArtifactHeader';
export { ArtifactClose } from './artifact/ArtifactClose';
export { ArtifactContent } from './artifact/ArtifactContent';

// Source 相关
export { SourceList } from './source/SourceList';
export { SourceItem } from './source/SourceItem';
export { SourcePreview } from './source/SourcePreview';

// Input 相关
export { ChatInput } from './input/ChatInput';
export { InputToolbar } from './input/InputToolbar';
export { FileUploadZone } from './input/FileUploadZone';
export { VoiceInput } from './input/VoiceInput';

// Feedback 相关
export { FeedbackButtons } from './feedback/FeedbackButtons';
export { FeedbackDialog } from './feedback/FeedbackDialog';
export { RatingStars } from './feedback/RatingStars';

// Renderer 相关
export { MarkdownRenderer } from './renderer/MarkdownRenderer';
export { CodeBlock } from './renderer/CodeBlock';
export { CodeEditor } from './renderer/CodeEditor';
export { MathRenderer } from './renderer/MathRenderer';

// Loading 相关
export { Shimmer } from './loading/Shimmer';
export { LoadingSpinner } from './loading/LoadingSpinner';
export { ProgressBar } from './loading/ProgressBar';

// Workflow 相关
export { WorkflowCanvas } from './workflow/WorkflowCanvas';
export { WorkflowNode } from './workflow/WorkflowNode';
export { WorkflowEdge } from './workflow/WorkflowEdge';

// Types
export type * from './types';
```

#### 详细实现：类型定义文件

**文件**：`frontend/src/components/ai-elements/types.ts`

```typescript
// 消息类型
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
  feedback?: Feedback;
}

// 思考过程
export interface ThinkingProcess {
  steps: ThinkingStep[];
  totalDuration?: number;
}

export interface ThinkingStep {
  id: string;
  type: 'reasoning' | 'tool_call' | 'retrieval';
  content: string;
  duration?: number;
}

// 源引用
export interface Source {
  id: string;
  title: string;
  url?: string;
  snippet: string;
  relevanceScore?: number;
}

// 工件
export interface Artifact {
  id: string;
  type: 'code' | 'document' | 'image' | 'data';
  title: string;
  content: string;
  language?: string;
  metadata?: Record<string, unknown>;
}

// 反馈
export interface Feedback {
  rating?: number;
  comment?: string;
  helpful?: boolean;
}

// 工作流
export interface WorkflowNode {
  id: string;
  type: 'start' | 'tool' | 'llm' | 'end';
  position: { x: number; y: number };
  data: {
    label: string;
    status?: 'pending' | 'running' | 'success' | 'error';
    duration?: number;
  };
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
}
```

#### 详细实现：Message 组件重构

**文件**：`frontend/src/components/ai-elements/message/Message.tsx`

```typescript
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { MessageHeader } from './MessageHeader';
import { MessageContent } from './MessageContent';
import { MessageFooter } from './MessageFooter';
import type { Message as MessageType } from '../types';

interface MessageProps {
  message: MessageType;
  onFeedback?: (feedback: { helpful: boolean }) => void;
  onCopy?: () => void;
  onRegenerate?: () => void;
  className?: string;
}

export function Message({
  message,
  onFeedback,
  onCopy,
  onRegenerate,
  className
}: MessageProps) {
  const isUser = message.role === 'user';

  return (
    <div
      className={cn(
        'group relative flex gap-3 px-4 py-3',
        isUser ? 'bg-muted/50' : 'bg-background',
        className
      )}
    >
      {/* 消息头部：头像 + 角色名 + 时间 */}
      <MessageHeader
        role={message.role}
        timestamp={message.timestamp}
      />

      <div className="flex-1 space-y-2">
        {/* 消息内容：支持 Markdown、代码块、工件 */}
        <MessageContent
          content={message.content}
          metadata={message.metadata}
        />

        {/* 消息底部：反馈按钮、源列表、操作按钮 */}
        {!isUser && (
          <MessageFooter
            message={message}
            onFeedback={onFeedback}
            onCopy={onCopy}
            onRegenerate={onRegenerate}
          />
        )}
      </div>
    </div>
  );
}
```

**文件**：`frontend/src/components/ai-elements/message/MessageHeader.tsx`

```typescript
'use client';

import React from 'react';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale';

interface MessageHeaderProps {
  role: 'user' | 'assistant' | 'system';
  timestamp: number;
}

export function MessageHeader({ role, timestamp }: MessageHeaderProps) {
  const avatarSrc = role === 'user' 
    ? '/avatars/user.png' 
    : '/avatars/assistant.png';
  
  const displayName = role === 'user' ? '你' : 'AI 助手';

  return (
    <div className="flex items-start gap-2 shrink-0">
      <Avatar className="h-8 w-8">
        <AvatarImage src={avatarSrc} />
        <AvatarFallback>{displayName[0]}</AvatarFallback>
      </Avatar>
      
      <div className="flex flex-col">
        <span className="text-sm font-medium">{displayName}</span>
        <span className="text-xs text-muted-foreground">
          {formatDistanceToNow(timestamp, { 
            addSuffix: true, 
            locale: zhCN 
          })}
        </span>
      </div>
    </div>
  );
}
```

**文件**：`frontend/src/components/ai-elements/message/MessageContent.tsx`

```typescript
'use client';

import React from 'react';
import { MarkdownRenderer } from '../renderer/MarkdownRenderer';
import { ThinkingIndicator } from '../thinking/ThinkingIndicator';
import { ArtifactPanel } from '../artifact/ArtifactPanel';
import { SourceList } from '../source/SourceList';
import type { MessageMetadata } from '../types';

interface MessageContentProps {
  content: string;
  metadata?: MessageMetadata;
}

export function MessageContent({ content, metadata }: MessageContentProps) {
  return (
    <div className="space-y-3">
      {/* 思考过程展示 */}
      {metadata?.thinking && (
        <ThinkingIndicator thinking={metadata.thinking} />
      )}

      {/* 主要内容：Markdown 渲染 */}
      <MarkdownRenderer content={content} />

      {/* 源引用列表 */}
      {metadata?.sources && metadata.sources.length > 0 && (
        <SourceList sources={metadata.sources} />
      )}

      {/* 工件面板 */}
      {metadata?.artifacts && metadata.artifacts.length > 0 && (
        <div className="space-y-2">
          {metadata.artifacts.map((artifact) => (
            <ArtifactPanel key={artifact.id} artifact={artifact} />
          ))}
        </div>
      )}
    </div>
  );
}
```

#### 迁移步骤（详细命令）

```bash
# Step 1: 创建新目录结构
cd frontend/src/components
mkdir -p ai-elements/{message,thinking,artifact,source,input,feedback,renderer,loading,workflow,hooks}

# Step 2: 创建基础文件
touch ai-elements/index.ts
touch ai-elements/types.ts

# Step 3: 迁移现有组件（示例）
# 注意：需要手动调整导入路径
git mv chat/MessageItem.tsx ai-elements/message/Message.tsx
git mv workbench/ArtifactPanel.tsx ai-elements/artifact/ArtifactPanel.tsx
git mv workbench/SourceList.tsx ai-elements/source/SourceList.tsx

# Step 4: 更新所有引用（使用 sed 或手动）
# 将 'from "@/components/chat/MessageItem"' 替换为 'from "@/components/ai-elements"'
find . -type f -name "*.tsx" -exec sed -i 's|@/components/chat/MessageItem|@/components/ai-elements|g' {} +

# Step 5: 验证编译
npm run build
```

#### 测试用例

**文件**：`frontend/src/components/ai-elements/__tests__/Message.test.tsx`

```typescript
import { render, screen } from '@testing-library/react';
import { Message } from '../message/Message';
import type { Message as MessageType } from '../types';

describe('Message Component', () => {
  const mockMessage: MessageType = {
    id: '1',
    role: 'assistant',
    content: '你好，我是 AI 助手',
    timestamp: Date.now(),
    metadata: {
      sources: [
        {
          id: 's1',
          title: '知识库 A',
          snippet: '相关内容片段'
        }
      ]
    }
  };

  it('should render message content', () => {
    render(<Message message={mockMessage} />);
    expect(screen.getByText('你好，我是 AI 助手')).toBeInTheDocument();
  });

  it('should display sources when provided', () => {
    render(<Message message={mockMessage} />);
    expect(screen.getByText('知识库 A')).toBeInTheDocument();
  });

  it('should call onFeedback when feedback button clicked', () => {
    const onFeedback = jest.fn();
    render(<Message message={mockMessage} onFeedback={onFeedback} />);
    
    const thumbUpButton = screen.getByLabelText('有帮助');
    thumbUpButton.click();
    
    expect(onFeedback).toHaveBeenCalledWith({ helpful: true });
  });
});
```

#### 验收标准

- [ ] 所有 AI 相关组件已迁移到 `ai-elements/` 目录
- [ ] 提供统一的 `index.ts` 导出接口
- [ ] 所有组件有完整的 TypeScript 类型定义
- [ ] 现有功能无回归（通过所有测试）
- [ ] 代码覆盖率 > 80%
- [ ] 新组件命名统一，易于查找

### 1.2 交互式代码编辑器集成

#### 设计目标

集成 CodeMirror 6，支持在 Web UI 中直接编辑代码（Python、JavaScript、JSON 等），替换当前的只读代码高亮。

#### 技术选型

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| **CodeMirror 6** | 轻量、性能好、扩展性强 | 学习曲线中等 | ✅ 推荐 |
| Monaco Editor | 功能强大（VS Code 同款） | 体积大（~4MB） | ❌ 不推荐 |
| Ace Editor | 成熟稳定 | API 较旧 | ❌ 不推荐 |

**选择 CodeMirror 6 的理由**：
- 体积小（~200KB gzipped）
- Tree-sitter 语法高亮（精确）
- 移动端友好
- TypeScript 原生支持

#### 依赖安装

```bash
cd frontend

# 核心库
npm install @uiw/react-codemirror@4.25.4

# 语言支持
npm install @codemirror/lang-javascript@6.2.4 \
  @codemirror/lang-python@6.2.1 \
  @codemirror/lang-json@6.0.2 \
  @codemirror/lang-html@6.5.0 \
  @codemirror/lang-css@6.3.2 \
  @codemirror/lang-markdown@6.3.2

# 主题
npm install @uiw/codemirror-theme-monokai@4.25.4 \
  @uiw/codemirror-theme-github@4.25.4

# 扩展功能
npm install @codemirror/view@6.36.2 \
  @codemirror/commands@6.8.2 \
  @codemirror/search@6.5.9 \
  @codemirror/autocomplete@6.20.3
```

#### 详细实现：代码编辑器组件

**文件**：`frontend/src/components/ai-elements/renderer/CodeEditor.tsx`

```typescript
'use client';

import React, { useMemo, useCallback } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { javascript } from '@codemirror/lang-javascript';
import { python } from '@codemirror/lang-python';
import { json } from '@codemirror/lang-json';
import { html } from '@codemirror/lang-html';
import { css } from '@codemirror/lang-css';
import { markdown } from '@codemirror/lang-markdown';
import { monokaiInit } from '@uiw/codemirror-theme-monokai';
import { githubLightInit } from '@uiw/codemirror-theme-github';
import { useTheme } from 'next-themes';
import { 
  lineNumbers, 
  highlightActiveLineGutter, 
  highlightSpecialChars, 
  drawSelection, 
  highlightActiveLine 
} from '@codemirror/view';
import { 
  defaultHighlightStyle, 
  syntaxHighlighting, 
  bracketMatching 
} from '@codemirror/language';
import { 
  defaultKeymap, 
  history, 
  historyKeymap 
} from '@codemirror/commands';
import { searchKeymap, highlightSelectionMatches } from '@codemirror/search';
import { autocompletion, completionKeymap } from '@codemirror/autocomplete';
import { keymap } from '@codemirror/view';
import { cn } from '@/lib/utils';

export type SupportedLanguage = 
  | 'javascript' 
  | 'typescript' 
  | 'python' 
  | 'json' 
  | 'html' 
  | 'css' 
  | 'markdown';

interface CodeEditorProps {
  value: string;
  onChange?: (value: string) => void;
  language?: SupportedLanguage;
  readonly?: boolean;
  height?: string;
  minHeight?: string;
  maxHeight?: string;
  className?: string;
  placeholder?: string;
  lineWrapping?: boolean;
  showLineNumbers?: boolean;
}

export function CodeEditor({
  value,
  onChange,
  language = 'python',
  readonly = false,
  height,
  minHeight = '100px',
  maxHeight = '600px',
  className,
  placeholder = '在此输入代码...',
  lineWrapping = true,
  showLineNumbers = true
}: CodeEditorProps) {
  const { resolvedTheme } = useTheme();

  // 语言扩展
  const languageExtension = useMemo(() => {
    switch (language) {
      case 'javascript':
      case 'typescript':
        return javascript({ typescript: language === 'typescript' });
      case 'python':
        return python();
      case 'json':
        return json();
      case 'html':
        return html();
      case 'css':
        return css();
      case 'markdown':
        return markdown();
      default:
        return python();
    }
  }, [language]);

  // 编辑器主题
  const theme = useMemo(() => {
    const isDark = resolvedTheme === 'dark';
    return isDark 
      ? monokaiInit({ 
          settings: { 
            background: 'transparent',
            gutterBackground: 'transparent'
          } 
        })
      : githubLightInit({
          settings: {
            background: 'transparent',
            gutterBackground: 'transparent'
          }
        });
  }, [resolvedTheme]);

  // 基础扩展
  const basicExtensions = useMemo(() => {
    const extensions = [
      languageExtension,
      syntaxHighlighting(defaultHighlightStyle),
      bracketMatching(),
      highlightSelectionMatches(),
      highlightSpecialChars(),
      history(),
      drawSelection(),
      keymap.of([
        ...defaultKeymap,
        ...historyKeymap,
        ...searchKeymap,
        ...completionKeymap
      ])
    ];

    if (showLineNumbers) {
      extensions.push(lineNumbers(), highlightActiveLineGutter());
    }

    if (!readonly) {
      extensions.push(
        highlightActiveLine(),
        autocompletion({
          activateOnTyping: true,
          override: []
        })
      );
    }

    return extensions;
  }, [languageExtension, readonly, showLineNumbers]);

  // 处理值变化
  const handleChange = useCallback((value: string) => {
    onChange?.(value);
  }, [onChange]);

  return (
    <div className={cn('border rounded-md overflow-hidden', className)}>
      <CodeMirror
        value={value}
        onChange={handleChange}
        theme={theme}
        extensions={basicExtensions}
        editable={!readonly}
        readOnly={readonly}
        placeholder={placeholder}
        height={height}
        minHeight={minHeight}
        maxHeight={maxHeight}
        basicSetup={{
          lineNumbers: showLineNumbers,
          highlightActiveLineGutter: showLineNumbers,
          highlightSpecialChars: true,
          history: true,
          foldGutter: true,
          drawSelection: true,
          dropCursor: true,
          allowMultipleSelections: true,
          indentOnInput: true,
          syntaxHighlighting: true,
          bracketMatching: true,
          closeBrackets: !readonly,
          autocompletion: !readonly,
          rectangularSelection: true,
          crosshairCursor: true,
          highlightActiveLine: !readonly,
          highlightSelectionMatches: true,
          closeBracketsKeymap: !readonly,
          searchKeymap: true,
          foldKeymap: true,
          completionKeymap: !readonly,
          lintKeymap: true
        }}
      />
    </div>
  );
}
```

#### 详细实现：在 ArtifactPanel 中集成

**文件**：`frontend/src/components/ai-elements/artifact/ArtifactContent.tsx`

```typescript
'use client';

import React, { useState } from 'react';
import { CodeEditor } from '../renderer/CodeEditor';
import { CodeBlock } from '../renderer/CodeBlock';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Copy, Edit, Eye, Save, X } from 'lucide-react';
import { toast } from 'sonner';
import type { Artifact } from '../types';

interface ArtifactContentProps {
  artifact: Artifact;
  onUpdate?: (updatedArtifact: Artifact) => void;
}

export function ArtifactContent({ artifact, onUpdate }: ArtifactContentProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editedContent, setEditedContent] = useState(artifact.content);
  const [activeTab, setActiveTab] = useState<'preview' | 'code'>('preview');

  // 保存编辑
  const handleSave = () => {
    onUpdate?.({
      ...artifact,
      content: editedContent
    });
    setIsEditing(false);
    toast.success('工件已保存');
  };

  // 取消编辑
  const handleCancel = () => {
    setEditedContent(artifact.content);
    setIsEditing(false);
  };

  // 复制代码
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(artifact.content);
      toast.success('已复制到剪贴板');
    } catch (error) {
      toast.error('复制失败');
    }
  };

  // 如果是代码类型
  if (artifact.type === 'code') {
    return (
      <div className="space-y-2">
        {/* 操作栏 */}
        <div className="flex items-center justify-between border-b pb-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">
              {artifact.language?.toUpperCase() || 'CODE'}
            </span>
            {artifact.title && (
              <span className="text-sm text-muted-foreground">
                {artifact.title}
              </span>
            )}
          </div>

          <div className="flex items-center gap-1">
            {isEditing ? (
              <>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={handleSave}
                >
                  <Save className="h-4 w-4 mr-1" />
                  保存
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={handleCancel}
                >
                  <X className="h-4 w-4 mr-1" />
                  取消
                </Button>
              </>
            ) : (
              <>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setIsEditing(true)}
                >
                  <Edit className="h-4 w-4 mr-1" />
                  编辑
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={handleCopy}
                >
                  <Copy className="h-4 w-4 mr-1" />
                  复制
                </Button>
              </>
            )}
          </div>
        </div>

        {/* 编辑器 / 预览切换 */}
        {isEditing ? (
          <CodeEditor
            value={editedContent}
            onChange={setEditedContent}
            language={artifact.language as any}
            readonly={false}
            height="400px"
          />
        ) : (
          <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)}>
            <TabsList>
              <TabsTrigger value="preview">
                <Eye className="h-4 w-4 mr-1" />
                预览
              </TabsTrigger>
              <TabsTrigger value="code">
                <Edit className="h-4 w-4 mr-1" />
                代码
              </TabsTrigger>
            </TabsList>

            <TabsContent value="preview">
              {/* 渲染预览（如果支持） */}
              {artifact.language === 'html' ? (
                <iframe
                  srcDoc={artifact.content}
                  className="w-full h-96 border rounded"
                  sandbox="allow-scripts"
                />
              ) : (
                <CodeBlock
                  code={artifact.content}
                  language={artifact.language || 'text'}
                />
              )}
            </TabsContent>

            <TabsContent value="code">
              <CodeEditor
                value={artifact.content}
                language={artifact.language as any}
                readonly={true}
                height="400px"
              />
            </TabsContent>
          </Tabs>
        )}
      </div>
    );
  }

  // 其他类型的工件（文档、图片等）
  return (
    <div className="prose prose-sm max-w-none">
      {artifact.content}
    </div>
  );
}
```

#### 后端支持：工件更新 API

**文件**：`seahorse-agent-adapter-web/src/main/java/com/seahorse/adapter/web/controller/ArtifactController.java`

```java
package com.seahorse.adapter.web.controller;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;

    /**
     * 更新工件内容
     */
    @PutMapping("/{artifactId}")
    public ArtifactUpdateResponse updateArtifact(
        @PathVariable String artifactId,
        @RequestBody ArtifactUpdateRequest request
    ) {
        Artifact updated = artifactService.updateContent(
            artifactId,
            request.getContent()
        );
        return new ArtifactUpdateResponse(updated);
    }

    /**
     * 获取工件详情
     */
    @GetMapping("/{artifactId}")
    public ArtifactDetailResponse getArtifact(@PathVariable String artifactId) {
        Artifact artifact = artifactService.findById(artifactId);
        return new ArtifactDetailResponse(artifact);
    }
}
```

#### 数据流图

```
用户点击"编辑" 
    ↓
启用 CodeEditor（readonly=false）
    ↓
用户修改代码
    ↓
点击"保存"
    ↓
调用 onUpdate 回调
    ↓
前端发送 PUT /api/artifacts/{id}
    ↓
后端更新数据库
    ↓
返回更新后的工件
    ↓
前端更新本地状态
    ↓
Toast 提示"已保存"
```

#### 测试用例

**文件**：`frontend/src/components/ai-elements/__tests__/CodeEditor.test.tsx`

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CodeEditor } from '../renderer/CodeEditor';
import userEvent from '@testing-library/user-event';

describe('CodeEditor Component', () => {
  it('should render with initial value', () => {
    const code = 'print("Hello, World!")';
    render(<CodeEditor value={code} language="python" />);
    
    expect(screen.getByText(/Hello, World!/)).toBeInTheDocument();
  });

  it('should call onChange when content changes', async () => {
    const onChange = jest.fn();
    render(
      <CodeEditor 
        value="" 
        onChange={onChange} 
        language="python" 
        readonly={false}
      />
    );

    const editor = screen.getByRole('textbox');
    await userEvent.type(editor, 'x = 1');

    await waitFor(() => {
      expect(onChange).toHaveBeenCalled();
      expect(onChange).toHaveBeenCalledWith(expect.stringContaining('x = 1'));
    });
  });

  it('should be readonly when readonly prop is true', () => {
    render(
      <CodeEditor 
        value="const x = 1;" 
        language="javascript" 
        readonly={true}
      />
    );

    const editor = screen.getByRole('textbox');
    expect(editor).toHaveAttribute('contenteditable', 'false');
  });

  it('should support Python syntax highlighting', () => {
    const code = 'def hello():\n    print("world")';
    render(<CodeEditor value={code} language="python" />);

    // 验证关键字高亮（通过 class 检查）
    const defKeyword = screen.getByText('def');
    expect(defKeyword).toHaveClass('cm-keyword');
  });
});
```

#### 性能优化建议

1. **代码分割**：
```typescript
// 使用动态导入减少初始加载
const CodeEditor = dynamic(
  () => import('@/components/ai-elements/renderer/CodeEditor'),
  { 
    ssr: false,
    loading: () => <LoadingSpinner />
  }
);
```

2. **防抖优化**：
```typescript
import { useDebouncedCallback } from 'use-debounce';

const debouncedOnChange = useDebouncedCallback(
  (value: string) => {
    onUpdate(value);
  },
  500 // 500ms 防抖
);
```

#### 验收标准

- [ ] 支持 Python、JavaScript、JSON、HTML、CSS、Markdown 六种语言
- [ ] 代码编辑实时保存（500ms 防抖）
- [ ] 暗黑模式自动切换
- [ ] 语法高亮准确
- [ ] 支持代码折叠、搜索、自动补全
- [ ] 性能正常（编辑器初始化 < 500ms）
- [ ] 移动端可用（虚拟键盘不遮挡）

**工作量**：1 人 × 2 天

---

// __CONTINUE_HERE__