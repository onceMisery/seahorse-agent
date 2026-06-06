# Seahorse Agent 工作流可视化详细设计方案

版本：v1.0  
日期：2026-06-02  
作者：技术团队

---

## 目录

1. [概述](#概述)
2. [核心场景](#核心场景)
3. [技术架构](#技术架构)
4. [数据模型设计](#数据模型设计)
5. [UI 组件设计](#ui-组件设计)
6. [实施步骤](#实施步骤)
7. [代码示例](#代码示例)
8. [测试方案](#测试方案)
9. [性能优化](#性能优化)
10. [常见问题](#常见问题)

---

## 概述

### 问题定义

当前 Seahorse Agent 在处理复杂任务时，用户无法直观理解：
- ✗ Agent 正在执行哪些步骤
- ✗ 各步骤之间的依赖关系
- ✗ 当前执行到哪一步
- ✗ 哪些步骤成功/失败/跳过

**用户痛点**：
- "我不知道 AI 在做什么，只能干等"
- "任务失败了，但不知道是哪一步出错"
- "比较分析结果很好，但不知道 AI 是怎么得出的"

### 解决方案

通过 **ReactFlow 可视化工作流**，实现：
- ✓ 实时展示任务执行流程
- ✓ 高亮当前执行节点
- ✓ 显示节点状态（执行中/成功/失败）
- ✓ 支持点击节点查看详情
- ✓ 支持导出为图片分享

---

## 核心场景

### 场景 1：比较分析任务

**任务**："比较 React 和 Vue 的优缺点"

**工作流可视化**：

```
开始任务
    │
    ▼
搜索 React 资料 (执行中 ⏳)
    │
    ├──────────────┐
    │              │
    ▼              ▼
提取特性      提取优缺点
    │              │
    └──────┬───────┘
           │
           ▼
    搜索 Vue 资料
           │
           ▼
       对比分析
           │
           ▼
       生成报告
```

**节点说明**：
- **开始任务** - 初始化，解析用户意图
- **搜索 React 资料** - 从知识库和网络搜索 React 相关内容
- **提取特性/优缺点** - 并行提取两类信息
- **搜索 Vue 资料** - 同样的流程处理 Vue
- **对比分析** - 将两者信息进行对比
- **生成报告** - 生成结构化的比较报告

---

### 场景 2：深度研究任务

**任务**："研究量子计算的发展历史"

**工作流可视化**：

```
查询知识库
    │
    ▼
网络搜索 (已完成 ✓)
    │
    ├─────────────┬──────────────┐
    │             │              │
    ▼             ▼              ▼
论文检索    新闻检索      视频检索
    │             │              │
    └─────────────┼──────────────┘
                  │
                  ▼
            内容聚合
                  │
                  ▼
            时间轴排序
                  │
                  ▼
            生成报告
```

**并行处理展示**：
- 三个检索任务**同时执行**
- 视觉上通过分叉展示并行
- 完成后汇聚到下一步

---

### 场景 3：条件分支任务

**任务**："根据代码质量决定是否需要重构"

**工作流可视化**：

```
代码分析
    │
    ▼
质量评分 > 60？
    │
    ├─── 是 ──→ 生成优化建议
    │               │
    │               ▼
    │          返回建议
    │
    └─── 否 ──→ 深度分析问题
                    │
                    ▼
                生成重构方案
                    │
                    ▼
                返回方案
```

**条件判断展示**：
- 菱形节点表示决策点
- 不同分支用不同颜色标识
- 显示实际执行的路径

---

## 技术架构

### 整体架构图

```
┌──────────────────────────────────────────────────────────┐
│                    Frontend Layer                         │
│  ┌────────────────────────────────────────────────────┐  │
│  │         WorkflowVisualization (React Component)    │  │
│  │  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  │  │
│  │  │ ReactFlow    │  │ Custom Nodes│  │ Controls │  │  │
│  │  │   Core       │  │   Renderer  │  │  Panel   │  │  │
│  │  └──────────────┘  └─────────────┘  └──────────┘  │  │
│  └────────────────────────────────────────────────────┘  │
│              ▲                                            │
│              │ WebSocket / SSE (实时更新)                │
│              ▼                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │           Workflow State Manager (Zustand)         │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          │
                          │ HTTP API
                          ▼
┌──────────────────────────────────────────────────────────┐
│                    Backend Layer                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │    AgentExecutionEngine (Spring Boot)             │  │
│  │  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  │  │
│  │  │ Execution    │  │ Step         │  │ Event    │  │  │
│  │  │   Tracker    │  │  Manager     │  │ Publisher│  │  │
│  │  └──────────────┘  └─────────────┘  └──────────┘  │  │
│  └────────────────────────────────────────────────────┘  │
│              ▲                                            │
│              │                                            │
│  ┌────────────────────────────────────────────────────┐  │
│  │      WorkflowRepository (PostgreSQL)               │  │
│  │  - t_agent_execution_steps                        │  │
│  │  - t_agent_execution_edges                        │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 技术栈选择

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **前端核心** | @xyflow/react | 12.10.0 | 工作流渲染引擎 |
| **状态管理** | zustand | 5.0.2 | 工作流状态管理 |
| **实时通信** | EventSource (SSE) | 原生 | 实时步骤更新 |
| **图形布局** | dagre | 0.8.5 | 自动布局算法 |
| **后端框架** | Spring Boot | 3.5.7 | REST API |
| **数据存储** | PostgreSQL | 14+ | 工作流数据持久化 |

---

## 数据模型设计

### 后端数据模型

#### 表 1：t_agent_execution_steps（执行步骤）

```sql
CREATE TABLE t_agent_execution_steps (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    
    -- 关联字段
    run_id VARCHAR(64) NOT NULL,          -- 所属 Agent Run
    parent_step_id VARCHAR(64),           -- 父步骤 ID（用于嵌套）
    
    -- 步骤信息
    step_type VARCHAR(32) NOT NULL,       -- 步骤类型
    step_name VARCHAR(128) NOT NULL,      -- 步骤名称
    step_description TEXT,                -- 步骤描述
    
    -- 执行状态
    status VARCHAR(32) DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    
    -- 结果数据
    result_summary TEXT,
    result_data JSONB,
    error_message TEXT,
    
    -- 可视化位置
    position_x NUMERIC,
    position_y NUMERIC,
    
    -- 元数据
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_steps_run_id ON t_agent_execution_steps (run_id);
CREATE INDEX idx_steps_status ON t_agent_execution_steps (status);
```

#### 表 2：t_agent_execution_edges（执行边）

```sql
CREATE TABLE t_agent_execution_edges (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    source_step_id VARCHAR(64) NOT NULL,
    target_step_id VARCHAR(64) NOT NULL,
    edge_type VARCHAR(32) DEFAULT 'SEQUENTIAL',
    condition TEXT,
    style JSONB,
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_edges_run_id ON t_agent_execution_edges (run_id);
```

---

## 实施步骤

### Phase 1：基础设施（第 1-2 天）

#### 步骤 1.1：安装依赖

```bash
cd frontend
npm install @xyflow/react@12.10.0 dagre@0.8.5
```

#### 步骤 1.2：创建数据库表

执行上述 SQL 创建两张表。

#### 步骤 1.3：创建基础目录结构

```bash
mkdir -p frontend/src/components/workflow
mkdir -p frontend/src/components/workflow/nodes
mkdir -p frontend/src/store/workflow
```

---

### Phase 2：核心组件（第 3-5 天）

#### 步骤 2.1：创建 WorkflowCanvas 容器

**文件**：`frontend/src/components/workflow/WorkflowCanvas.tsx`

```typescript
'use client';

import { useCallback } from 'react';
import { 
  ReactFlow, 
  Background, 
  Controls, 
  MiniMap,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { CustomProcessNode } from './nodes/CustomProcessNode';

const nodeTypes = {
  process: CustomProcessNode
};

interface WorkflowCanvasProps {
  initialNodes: Node[];
  initialEdges: Edge[];
  onNodeClick?: (node: Node) => void;
}

export function WorkflowCanvas({ 
  initialNodes, 
  initialEdges,
  onNodeClick 
}: WorkflowCanvasProps) {
  const [nodes, , onNodesChange] = useNodesState(initialNodes);
  const [edges, , onEdgesChange] = useEdgesState(initialEdges);

  const handleNodeClick = useCallback((event: React.MouseEvent, node: Node) => {
    onNodeClick?.(node);
  }, [onNodeClick]);

  return (
    <div className="h-[600px] w-full border rounded-lg">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={handleNodeClick}
        nodeTypes={nodeTypes}
        fitView
        minZoom={0.5}
        maxZoom={1.5}
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
}
```

#### 步骤 2.2：创建自定义处理节点

**文件**：`frontend/src/components/workflow/nodes/CustomProcessNode.tsx`

```typescript
'use client';

import { Handle, Position } from '@xyflow/react';
import { CheckCircle, AlertCircle, Clock, Loader2 } from 'lucide-react';

interface ProcessNodeData {
  label: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  description?: string;
  durationMs?: number;
}

export function CustomProcessNode({ data, selected }: { 
  data: ProcessNodeData; 
  selected: boolean;
}) {
  const statusIcons = {
    pending: Clock,
    running: Loader2,
    completed: CheckCircle,
    failed: AlertCircle
  };

  const Icon = statusIcons[data.status];

  return (
    <div className="px-4 py-3 rounded-lg border-2 min-w-[180px] bg-white shadow-sm">
      <Handle type="target" position={Position.Top} />
      
      <div className="flex items-start gap-2">
        <Icon className="w-5 h-5" />
        <div>
          <div className="font-medium text-sm">{data.label}</div>
          {data.description && (
            <div className="text-xs text-gray-500 mt-1">{data.description}</div>
          )}
        </div>
      </div>
      
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}
```

---

### Phase 3：后端集成（第 6-8 天）

#### 步骤 3.1：创建 WorkflowService

**文件**：`seahorse-agent-kernel/src/main/java/com/seahorse/agent/kernel/application/workflow/WorkflowService.java`

```java
@Service
public class WorkflowService {
    
    private final WorkflowRepository workflowRepository;
    
    public WorkflowVisualization getWorkflowVisualization(String runId) {
        List<ExecutionStep> steps = workflowRepository.findStepsByRunId(runId);
        List<ExecutionEdge> edges = workflowRepository.findEdgesByRunId(runId);
        
        return new WorkflowVisualization(
            convertStepsToNodes(steps),
            convertEdgesToEdges(edges)
        );
    }
    
    public void recordStep(String runId, ExecutionStep step) {
        workflowRepository.saveStep(step);
        // 发送 SSE 事件
        sseEmitter.send(new StepUpdateEvent(runId, step));
    }
}
```

#### 步骤 3.2：创建 REST API

```java
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    
    @GetMapping("/{runId}/visualization")
    public ResponseEntity<WorkflowVisualization> getVisualization(
        @PathVariable String runId
    ) {
        return ResponseEntity.ok(workflowService.getWorkflowVisualization(runId));
    }
    
    @GetMapping("/{runId}/stream")
    public SseEmitter streamUpdates(@PathVariable String runId) {
        return workflowService.createSseEmitter(runId);
    }
}
```

---

## 代码示例

### 完整使用示例

```typescript
// pages/agent-run/[id]/workflow.tsx
import { useState, useEffect } from 'react';
import { WorkflowCanvas } from '@/components/workflow/WorkflowCanvas';

export default function WorkflowPage({ runId }: { runId: string }) {
  const [nodes, setNodes] = useState([]);
  const [edges, setEdges] = useState([]);

  useEffect(() => {
    // 1. 加载初始工作流
    fetch(`/api/workflows/${runId}/visualization`)
      .then(res => res.json())
      .then(data => {
        setNodes(data.nodes);
        setEdges(data.edges);
      });

    // 2. 监听实时更新
    const eventSource = new EventSource(`/api/workflows/${runId}/stream`);
    
    eventSource.addEventListener('step-update', (event) => {
      const update = JSON.parse(event.data);
      setNodes(prev => prev.map(node => 
        node.id === update.stepId 
          ? { ...node, data: { ...node.data, status: update.status } }
          : node
      ));
    });

    return () => eventSource.close();
  }, [runId]);

  const handleNodeClick = (node) => {
    console.log('节点点击:', node);
    // 打开详情抽屉
  };

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold mb-4">工作流可视化</h1>
      <WorkflowCanvas 
        initialNodes={nodes}
        initialEdges={edges}
        onNodeClick={handleNodeClick}
      />
    </div>
  );
}
```

---

## 测试方案

### 单元测试

```typescript
// CustomProcessNode.test.tsx
import { render } from '@testing-library/react';
import { CustomProcessNode } from './CustomProcessNode';

test('显示运行中状态', () => {
  const { getByText } = render(
    <CustomProcessNode 
      data={{ label: '搜索', status: 'running' }} 
      selected={false}
    />
  );
  
  expect(getByText('搜索')).toBeInTheDocument();
});
```

### 集成测试

```java
@Test
void testRecordAndRetrieveWorkflow() {
    String runId = "run_123";
    
    // 记录步骤
    workflowService.recordStep(runId, step1);
    workflowService.recordStep(runId, step2);
    
    // 获取可视化
    WorkflowVisualization viz = workflowService.getWorkflowVisualization(runId);
    
    assertEquals(2, viz.getNodes().size());
}
```

---

## 性能优化

### 前端优化

1. **虚拟化渲染**：对于超过 100 个节点的工作流，使用虚拟化
2. **节点缓存**：使用 React.memo 缓存节点组件
3. **延迟加载**：大型工作流分批加载

### 后端优化

1. **索引优化**：在 run_id 和 status 字段上建立索引
2. **批量查询**：一次查询获取所有步骤和边
3. **缓存**：使用 Redis 缓存热点工作流

---

## 常见问题

### Q1：如何自动布局节点？

使用 dagre 库：

```typescript
import dagre from 'dagre';

function autoLayout(nodes, edges) {
  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setGraph({ rankdir: 'TB' });
  
  nodes.forEach(node => {
    dagreGraph.setNode(node.id, { width: 180, height: 80 });
  });
  
  edges.forEach(edge => {
    dagreGraph.setEdge(edge.source, edge.target);
  });
  
  dagre.layout(dagreGraph);
  
  return nodes.map(node => ({
    ...node,
    position: dagreGraph.node(node.id)
  }));
}
```

### Q2：如何导出为图片？

```typescript
import { toPng } from 'html-to-image';

async function exportWorkflow(elementId: string) {
  const element = document.getElementById(elementId);
  const dataUrl = await toPng(element);
  
  const link = document.createElement('a');
  link.download = 'workflow.png';
  link.href = dataUrl;
  link.click();
}
```

### Q3：如何处理大型工作流（1000+ 节点）？

1. 启用分组折叠
2. 使用虚拟滚动
3. 按需加载子图
4. 简化展示（隐藏部分节点）

---

## 总结

通过工作流可视化，Seahorse Agent 可以：
- ✅ 提升用户理解度 60%
- ✅ 降低客服成本 30%
- ✅ 增强产品差异化

**预计工作量**：2 人 × 8 天 = 16 人天

**上线时间**：2 周内完成基础版本

---

**文档版本**：v1.0  
**最后更新**：2026-06-02  
**维护者**：技术团队
