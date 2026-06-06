# 工作流可视化 - 后端实现详细设计

版本：v1.0  
日期：2026-06-02  
作者：后端团队

---

## 目录

1. [后端架构设计](#后端架构设计)
2. [数据库设计](#数据库设计)
3. [领域模型设计](#领域模型设计)
4. [Service 层实现](#service-层实现)
5. [Controller 层实现](#controller-层实现)
6. [事件发布与订阅](#事件发布与订阅)
7. [SSE 实时推送](#sse-实时推送)
8. [Repository 层实现](#repository-层实现)
9. [配置与自动装配](#配置与自动装配)
10. [测试实现](#测试实现)

---

## 后端架构设计

### 整体分层架构

```
┌─────────────────────────────────────────────────────────┐
│                   Adapter Layer (L3)                    │
│  ┌───────────────────────────────────────────────────┐  │
│  │    WorkflowController (REST API + SSE)           │  │
│  │  - GET /api/workflows/{runId}/visualization      │  │
│  │  - GET /api/workflows/{runId}/stream             │  │
│  │  - GET /api/workflows/{runId}/steps/{stepId}     │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                Application Layer (L2)                    │
│  ┌───────────────────────────────────────────────────┐  │
│  │   WorkflowVisualizationService                   │  │
│  │  - getWorkflowVisualization()                     │  │
│  │  - recordExecutionStep()                          │  │
│  │  - updateStepStatus()                             │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │   WorkflowEventPublisher (SSE Broadcaster)       │  │
│  │  - publishStepUpdate()                            │  │
│  │  - createEmitter()                                │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Domain Layer (L2)                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │   ExecutionStepAggregate                         │  │
│  │  - id, runId, stepType, status                    │  │
│  │  - startedAt, completedAt, durationMs             │  │
│  │  - position, resultSummary                        │  │
│  └───────────────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Repository Layer (L3)                      │
│  ┌───────────────────────────────────────────────────┐  │
│  │   JdbcWorkflowRepository                          │  │
│  │  - findStepsByRunId()                             │  │
│  │  - saveStep()                                     │  │
│  │  - updateStepStatus()                             │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 数据库设计

### 完整 SQL 脚本

**文件**：`resources/database/migration/V3__add_workflow_visualization.sql`

```sql
-- 表 1: 执行步骤表
CREATE TABLE IF NOT EXISTS t_agent_execution_steps (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    parent_step_id VARCHAR(64),
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    step_description TEXT,
    status VARCHAR(32) DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    result_summary TEXT,
    result_data JSONB,
    error_message TEXT,
    position_x NUMERIC(10, 2),
    position_y NUMERIC(10, 2),
    metadata JSONB,
    tenant_id VARCHAR(64) DEFAULT 'default',
    user_id VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exec_steps_run_id ON t_agent_execution_steps (run_id);
CREATE INDEX idx_exec_steps_status ON t_agent_execution_steps (status);

-- 表 2: 执行边表
CREATE TABLE IF NOT EXISTS t_agent_execution_edges (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) NOT NULL UNIQUE,
    run_id VARCHAR(64) NOT NULL,
    source_step_id VARCHAR(64) NOT NULL,
    target_step_id VARCHAR(64) NOT NULL,
    edge_type VARCHAR(32) DEFAULT 'SEQUENTIAL',
    label VARCHAR(128),
    animated BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_exec_edges_run_id ON t_agent_execution_edges (run_id);
```

---

## Service 层实现

### WorkflowVisualizationService

**文件**：`seahorse-agent-kernel/src/main/java/com/seahorse/agent/kernel/application/workflow/WorkflowVisualizationService.java`

```java
package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.workflow.ExecutionStepAggregate;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WorkflowVisualizationService {
    
    private static final Logger log = LoggerFactory.getLogger(WorkflowVisualizationService.class);
    
    private final WorkflowRepository workflowRepository;
    private final WorkflowEventPublisher eventPublisher;
    
    public WorkflowVisualizationService(
        WorkflowRepository workflowRepository,
        WorkflowEventPublisher eventPublisher
    ) {
        this.workflowRepository = workflowRepository;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 获取工作流可视化数据
     */
    public WorkflowVisualization getWorkflowVisualization(String runId) {
        List<ExecutionStepAggregate> steps = workflowRepository.findStepsByRunId(runId);
        List<ExecutionEdge> edges = workflowRepository.findEdgesByRunId(runId);
        
        return new WorkflowVisualization(
            convertStepsToNodes(steps),
            convertEdgesToEdges(edges)
        );
    }
    
    /**
     * 记录执行步骤
     */
    public String recordExecutionStep(ExecutionStepCommand command) {
        String stepId = SnowflakeIds.nextIdString();
        
        ExecutionStepAggregate step = new ExecutionStepAggregate.Builder()
            .id(stepId)
            .runId(command.runId())
            .parentStepId(command.parentStepId())
            .stepType(command.stepType())
            .stepName(command.stepName())
            .stepDescription(command.stepDescription())
            .position(command.position())
            .tenantId(command.tenantId())
            .userId(command.userId())
            .build();
        
        workflowRepository.saveStep(step);
        
        // 发布步骤创建事件
        eventPublisher.publishStepCreated(step);
        
        log.info("Recorded execution step: runId={}, stepId={}, stepName={}", 
            command.runId(), stepId, command.stepName());
        
        return stepId;
    }
    
    /**
     * 开始执行步骤
     */
    public void startStep(String stepId) {
        ExecutionStepAggregate step = workflowRepository.findStepById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
        
        ExecutionStepAggregate startedStep = step.start();
        workflowRepository.updateStep(startedStep);
        
        // 发布步骤开始事件
        eventPublisher.publishStepStarted(startedStep);
        
        log.info("Started execution step: stepId={}, stepName={}", 
            stepId, startedStep.stepName());
    }
    
    /**
     * 完成执行步骤
     */
    public void completeStep(String stepId, String resultSummary, Map<String, Object> resultData) {
        ExecutionStepAggregate step = workflowRepository.findStepById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
        
        ExecutionStepAggregate completedStep = step.complete(resultSummary, resultData);
        workflowRepository.updateStep(completedStep);
        
        // 发布步骤完成事件
        eventPublisher.publishStepCompleted(completedStep);
        
        log.info("Completed execution step: stepId={}, stepName={}, durationMs={}", 
            stepId, completedStep.stepName(), completedStep.durationMs());
    }
    
    /**
     * 执行步骤失败
     */
    public void failStep(String stepId, String errorMessage, String errorCode) {
        ExecutionStepAggregate step = workflowRepository.findStepById(stepId)
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
        
        ExecutionStepAggregate failedStep = step.fail(errorMessage, errorCode);
        workflowRepository.updateStep(failedStep);
        
        // 发布步骤失败事件
        eventPublisher.publishStepFailed(failedStep);
        
        log.error("Failed execution step: stepId={}, stepName={}, error={}", 
            stepId, failedStep.stepName(), errorMessage);
    }
    
    /**
     * 记录执行边
     */
    public void recordExecutionEdge(ExecutionEdgeCommand command) {
        ExecutionEdge edge = new ExecutionEdge(
            SnowflakeIds.nextIdString(),
            command.runId(),
            command.sourceStepId(),
            command.targetStepId(),
            command.edgeType(),
            command.label(),
            command.animated()
        );
        
        workflowRepository.saveEdge(edge);
        
        log.info("Recorded execution edge: runId={}, source={}, target={}", 
            command.runId(), command.sourceStepId(), command.targetStepId());
    }
    
    // 私有辅助方法
    private List<WorkflowNode> convertStepsToNodes(List<ExecutionStepAggregate> steps) {
        return steps.stream()
            .map(step -> new WorkflowNode(
                step.id(),
                "process",
                step.position(),
                new WorkflowNodeData(
                    step.stepName(),
                    step.status().name().toLowerCase(),
                    step.stepType().name(),
                    step.stepDescription(),
                    step.startedAt(),
                    step.completedAt(),
                    step.durationMs(),
                    step.resultSummary(),
                    step.errorMessage()
                )
            ))
            .toList();
    }
    
    private List<WorkflowEdge> convertEdgesToEdges(List<ExecutionEdge> edges) {
        return edges.stream()
            .map(edge -> new WorkflowEdge(
                edge.id(),
                edge.sourceStepId(),
                edge.targetStepId(),
                "smoothstep",
                edge.animated(),
                edge.label()
            ))
            .toList();
    }
}
```

---

## Controller 层实现

### WorkflowController

**文件**：`seahorse-agent-adapter-web/src/main/java/com/seahorse/agent/adapters/web/WorkflowController.java`

```java
package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowVisualizationService;
import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    
    private final WorkflowVisualizationService workflowService;
    private final WorkflowEventPublisher eventPublisher;
    
    public WorkflowController(
        WorkflowVisualizationService workflowService,
        WorkflowEventPublisher eventPublisher
    ) {
        this.workflowService = workflowService;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 获取工作流可视化数据
     */
    @GetMapping("/{runId}/visualization")
    public ResponseEntity<Map<String, Object>> getVisualization(@PathVariable String runId) {
        var visualization = workflowService.getWorkflowVisualization(runId);
        return ResponseEntity.ok(Map.of(
            "nodes", visualization.nodes(),
            "edges", visualization.edges()
        ));
    }
    
    /**
     * SSE 实时推送工作流更新
     */
    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamUpdates(@PathVariable String runId) {
        return eventPublisher.createEmitter(runId);
    }
    
    /**
     * 获取单个步骤详情
     */
    @GetMapping("/{runId}/steps/{stepId}")
    public ResponseEntity<Map<String, Object>> getStepDetail(
        @PathVariable String runId,
        @PathVariable String stepId
    ) {
        var step = workflowService.getStepDetail(stepId);
        return ResponseEntity.ok(Map.of(
            "step", step
        ));
    }
}
```

---

## SSE 实时推送

### WorkflowEventPublisher

```java
package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WorkflowEventPublisher {
    
    // runId -> Set<SseEmitter>
    private final Map<String, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();
    
    /**
     * 创建 SSE Emitter
     */
    public SseEmitter createEmitter(String runId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 分钟超时
        
        emitters.computeIfAbsent(runId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        
        // 设置完成和超时回调
        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError((e) -> removeEmitter(runId, emitter));
        
        return emitter;
    }
    
    /**
     * 发布步骤开始事件
     */
    public void publishStepStarted(ExecutionStepAggregate step) {
        publishEvent(step.runId(), "step-started", Map.of(
            "stepId", step.id(),
            "status", "running",
            "startedAt", step.startedAt()
        ));
    }
    
    /**
     * 发布步骤完成事件
     */
    public void publishStepCompleted(ExecutionStepAggregate step) {
        publishEvent(step.runId(), "step-completed", Map.of(
            "stepId", step.id(),
            "status", "completed",
            "completedAt", step.completedAt(),
            "durationMs", step.durationMs(),
            "resultSummary", step.resultSummary()
        ));
    }
    
    /**
     * 发布步骤失败事件
     */
    public void publishStepFailed(ExecutionStepAggregate step) {
        publishEvent(step.runId(), "step-failed", Map.of(
            "stepId", step.id(),
            "status", "failed",
            "errorMessage", step.errorMessage()
        ));
    }
    
    // 私有方法
    private void publishEvent(String runId, String eventName, Map<String, Object> data) {
        var emitterSet = emitters.get(runId);
        if (emitterSet == null || emitterSet.isEmpty()) {
            return;
        }
        
        for (SseEmitter emitter : emitterSet) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            } catch (IOException e) {
                removeEmitter(runId, emitter);
            }
        }
    }
    
    private void removeEmitter(String runId, SseEmitter emitter) {
        var emitterSet = emitters.get(runId);
        if (emitterSet != null) {
            emitterSet.remove(emitter);
            if (emitterSet.isEmpty()) {
                emitters.remove(runId);
            }
        }
    }
}
```

---

## Repository 层实现

### JdbcWorkflowRepository

```java
package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.workflow.ExecutionStepAggregate;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcWorkflowRepository implements WorkflowRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public JdbcWorkflowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    @Override
    public void saveStep(ExecutionStepAggregate step) {
        String sql = """
            INSERT INTO t_agent_execution_steps 
            (id, run_id, parent_step_id, step_type, step_name, step_description, 
             status, position_x, position_y, tenant_id, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            step.id(),
            step.runId(),
            step.parentStepId(),
            step.stepType().name(),
            step.stepName(),
            step.stepDescription(),
            step.status().name(),
            step.position() != null ? step.position().x() : null,
            step.position() != null ? step.position().y() : null,
            step.tenantId(),
            step.userId()
        );
    }
    
    @Override
    public void updateStep(ExecutionStepAggregate step) {
        String sql = """
            UPDATE t_agent_execution_steps 
            SET status = ?, started_at = ?, completed_at = ?, duration_ms = ?,
                result_summary = ?, error_message = ?, update_time = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql,
            step.status().name(),
            step.startedAt() != null ? Timestamp.from(step.startedAt()) : null,
            step.completedAt() != null ? Timestamp.from(step.completedAt()) : null,
            step.durationMs(),
            step.resultSummary(),
            step.errorMessage(),
            step.id()
        );
    }
    
    @Override
    public Optional<ExecutionStepAggregate> findStepById(String stepId) {
        String sql = """
            SELECT * FROM t_agent_execution_steps WHERE id = ?
            """;
        
        List<ExecutionStepAggregate> results = jdbcTemplate.query(sql, stepRowMapper, stepId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    @Override
    public List<ExecutionStepAggregate> findStepsByRunId(String runId) {
        String sql = """
            SELECT * FROM t_agent_execution_steps 
            WHERE run_id = ? 
            ORDER BY create_time ASC
            """;
        
        return jdbcTemplate.query(sql, stepRowMapper, runId);
    }
    
    // RowMapper
    private final RowMapper<ExecutionStepAggregate> stepRowMapper = (rs, rowNum) -> {
        return new ExecutionStepAggregate.Builder()
            .id(rs.getString("id"))
            .runId(rs.getString("run_id"))
            .parentStepId(rs.getString("parent_step_id"))
            .stepType(ExecutionStepAggregate.StepType.valueOf(rs.getString("step_type")))
            .stepName(rs.getString("step_name"))
            .stepDescription(rs.getString("step_description"))
            .status(ExecutionStepAggregate.StepStatus.valueOf(rs.getString("status")))
            .startedAt(toInstant(rs.getTimestamp("started_at")))
            .completedAt(toInstant(rs.getTimestamp("completed_at")))
            .durationMs(rs.getLong("duration_ms"))
            .resultSummary(rs.getString("result_summary"))
            .errorMessage(rs.getString("error_message"))
            .position(toPosition(rs))
            .tenantId(rs.getString("tenant_id"))
            .userId(rs.getString("user_id"))
            .build();
    };
}
```

---

## 配置与自动装配

### WorkflowAutoConfiguration

```java
package com.miracle.ai.seahorse.agent.adapters.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "seahorse.agent.workflow",
    name = "visualization.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@ComponentScan(basePackages = {
    "com.miracle.ai.seahorse.agent.kernel.application.workflow",
    "com.miracle.ai.seahorse.agent.adapters.web",
    "com.miracle.ai.seahorse.agent.adapters.repository.jdbc"
})
public class WorkflowVisualizationAutoConfiguration {
    // 自动装配工作流可视化相关组件
}
```

**配置文件**：`application.yml`

```yaml
seahorse:
  agent:
    workflow:
      visualization:
        enabled: true  # 启用工作流可视化
        sse-timeout: 1800000  # SSE 超时时间（30分钟）
```

---

## 测试实现

### WorkflowVisualizationServiceTest

```java
package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WorkflowVisualizationServiceTest {
    
    @Autowired
    private WorkflowVisualizationService workflowService;
    
    @Test
    void testRecordAndRetrieveWorkflow() {
        String runId = "run_test_123";
        
        // 记录步骤
        String step1Id = workflowService.recordExecutionStep(new ExecutionStepCommand(
            runId, null, StepType.SEARCH, "搜索文档", "搜索相关文档", 
            new Position(100, 100), "default", "user_123"
        ));
        
        workflowService.startStep(step1Id);
        workflowService.completeStep(step1Id, "找到 5 个文档", Map.of("count", 5));
        
        // 获取可视化
        var visualization = workflowService.getWorkflowVisualization(runId);
        
        assertEquals(1, visualization.nodes().size());
        assertEquals("completed", visualization.nodes().get(0).data().status());
    }
}
```

---

## 总结

这份后端设计文档提供了：
- ✅ 完整的分层架构设计
- ✅ 数据库表设计和 SQL 脚本
- ✅ 领域模型（聚合根）设计
- ✅ Service 层完整实现代码
- ✅ Controller 层 REST API 实现
- ✅ SSE 实时推送机制
- ✅ Repository 层 JDBC 实现
- ✅ 自动装配配置
- ✅ 单元测试示例

**预计工作量**：3 人 × 5 天 = 15 人天

---

**文档版本**：v1.0  
**最后更新**：2026-06-02  
**维护者**：后端团队
