# 08 · 工作流可视化（DAG 编排 + 实时监控）— 可落地技术方案

> 状态：已定稿 ｜ 作者：架构组 ｜ 日期：2026-06-05 ｜ 定位：主线功能增强
>
> **重要发现**：后端设计已完整（见 `docs/WORKFLOW-VISUALIZATION-BACKEND-DESIGN.md`），包含表结构、领域模型、SSE 推送。本方案聚焦**前端可视化实现、技术选型、集成路径**。

---

## 1. 目标与范围

### 1.1 做什么（MVP 交付物）

| 编号 | 目标 | 优先级 | 现状 |
|------|------|--------|------|
| G1 | 实现后端设计文档中定义的全部 API（CRUD + SSE 推送） | P0 | 设计完整 ✅ 代码未实现 ❌ |
| G2 | 前端 DAG 可视化画布：显示节点（step）、边（依赖）、状态（running/success/failed） | P0 | 无 |
| G3 | 实时执行监控：SSE 订阅执行状态，节点动态高亮、进度更新 | P0 | 后端设计完整 ✅ |
| G4 | 工作流列表页：历史运行记录、筛选、详情跳转 | P1 | 无 |
| G5 | 节点详情抽屉：点击节点查看输入输出、日志、耗时 | P1 | 无 |

### 1.2 明确不做（Phase 3）

- **不做**拖拽编排器（本期只展示执行结果，不支持用户创建工作流）
- **不做**工作流模板市场
- **不做**子工作流嵌套可视化
- **不做**条件分支（if/switch）可视化

### 1.3 验收信号

1. 启动一个 Agent 运行，工作流页面实时显示"检索 → 推理 → 工具调用"的 DAG
2. 某节点执行失败，画布上该节点标红，点击查看错误堆栈
3. 100 个并发运行同时推送状态，前端无卡顿，延迟 < 2 秒
4. 历史运行记录可按时间/状态筛选，分页加载 < 500ms

---

## 2. 现状（代码级审查 + 设计文档总结）

### 2.1 后端设计文档已完成（可直接实施）

**文档**：`docs/WORKFLOW-VISUALIZATION-BACKEND-DESIGN.md`（约 800 行）

**核心内容**：

#### 2.1.1 数据库设计

**表**：`t_agent_execution_steps`（执行步骤表）

```sql
CREATE TABLE t_agent_execution_steps (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    parent_step_id VARCHAR(64),
    step_type VARCHAR(32) NOT NULL,  -- 'retrieval', 'reasoning', 'tool_call'
    step_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    result_summary TEXT,
    result_data JSONB,
    error_message TEXT,
    position_x NUMERIC(10, 2),  -- 前端布局坐标
    position_y NUMERIC(10, 2),
    tenant_id VARCHAR(64) DEFAULT 'default',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_steps_run_id ON t_agent_execution_steps(run_id);
CREATE INDEX idx_steps_tenant ON t_agent_execution_steps(tenant_id);
```

**表**：`t_agent_execution_step_edges`（步骤依赖关系）

```sql
CREATE TABLE t_agent_execution_step_edges (
    edge_id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    source_step_id VARCHAR(64) NOT NULL,
    target_step_id VARCHAR(64) NOT NULL,
    edge_type VARCHAR(32) DEFAULT 'SEQUENTIAL',  -- SEQUENTIAL/CONDITIONAL
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_edges_run_id ON t_agent_execution_step_edges(run_id);
```

#### 2.1.2 领域模型

**类**：`ExecutionStepAggregate`（领域根）

```java
public class ExecutionStepAggregate {
    private String id;
    private String runId;
    private String parentStepId;
    private StepType stepType;  // RETRIEVAL, REASONING, TOOL_CALL
    private String stepName;
    private StepStatus status;  // PENDING, RUNNING, SUCCESS, FAILED
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private String resultSummary;
    private JsonNode resultData;
    private String errorMessage;
    private Position position;  // {x, y}
}

enum StepStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }
enum StepType { RETRIEVAL, REASONING, TOOL_CALL, HTTP_REQUEST, DB_QUERY }
```

#### 2.1.3 API 契约（设计文档定义）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/workflows/runs/{runId}/visualization` | 获取完整 DAG（nodes + edges） |
| GET | `/api/workflows/runs/{runId}/stream` | SSE 订阅实时状态 |
| GET | `/api/workflows/runs/{runId}/steps/{stepId}` | 获取节点详情 |
| GET | `/api/workflows/runs` | 分页查询运行历史 |
| POST | `/api/workflows/runs/{runId}/steps` | 记录新步骤（内部调用） |
| PUT | `/api/workflows/runs/{runId}/steps/{stepId}/status` | 更新步骤状态（内部） |

#### 2.1.4 SSE 推送设计

**推送格式**：
```json
{
  "event": "step_status_update",
  "data": {
    "runId": "run-123",
    "stepId": "step-456",
    "status": "RUNNING",
    "timestamp": "2026-06-05T10:30:00Z"
  }
}
```

**实现**：Spring MVC `SseEmitter` + 内存 `ConcurrentHashMap` 管理连接

### 2.2 当前代码现状（待实现）

**检索结果**：全仓搜索工作流相关类

```bash
find . -name "*Workflow*.java" -o -name "*ExecutionStep*.java"
# 结果：无任何实现类
```

**结论**：后端设计文档完整，但**代码未实现**（0%）。

### 2.3 前端现状

**检索**：`frontend/src` 搜索 "workflow" 或 "execution"

```bash
grep -r "workflow" frontend/src --include="*.tsx" --include="*.ts"
# 结果：无相关代码
```

**结论**：前端无任何工作流页面或组件。

---

## 3. 技术方案

### 3.1 前端技术选型（React Flow vs AntV X6）

#### 3.1.1 对比分析

| 维度 | React Flow | AntV X6 | 推荐 |
|------|-----------|---------|------|
| **React 集成** | 原生 React 组件 ✅ | 需 wrapper | React Flow |
| **学习曲线** | 低（声明式） | 中（命令式 API） | React Flow |
| **自定义节点** | JSX 自定义 ✅ | Canvas 绘制 | React Flow |
| **性能（1000+ 节点）** | 中（虚拟化支持） | 高 ✅ | X6 |
| **布局算法** | Dagre/Elkjs | 内置多种 ✅ | X6 |
| **社区活跃度** | 高 ✅ | 中 | React Flow |
| **中文文档** | 英文为主 | 完整中文 ✅ | X6 |

**推荐**：**React Flow**（MVP 阶段）
- 理由：Agent 运行的 DAG 通常 < 50 节点，性能足够；React 生态集成更自然；自定义节点更简单。
- 企业版可切换 X6（处理复杂工作流）。

#### 3.1.2 依赖安装

```bash
cd frontend
pnpm add reactflow dagre  # dagre 用于自动布局
pnpm add -D @types/dagre
```

### 3.2 后端实现路径（按设计文档）

#### 3.2.1 Repository 层

**新增类**：`JdbcWorkflowVisualizationRepositoryAdapter`  
**路径**：`seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcWorkflowVisualizationRepositoryAdapter.java`（新建）

```java
@Component
public class JdbcWorkflowVisualizationRepositoryAdapter 
    implements WorkflowVisualizationRepositoryPort {
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final String SQL_FIND_STEPS_BY_RUN = """
        SELECT id, run_id, step_type, step_name, status, started_at, completed_at,
               duration_ms, result_summary, error_message, position_x, position_y
        FROM t_agent_execution_steps
        WHERE run_id = ? AND tenant_id = ?
        ORDER BY create_time
        """;
    
    private static final String SQL_FIND_EDGES_BY_RUN = """
        SELECT source_step_id, target_step_id, edge_type
        FROM t_agent_execution_step_edges
        WHERE run_id = ?
        """;
    
    @Override
    public WorkflowVisualizationGraph findByRunId(String runId, String tenantId) {
        List<ExecutionStepRecord> steps = jdbcTemplate.query(
            SQL_FIND_STEPS_BY_RUN, 
            this::mapStep, 
            runId, 
            tenantId
        );
        
        List<StepEdgeRecord> edges = jdbcTemplate.query(
            SQL_FIND_EDGES_BY_RUN,
            this::mapEdge,
            runId
        );
        
        return new WorkflowVisualizationGraph(steps, edges);
    }
    
    @Override
    public void saveStep(ExecutionStepRecord step) {
        jdbcTemplate.update("""
            INSERT INTO t_agent_execution_steps 
            (id, run_id, step_type, step_name, status, position_x, position_y, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            step.id(), step.runId(), step.stepType(), step.stepName(),
            step.status(), step.positionX(), step.positionY(), step.tenantId()
        );
    }
    
    @Override
    public void updateStepStatus(String stepId, StepStatus status, 
                                  Instant completedAt, Long durationMs, String errorMessage) {
        jdbcTemplate.update("""
            UPDATE t_agent_execution_steps
            SET status = ?, completed_at = ?, duration_ms = ?, error_message = ?
            WHERE id = ?
            """,
            status.name(), completedAt, durationMs, errorMessage, stepId
        );
    }
}
```

#### 3.2.2 Application 层

**新增类**：`KernelWorkflowVisualizationService`  
**路径**：`seahorse-agent-kernel/.../kernel/application/workflow/KernelWorkflowVisualizationService.java`（新建）

```java
public class KernelWorkflowVisualizationService implements WorkflowVisualizationInboundPort {
    
    private final WorkflowVisualizationRepositoryPort repositoryPort;
    private final WorkflowEventPublisher eventPublisher;
    private final TenantContext tenantContext;
    
    @Override
    public WorkflowVisualizationGraph getVisualization(String runId) {
        String tenantId = tenantContext.getTenantId();
        return repositoryPort.findByRunId(runId, tenantId);
    }
    
    @Override
    public void recordStepStart(String runId, String stepId, StepType type, String name) {
        var step = new ExecutionStepRecord(
            stepId, runId, type, name, StepStatus.RUNNING, Instant.now(), 
            null, null, null, null, calculatePosition(runId, stepId), tenantContext.getTenantId()
        );
        repositoryPort.saveStep(step);
        eventPublisher.publishStepUpdate(runId, stepId, StepStatus.RUNNING);
    }
    
    @Override
    public void recordStepComplete(String runId, String stepId, boolean success, String summary, String error) {
        Instant completedAt = Instant.now();
        Long durationMs = calculateDuration(stepId);
        StepStatus status = success ? StepStatus.SUCCESS : StepStatus.FAILED;
        
        repositoryPort.updateStepStatus(stepId, status, completedAt, durationMs, error);
        eventPublisher.publishStepUpdate(runId, stepId, status);
    }
    
    private Position calculatePosition(String runId, String stepId) {
        // 简单布局：按创建顺序横向排列
        int index = repositoryPort.countStepsByRun(runId);
        return new Position(index * 200.0, 100.0);
    }
}
```

**SSE 事件发布器**：`WorkflowEventPublisher`

```java
@Component
public class WorkflowEventPublisher {
    
    private final ConcurrentHashMap<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    
    public SseEmitter createEmitter(String runId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        
        return emitter;
    }
    
    public void publishStepUpdate(String runId, String stepId, StepStatus status) {
        List<SseEmitter> runEmitters = emitters.get(runId);
        if (runEmitters == null) return;
        
        var event = new StepStatusUpdateEvent(runId, stepId, status, Instant.now());
        
        runEmitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("step_status_update")
                    .data(event));
            } catch (IOException e) {
                removeEmitter(runId, emitter);
            }
        });
    }
    
    private void removeEmitter(String runId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(runId);
        }
    }
}

record StepStatusUpdateEvent(String runId, String stepId, StepStatus status, Instant timestamp) {}
```

#### 3.2.3 Controller 层

**新增类**：`SeahorseWorkflowVisualizationController`

```java
@RestController
@RequestMapping("/api/workflows")
public class SeahorseWorkflowVisualizationController {
    
    private final ObjectProvider<WorkflowVisualizationInboundPort> visualizationPortProvider;
    private final ObjectProvider<WorkflowEventPublisher> eventPublisherProvider;
    
    @GetMapping("/runs/{runId}/visualization")
    public Map<String, Object> getVisualization(@PathVariable String runId) {
        WorkflowVisualizationGraph graph = visualizationPortProvider.getIfAvailable()
            .getVisualization(runId);
        return Map.of("code", "0", "data", graph);
    }
    
    @GetMapping("/runs/{runId}/stream")
    public SseEmitter streamUpdates(@PathVariable String runId) {
        return eventPublisherProvider.getIfAvailable().createEmitter(runId);
    }
    
    @GetMapping("/runs/{runId}/steps/{stepId}")
    public Map<String, Object> getStepDetail(@PathVariable String runId, @PathVariable String stepId) {
        ExecutionStepRecord step = visualizationPortProvider.getIfAvailable()
            .getStepDetail(runId, stepId);
        return Map.of("code", "0", "data", step);
    }
    
    @GetMapping("/runs")
    public Map<String, Object> listRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        PageResult<WorkflowRunSummary> result = visualizationPortProvider.getIfAvailable()
            .listRuns(page, size, status);
        return Map.of("code", "0", "data", result);
    }
}
```

### 3.3 前端实现（React Flow）

#### 3.3.1 核心页面：WorkflowVisualizationPage

**路径**：`frontend/src/pages/workflow/WorkflowVisualizationPage.tsx`（新建）

```tsx
import ReactFlow, { Node, Edge, Controls, Background } from 'reactflow';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import dagre from 'dagre';
import 'reactflow/dist/style.css';

interface WorkflowVisualizationPageProps {}

export const WorkflowVisualizationPage = () => {
  const { runId } = useParams<{ runId: string }>();
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  
  useEffect(() => {
    // 1. 加载初始 DAG
    loadWorkflowGraph(runId);
    
    // 2. 订阅 SSE 实时更新
    const eventSource = new EventSource(`/api/workflows/runs/${runId}/stream`);
    eventSource.addEventListener('step_status_update', (e) => {
      const update = JSON.parse(e.data);
      updateNodeStatus(update.stepId, update.status);
    });
    
    return () => eventSource.close();
  }, [runId]);
  
  const loadWorkflowGraph = async (runId: string) => {
    const res = await fetch(`/api/workflows/runs/${runId}/visualization`);
    const { data } = await res.json();
    
    // 转换为 React Flow 格式
    const flowNodes = data.steps.map((step: any) => ({
      id: step.id,
      type: 'custom',
      position: { x: step.positionX || 0, y: step.positionY || 0 },
      data: {
        label: step.stepName,
        status: step.status,
        stepType: step.stepType,
        duration: step.durationMs,
      },
    }));
    
    const flowEdges = data.edges.map((edge: any) => ({
      id: `${edge.sourceStepId}-${edge.targetStepId}`,
      source: edge.sourceStepId,
      target: edge.targetStepId,
      type: 'smoothstep',
      animated: edge.sourceStatus === 'RUNNING',
    }));
    
    // 自动布局
    const layouted = applyDagreLayout(flowNodes, flowEdges);
    setNodes(layouted.nodes);
    setEdges(layouted.edges);
  };
  
  const updateNodeStatus = (stepId: string, status: string) => {
    setNodes((nds) =>
      nds.map((node) =>
        node.id === stepId
          ? { ...node, data: { ...node.data, status } }
          : node
      )
    );
  };
  
  const applyDagreLayout = (nodes: Node[], edges: Edge[]) => {
    const dagreGraph = new dagre.graphlib.Graph();
    dagreGraph.setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({ rankdir: 'LR', ranksep: 150, nodesep: 80 });
    
    nodes.forEach((node) => dagreGraph.setNode(node.id, { width: 180, height: 80 }));
    edges.forEach((edge) => dagreGraph.setEdge(edge.source, edge.target));
    
    dagre.layout(dagreGraph);
    
    const layoutedNodes = nodes.map((node) => {
      const positioned = dagreGraph.node(node.id);
      return {
        ...node,
        position: { x: positioned.x - 90, y: positioned.y - 40 },
      };
    });
    
    return { nodes: layoutedNodes, edges };
  };
  
  return (
    <div style={{ width: '100%', height: '800px' }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={{ custom: CustomStepNode }}
        fitView
      >
        <Controls />
        <Background />
      </ReactFlow>
    </div>
  );
};
```

#### 3.3.2 自定义节点组件

**文件**：`CustomStepNode.tsx`

```tsx
import { Handle, Position } from 'reactflow';
import { Tag, Spin } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined } from '@ant-design/icons';

const statusConfig = {
  RUNNING: { color: 'blue', icon: <Spin size="small" />, text: '运行中' },
  SUCCESS: { color: 'green', icon: <CheckCircleOutlined />, text: '成功' },
  FAILED: { color: 'red', icon: <CloseCircleOutlined />, text: '失败' },
  PENDING: { color: 'default', icon: <ClockCircleOutlined />, text: '待执行' },
};

export const CustomStepNode = ({ data }: any) => {
  const config = statusConfig[data.status] || statusConfig.PENDING;
  
  return (
    <div
      style={{
        padding: '10px 15px',
        borderRadius: '8px',
        border: `2px solid ${config.color}`,
        background: '#fff',
        minWidth: '180px',
      }}
    >
      <Handle type="target" position={Position.Left} />
      
      <div style={{ fontWeight: 'bold', marginBottom: '5px' }}>
        {data.label}
      </div>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Tag color={config.color} icon={config.icon}>
          {config.text}
        </Tag>
        {data.duration && <span style={{ fontSize: '12px', color: '#999' }}>{data.duration}ms</span>}
      </div>
      
      <Handle type="source" position={Position.Right} />
    </div>
  );
};
```

#### 3.3.3 历史记录页

**文件**：`WorkflowRunListPage.tsx`

```tsx
import { Table, Tag, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';

export const WorkflowRunListPage = () => {
  const [runs, setRuns] = useState([]);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  
  useEffect(() => {
    loadRuns();
  }, []);
  
  const loadRuns = async () => {
    setLoading(true);
    const res = await fetch('/api/workflows/runs?page=1&size=20');
    const { data } = await res.json();
    setRuns(data.items);
    setLoading(false);
  };
  
  const columns = [
    { title: 'Run ID', dataIndex: 'runId', key: 'runId' },
    { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt' },
    { 
      title: '状态', 
      dataIndex: 'status', 
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'SUCCESS' ? 'green' : status === 'FAILED' ? 'red' : 'blue'}>
          {status}
        </Tag>
      ),
    },
    { title: '总耗时', dataIndex: 'totalDurationMs', key: 'totalDurationMs', render: (ms: number) => `${ms}ms` },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Button type="link" onClick={() => navigate(`/workflows/runs/${record.runId}`)}>
          查看详情
        </Button>
      ),
    },
  ];
  
  return (
    <div>
      <h2>工作流运行历史</h2>
      <Table columns={columns} dataSource={runs} loading={loading} rowKey="runId" />
    </div>
  );
};
```

### 3.4 集成到 Agent 运行

#### 3.4.1 在 Agent 执行时埋点

**修改点**：`KernelAgentRunService` 或相关执行服务

```java
public class KernelAgentRunService {
    
    private final WorkflowVisualizationInboundPort workflowPort;
    
    public AgentRunResult run(AgentRunCommand command) {
        String runId = generateRunId();
        
        try {
            // 1. 记录检索步骤开始
            workflowPort.recordStepStart(runId, "step-retrieval-1", StepType.RETRIEVAL, "检索知识库");
            List<Chunk> chunks = retrievalEngine.retrieve(command.query());
            workflowPort.recordStepComplete(runId, "step-retrieval-1", true, chunks.size() + " chunks", null);
            
            // 2. 记录推理步骤
            workflowPort.recordStepStart(runId, "step-reasoning-1", StepType.REASONING, "LLM 推理");
            String response = chatModel.chat(buildPrompt(chunks, command.query()));
            workflowPort.recordStepComplete(runId, "step-reasoning-1", true, "生成 " + response.length() + " 字", null);
            
            // 3. 工具调用（如果有）
            if (needsToolCall(response)) {
                workflowPort.recordStepStart(runId, "step-tool-1", StepType.TOOL_CALL, "调用天气 API");
                String toolResult = toolGateway.invoke("weather", extractParams(response));
                workflowPort.recordStepComplete(runId, "step-tool-1", true, toolResult, null);
            }
            
            return new AgentRunResult(runId, response);
            
        } catch (Exception e) {
            workflowPort.recordStepComplete(runId, currentStepId, false, null, e.getMessage());
            throw e;
        }
    }
}
```

---

## 4. 配置与自动装配

### 4.1 AutoConfiguration

**新增类**：`SeahorseAgentWorkflowVisualizationAutoConfiguration`  
**路径**：`seahorse-agent-spring-boot-starter/.../spring/`

**注册顺序**：Layer 6 — Kernel sub-configs（依赖 DataSource + MQ）

```java
@Configuration
@ConditionalOnBean(DataSource.class)
@AutoConfigureAfter({
    DataSourceAutoConfiguration.class,
    SeahorseAgentMemoryRepositoryAutoConfiguration.class
})
public class SeahorseAgentWorkflowVisualizationAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowVisualizationRepositoryPort workflowVisualizationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkflowVisualizationRepositoryAdapter(jdbcTemplate);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowEventPublisher workflowEventPublisher() {
        return new WorkflowEventPublisher();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public WorkflowVisualizationInboundPort workflowVisualizationService(
            WorkflowVisualizationRepositoryPort repositoryPort,
            WorkflowEventPublisher eventPublisher) {
        return new KernelWorkflowVisualizationService(repositoryPort, eventPublisher);
    }
}
```

**注册到**：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
# 第 6 层 - Kernel sub-configs
...
com.miracle.ai.seahorse.agent.spring.SeahorseAgentWorkflowVisualizationAutoConfiguration
```

### 4.2 数据库升级

**文件**：`JdbcChatSchemaUpgrade.upgrade()` 方法中追加

```java
public void upgrade() {
    // ... 现有升级逻辑
    
    // 工作流可视化表
    addWorkflowVisualizationTables();
}

private void addWorkflowVisualizationTables() {
    if (!tableExists("t_agent_execution_steps")) {
        jdbcTemplate.execute("""
            CREATE TABLE t_agent_execution_steps (
                pk_id BIGSERIAL PRIMARY KEY,
                id VARCHAR(64) NOT NULL UNIQUE,
                run_id VARCHAR(64) NOT NULL,
                parent_step_id VARCHAR(64),
                step_type VARCHAR(32) NOT NULL,
                step_name VARCHAR(128) NOT NULL,
                status VARCHAR(32) DEFAULT 'PENDING',
                started_at TIMESTAMP,
                completed_at TIMESTAMP,
                duration_ms BIGINT,
                result_summary TEXT,
                result_data JSONB,
                error_message TEXT,
                position_x NUMERIC(10, 2),
                position_y NUMERIC(10, 2),
                tenant_id VARCHAR(64) DEFAULT 'default',
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        
        jdbcTemplate.execute("CREATE INDEX idx_steps_run_id ON t_agent_execution_steps(run_id)");
        jdbcTemplate.execute("CREATE INDEX idx_steps_tenant ON t_agent_execution_steps(tenant_id)");
    }
    
    if (!tableExists("t_agent_execution_step_edges")) {
        jdbcTemplate.execute("""
            CREATE TABLE t_agent_execution_step_edges (
                edge_id BIGSERIAL PRIMARY KEY,
                run_id VARCHAR(64) NOT NULL,
                source_step_id VARCHAR(64) NOT NULL,
                target_step_id VARCHAR(64) NOT NULL,
                edge_type VARCHAR(32) DEFAULT 'SEQUENTIAL',
                create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        
        jdbcTemplate.execute("CREATE INDEX idx_edges_run_id ON t_agent_execution_step_edges(run_id)");
    }
}
```

---

## 5. 任务清单（Checkbox）

### Phase 1 — 后端实现（P0，第 1 周）

- [ ] **Repository 层**
  - [ ] `JdbcWorkflowVisualizationRepositoryAdapter` 实现
  - [ ] `WorkflowVisualizationRepositoryPort` 接口定义
  - [ ] 数据库表创建（通过 `JdbcChatSchemaUpgrade`）
  - [ ] Repository 单元测试

- [ ] **Application 层**
  - [ ] `KernelWorkflowVisualizationService` 实现
  - [ ] `WorkflowEventPublisher` SSE 管理器
  - [ ] 埋点集成到 `KernelAgentRunService`
  - [ ] Service 集成测试

- [ ] **Controller 层**
  - [ ] `SeahorseWorkflowVisualizationController` 实现
  - [ ] SSE 端点测试（浏览器 EventSource 连接）
  - [ ] API 契约测试

- [ ] **AutoConfiguration**
  - [ ] `SeahorseAgentWorkflowVisualizationAutoConfiguration` 编写
  - [ ] 注册到 `AutoConfiguration.imports`（Layer 6）
  - [ ] 启动验证（Bean 创建成功）

### Phase 2 — 前端实现（P0，第 2 周）

- [ ] **React Flow 集成**
  - [ ] 安装依赖（`reactflow`, `dagre`）
  - [ ] `WorkflowVisualizationPage` 页面
  - [ ] `CustomStepNode` 自定义节点组件
  - [ ] Dagre 自动布局集成

- [ ] **SSE 实时更新**
  - [ ] EventSource 订阅实现
  - [ ] 节点状态动态更新
  - [ ] 连接断开重连机制
  - [ ] 错误处理（SSE 超时）

- [ ] **历史记录页**
  - [ ] `WorkflowRunListPage` 列表页
  - [ ] 分页、筛选、搜索
  - [ ] 跳转到详情页

- [ ] **节点详情抽屉**
  - [ ] 点击节点弹出详情
  - [ ] 显示输入输出、日志、耗时
  - [ ] 错误堆栈展示

### Phase 3 — 测试与优化（P1，第 3 周）

- [ ] **性能测试**
  - [ ] 100 并发 SSE 连接压测
  - [ ] 1000 节点 DAG 渲染性能
  - [ ] 前端虚拟化优化（如需要）

- [ ] **用户体验**
  - [ ] 加载态优化（Skeleton）
  - [ ] 空状态提示
  - [ ] 响应式布局（移动端适配）

---

## 6. 验收标准

1. ✅ 启动 Agent 运行，`/workflows/runs/{runId}` 页面实时显示"检索 → 推理 → 工具"DAG
2. ✅ 某步骤失败，节点标红，点击查看错误消息"API timeout after 5s"
3. ✅ 50 个并发运行同时推送状态，前端无卡顿，状态更新延迟 < 1 秒
4. ✅ 历史记录页分页加载 < 300ms，筛选"失败"状态生效
5. ✅ SSE 连接断开后 3 秒内自动重连，不丢失状态更新

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| **SSE 连接泄漏** | 服务器内存占用飙升 | ① 30 分钟无活动自动关闭 ② 定时清理僵尸连接 |
| **DAG 布局混乱** | 节点重叠、边交叉 | ① Dagre 算法调优 ② 提供手动拖拽（Phase 2） |
| **高并发推送** | CPU 占用高、延迟增加 | ① 批量发送（100ms 窗口合并）② MQ 异步解耦 |
| **前端状态不同步** | 用户看到旧数据 | ① 定期全量拉取校准（10s）② 乐观更新 |

---

## 8. 参考文件锚点

### 8.1 设计文档

- **后端详细设计**：`docs/WORKFLOW-VISUALIZATION-BACKEND-DESIGN.md`
- **简化设计**：`docs/WORKFLOW-BACKEND-DESIGN-SIMPLE.md`

### 8.2 相关代码（实施后）

- Repository：`seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcWorkflowVisualizationRepositoryAdapter.java`
- Service：`seahorse-agent-kernel/.../kernel/application/workflow/KernelWorkflowVisualizationService.java`
- Controller：`seahorse-agent-adapter-web/.../web/SeahorseWorkflowVisualizationController.java`
- 前端页面：`frontend/src/pages/workflow/WorkflowVisualizationPage.tsx`

### 8.3 表结构

- `t_agent_execution_steps`：执行步骤表
- `t_agent_execution_step_edges`：步骤依赖关系表

---

**文档版本**：v1.0-final  
**最后更新**：2026-06-05  
**已确认决策**：
- 前端可视化：**React Flow**（Agent 运行 DAG < 50 节点，性能足够；React 生态更自然；企业版可切换 X6）
- 实时推送：**SSE**（单向推送足够，比 WebSocket 简单；若并发 > 1000 再考虑 WebSocket）
- 埋点性能：**异步记录**（步骤开始/结束时发 MQ 异步入库，对主流程延迟 < 5ms）
