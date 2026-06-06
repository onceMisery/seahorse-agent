# 工作流可视化 - 后端实现（简洁版）

版本：v1.0  
日期：2026-06-02

---

## 数据库设计

### SQL 脚本

```sql
-- 执行步骤表
CREATE TABLE t_agent_execution_steps (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) UNIQUE NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    result_summary TEXT,
    error_message TEXT,
    position_x NUMERIC(10,2),
    position_y NUMERIC(10,2),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_steps_run ON t_agent_execution_steps(run_id);

-- 执行边表
CREATE TABLE t_agent_execution_edges (
    pk_id BIGSERIAL PRIMARY KEY,
    id VARCHAR(64) UNIQUE NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    source_step_id VARCHAR(64) NOT NULL,
    target_step_id VARCHAR(64) NOT NULL,
    edge_type VARCHAR(32) DEFAULT 'SEQUENTIAL',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_edges_run ON t_agent_execution_edges(run_id);
```

---

## 领域模型

### ExecutionStep.java

```java
package com.miracle.ai.seahorse.agent.kernel.domain.workflow;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ExecutionStep {
    private String id;
    private String runId;
    private StepType stepType;
    private String stepName;
    private StepStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private String resultSummary;
    private String errorMessage;
    private Double positionX;
    private Double positionY;
    
    public enum StepType {
        SEARCH, ANALYZE, GENERATE, RETRIEVE, TRANSFORM
    }
    
    public enum StepStatus {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }
}
```

---

## Service 层

### WorkflowService.java

```java
package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.workflow.ExecutionStep;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    
    private final WorkflowRepository repository;
    private final WorkflowEventPublisher eventPublisher;
    
    public WorkflowVisualization getVisualization(String runId) {
        List<ExecutionStep> steps = repository.findByRunId(runId);
        return new WorkflowVisualization(
            steps.stream().map(this::toNode).toList(),
            List.of()
        );
    }
    
    public String recordStep(String runId, StepType type, String name) {
        String stepId = SnowflakeIds.nextIdString();
        ExecutionStep step = ExecutionStep.builder()
            .id(stepId)
            .runId(runId)
            .stepType(type)
            .stepName(name)
            .status(StepStatus.PENDING)
            .build();
        
        repository.save(step);
        eventPublisher.publishCreated(step);
        return stepId;
    }
    
    public void startStep(String stepId) {
        ExecutionStep step = repository.findById(stepId).orElseThrow();
        step.setStatus(StepStatus.RUNNING);
        step.setStartedAt(Instant.now());
        repository.update(step);
        eventPublisher.publishStarted(step);
    }
    
    public void completeStep(String stepId, String summary) {
        ExecutionStep step = repository.findById(stepId).orElseThrow();
        Instant now = Instant.now();
        step.setStatus(StepStatus.COMPLETED);
        step.setCompletedAt(now);
        step.setDurationMs(Duration.between(step.getStartedAt(), now).toMillis());
        step.setResultSummary(summary);
        repository.update(step);
        eventPublisher.publishCompleted(step);
    }
    
    private WorkflowNode toNode(ExecutionStep step) {
        return new WorkflowNode(
            step.getId(),
            "process",
            new Position(step.getPositionX(), step.getPositionY()),
            new NodeData(step.getStepName(), 
                        step.getStatus().name().toLowerCase(),
                        step.getDurationMs())
        );
    }
}
```

---

## Controller 层

### WorkflowController.java

```java
package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowService;
import com.miracle.ai.seahorse.agent.kernel.application.workflow.WorkflowEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    
    private final WorkflowService workflowService;
    private final WorkflowEventPublisher eventPublisher;
    
    @GetMapping("/{runId}/visualization")
    public ResponseEntity<WorkflowVisualization> getVisualization(
        @PathVariable String runId
    ) {
        return ResponseEntity.ok(workflowService.getVisualization(runId));
    }
    
    @GetMapping(value = "/{runId}/stream", 
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        return eventPublisher.createEmitter(runId);
    }
}
```

---

## SSE 实时推送

### WorkflowEventPublisher.java

```java
package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class WorkflowEventPublisher {
    
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    
    public SseEmitter createEmitter(String runId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        return emitter;
    }
    
    public void publishStarted(ExecutionStep step) {
        sendEvent(step.getRunId(), "step-started", Map.of(
            "stepId", step.getId(),
            "status", "running"
        ));
    }
    
    public void publishCompleted(ExecutionStep step) {
        sendEvent(step.getRunId(), "step-completed", Map.of(
            "stepId", step.getId(),
            "status", "completed",
            "durationMs", step.getDurationMs()
        ));
    }
    
    private void sendEvent(String runId, String event, Map<String, Object> data) {
        Set<SseEmitter> set = emitters.get(runId);
        if (set == null) return;
        set.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                remove(runId, emitter);
            }
        });
    }
    
    private void remove(String runId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(runId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) emitters.remove(runId);
        }
    }
}
```

---

## Repository 层

### WorkflowRepository.java

```java
package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.workflow.ExecutionStep;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WorkflowRepository {
    
    private final JdbcTemplate jdbc;
    
    public void save(ExecutionStep step) {
        jdbc.update(
            "INSERT INTO t_agent_execution_steps " +
            "(id, run_id, step_type, step_name, status, position_x, position_y) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            step.getId(), step.getRunId(), step.getStepType().name(),
            step.getStepName(), step.getStatus().name(),
            step.getPositionX(), step.getPositionY()
        );
    }
    
    public void update(ExecutionStep step) {
        jdbc.update(
            "UPDATE t_agent_execution_steps " +
            "SET status = ?, started_at = ?, completed_at = ?, " +
            "duration_ms = ?, result_summary = ? WHERE id = ?",
            step.getStatus().name(),
            toTimestamp(step.getStartedAt()),
            toTimestamp(step.getCompletedAt()),
            step.getDurationMs(),
            step.getResultSummary(),
            step.getId()
        );
    }
    
    public List<ExecutionStep> findByRunId(String runId) {
        return jdbc.query(
            "SELECT * FROM t_agent_execution_steps WHERE run_id = ? ORDER BY create_time",
            (rs, i) -> ExecutionStep.builder()
                .id(rs.getString("id"))
                .runId(rs.getString("run_id"))
                .stepType(ExecutionStep.StepType.valueOf(rs.getString("step_type")))
                .stepName(rs.getString("step_name"))
                .status(ExecutionStep.StepStatus.valueOf(rs.getString("status")))
                .durationMs(rs.getLong("duration_ms"))
                .resultSummary(rs.getString("result_summary"))
                .positionX(rs.getDouble("position_x"))
                .positionY(rs.getDouble("position_y"))
                .build(),
            runId
        );
    }
    
    public Optional<ExecutionStep> findById(String id) {
        List<ExecutionStep> results = jdbc.query(
            "SELECT * FROM t_agent_execution_steps WHERE id = ?",
            (rs, i) -> ExecutionStep.builder()
                .id(rs.getString("id"))
                .runId(rs.getString("run_id"))
                .stepType(ExecutionStep.StepType.valueOf(rs.getString("step_type")))
                .stepName(rs.getString("step_name"))
                .status(ExecutionStep.StepStatus.valueOf(rs.getString("status")))
                .build(),
            id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
```

---

## 使用示例

```java
@Service
public class AgentExecutionService {
    
    @Autowired
    private WorkflowService workflowService;
    
    public void executeAgent(String runId, String query) {
        // 记录搜索步骤
        String stepId = workflowService.recordStep(
            runId, StepType.SEARCH, "搜索文档"
        );
        
        // 开始执行
        workflowService.startStep(stepId);
        
        // 执行搜索
        List<Document> docs = searchDocuments(query);
        
        // 完成步骤
        workflowService.completeStep(stepId, "找到 " + docs.size() + " 个文档");
    }
}
```

---

## 配置

### application.yml

```yaml
seahorse:
  agent:
    workflow:
      enabled: true
      sse-timeout: 1800000
```

---

## 总结

这份简化的后端设计：
- ✅ 使用 Lombok 简化代码
- ✅ 核心功能完整
- ✅ 易于理解和维护
- ✅ 约 200 行核心代码

**工作量**：2 人 × 3 天 = 6 人天

---

**文档版本**：v1.0  
**最后更新**：2026-06-02  
**维护者**：后端团队
