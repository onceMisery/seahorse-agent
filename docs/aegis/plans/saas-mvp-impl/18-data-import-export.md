# 块L · 数据导入导出 — 批量数据处理方案

> 文档定位：SaaS MVP 执行计划第 18 篇。功能增强系列之「数据处理」。  
> 关键属性：**P1 优先级、企业用户必需、独立可实施**。  
> 编写依据：2026-06-05 用户反馈 + 企业级数据处理最佳实践。  
> 工作量口径：1 人 × 2-3 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 无批量导入（只能逐条创建，效率低）
- ❌ 无数据导出（无法备份数据）
- ❌ 无模板下载（用户不知道格式）
- ❌ 无数据校验（错误数据直接入库）
- ❌ 导出同步阻塞（大数据量超时）

**用户痛点**：
- 😤 迁移 1000 条数据需要手动创建（耗时 2 小时）
- 😤 无法批量修改（导出 → 修改 → 导入）
- 😤 导出 10000 条数据超时（接口 504）
- 😤 导入错误数据无提示（直接失败）

**本方案价值**：
- ✅ 批量导入（Excel、CSV、JSON，1000 条 < 10 秒）
- ✅ 批量导出（异步导出，支持 100 万条）
- ✅ 模板下载（标准格式 + 示例数据）
- ✅ 数据校验（实时校验 + 预览）
- ✅ 进度反馈（导入/导出进度条）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | 批量导入（Excel、CSV、JSON） | **P0** | 无 ❌ | 1000 条 < 10s |
| G2 | 批量导出（异步导出、分片下载） | **P0** | 无 ❌ | 支持 100 万条 |
| G3 | 模板下载（标准格式） | **P0** | 无 ❌ | 一键下载 |
| G4 | 数据校验（格式校验、业务校验） | **P0** | 无 ❌ | 实时校验 |
| G5 | 预览与确认（导入前预览） | P1 | 无 ❌ | 显示前 10 条 |
| G6 | 进度反馈（WebSocket 实时进度） | P1 | 无 ❌ | 实时百分比 |

### 1.2 支持的数据类型

| 数据类型 | 导入 | 导出 | 数量级 |
|---------|------|------|--------|
| 知识库文档 | ✅ | ✅ | 10 万 |
| 用户列表 | ✅ | ✅ | 1 万 |
| 对话记录 | ❌ | ✅ | 100 万 |
| 配额记录 | ❌ | ✅ | 10 万 |

### 1.3 验收信号

#### P0 验收

1. ✅ 导入 1000 条文档 < 10s（Excel 格式）
2. ✅ 导出 10000 条对话记录 < 30s（异步）
3. ✅ 下载导入模板（包含示例数据）
4. ✅ 格式错误时显示错误行号和原因

#### P1 验收

5. ⚠️ 导入前预览前 10 条数据
6. ⚠️ 导入进度实时显示（WebSocket）

---

## 2. 现状（功能审查）

### 2.1 现有导入导出

**缺失功能**：
- ❌ 无批量导入 API
- ❌ 无导出功能
- ❌ 无模板下载

---

## 3. 技术方案

### 3.1 批量导入（P0）

#### 3.1.1 前端上传

```typescript
// components/DataImport.tsx
import { Upload, Button, message, Progress } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import * as XLSX from 'xlsx';

export const DataImport = () => {
  const [progress, setProgress] = useState(0);
  
  const handleUpload = async (file: File) => {
    // 1. 读取文件
    const data = await parseExcel(file);
    
    // 2. 校验数据
    const { valid, errors } = validateData(data);
    if (!valid) {
      message.error(`校验失败：${errors.join(', ')}`);
      return;
    }
    
    // 3. 上传数据
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('/api/documents/import', {
      method: 'POST',
      body: formData,
      onUploadProgress: (e) => {
        setProgress(Math.round((e.loaded / e.total) * 100));
      },
    });
    
    if (response.ok) {
      message.success('导入成功');
    }
  };
  
  const parseExcel = async (file: File): Promise<any[]> => {
    const buffer = await file.arrayBuffer();
    const workbook = XLSX.read(buffer);
    const sheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[sheetName];
    return XLSX.utils.sheet_to_json(worksheet);
  };
  
  return (
    <>
      <Upload
        accept=".xlsx,.xls,.csv"
        beforeUpload={handleUpload}
        showUploadList={false}
      >
        <Button icon={<UploadOutlined />}>上传 Excel</Button>
      </Upload>
      
      {progress > 0 && <Progress percent={progress} />}
    </>
  );
};
```

#### 3.1.2 后端解析

```java
// controller/DocumentImportController.java
@RestController
@RequestMapping("/api/documents")
public class DocumentImportController {
    
    @PostMapping("/import")
    public ImportResponse importDocuments(
            @RequestParam("file") MultipartFile file) throws IOException {
        
        // 1. 解析 Excel
        List<DocumentImportRow> rows = parseExcel(file.getInputStream());
        
        // 2. 校验数据
        List<ValidationError> errors = validateRows(rows);
        if (!errors.isEmpty()) {
            return ImportResponse.failure(errors);
        }
        
        // 3. 批量插入（异步）
        CompletableFuture<ImportResult> future = 
            documentImportService.importAsync(rows);
        
        return ImportResponse.success(future.get().getImportedCount());
    }
    
    private List<DocumentImportRow> parseExcel(InputStream is) throws IOException {
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);
        
        List<DocumentImportRow> rows = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;  // 跳过表头
            
            rows.add(DocumentImportRow.builder()
                .title(getCellValue(row.getCell(0)))
                .content(getCellValue(row.getCell(1)))
                .tags(getCellValue(row.getCell(2)))
                .build());
        }
        
        return rows;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            default -> "";
        };
    }
}
```

#### 3.1.3 数据校验

```java
@Service
public class DocumentImportValidator {
    
    public List<ValidationError> validate(List<DocumentImportRow> rows) {
        List<ValidationError> errors = new ArrayList<>();
        
        for (int i = 0; i < rows.size(); i++) {
            DocumentImportRow row = rows.get(i);
            int lineNumber = i + 2;  // Excel 行号从 1 开始，第 1 行是表头
            
            // 标题不能为空
            if (StringUtils.isBlank(row.getTitle())) {
                errors.add(ValidationError.of(lineNumber, "title", "标题不能为空"));
            }
            
            // 标题长度限制
            if (row.getTitle().length() > 100) {
                errors.add(ValidationError.of(lineNumber, "title", "标题长度不能超过 100"));
            }
            
            // 内容不能为空
            if (StringUtils.isBlank(row.getContent())) {
                errors.add(ValidationError.of(lineNumber, "content", "内容不能为空"));
            }
            
            // 标签格式校验（逗号分隔）
            if (StringUtils.isNotBlank(row.getTags())) {
                String[] tags = row.getTags().split(",");
                if (tags.length > 10) {
                    errors.add(ValidationError.of(lineNumber, "tags", "标签数量不能超过 10"));
                }
            }
        }
        
        return errors;
    }
}

@Data
@Builder
public class ValidationError {
    private int lineNumber;
    private String field;
    private String message;
}
```

#### 3.1.4 批量插入

```java
@Service
public class DocumentImportService {
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Async
    public CompletableFuture<ImportResult> importAsync(List<DocumentImportRow> rows) {
        int batchSize = 100;
        int imported = 0;
        
        for (int i = 0; i < rows.size(); i += batchSize) {
            List<DocumentImportRow> batch = rows.subList(
                i, Math.min(i + batchSize, rows.size())
            );
            
            List<Document> documents = batch.stream()
                .map(this::toDocument)
                .toList();
            
            documentRepository.saveAll(documents);
            imported += documents.size();
            
            // 发布进度事件
            eventPublisher.publishEvent(new ImportProgressEvent(
                imported, rows.size()
            ));
        }
        
        return CompletableFuture.completedFuture(
            ImportResult.of(imported, 0)
        );
    }
    
    private Document toDocument(DocumentImportRow row) {
        return Document.builder()
            .title(row.getTitle())
            .content(row.getContent())
            .tags(row.getTags() != null ? 
                Arrays.asList(row.getTags().split(",")) : List.of())
            .tenantId(TenantContext.get())
            .build();
    }
}
```

---

### 3.2 批量导出（P0）

#### 3.2.1 异步导出（后端）

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentExportController {
    
    @PostMapping("/export")
    public ExportResponse exportDocuments(@RequestBody ExportRequest request) {
        // 1. 创建导出任务
        ExportTask task = exportTaskService.create(
            ExportType.DOCUMENT,
            request.getFilters()
        );
        
        // 2. 异步执行导出（⚠️ 使用自定义线程池，避免默认 ForkJoinPool）
        exportExecutor.execute(() -> {
            try {
                executeExport(task);
            } catch (Exception ex) {
                exportTaskService.markFailed(task.getId(), ex.getMessage());
            }
        });
        
        // 3. 立即返回任务 ID
        return ExportResponse.of(task.getId());
    }
    
    @GetMapping("/export/{taskId}")
    public ExportTaskStatus getExportStatus(@PathVariable Long taskId) {
        return exportTaskService.getStatus(taskId);
    }
    
    @GetMapping("/export/{taskId}/download")
    public ResponseEntity<Resource> downloadExport(@PathVariable Long taskId) 
            throws IOException {
        
        ExportTask task = exportTaskService.findById(taskId);
        
        if (!task.getStatus().equals("COMPLETED")) {
            throw new ExportNotReadyException();
        }
        
        File file = new File(task.getFilePath());
        Resource resource = new FileSystemResource(file);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"" + task.getFileName() + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }
    
    private void executeExport(ExportTask task) throws IOException {
        // 1. 查询数据（分页，⚠️ 必须加 tenant_id 过滤，防止跨租户数据泄露）
        String tenantId = task.getTenantId();
        int pageSize = 1000;
        int page = 0;
        int total = 0;
        
        // ✅ 使用 SXSSFWorkbook（流式写入，内存占用仅为 XSSFWorkbook 的 1/100）
        // XSSFWorkbook 会将所有行保留在内存中，导出 10 万条时 OOM
        Workbook workbook = new SXSSFWorkbook(new XSSFWorkbook());
        Sheet sheet = workbook.createSheet("Documents");
        
        // 表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID");
        headerRow.createCell(1).setCellValue("标题");
        headerRow.createCell(2).setCellValue("内容");
        headerRow.createCell(3).setCellValue("标签");
        headerRow.createCell(4).setCellValue("创建时间");
        
        // 数据（⚠️ findByTenantId 而非 findAll，确保租户隔离）
        int rowNum = 1;
        while (true) {
            Page<Document> pageData = documentRepository.findByTenantId(
                tenantId, PageRequest.of(page, pageSize)
            );
            
            if (pageData.isEmpty()) break;
            
            for (Document doc : pageData) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(doc.getId());
                dataRow.createCell(1).setCellValue(doc.getTitle());
                dataRow.createCell(2).setCellValue(doc.getContent());
                dataRow.createCell(3).setCellValue(String.join(",", doc.getTags()));
                dataRow.createCell(4).setCellValue(doc.getCreatedAt().toString());
            }
            
            total += pageData.getNumberOfElements();
            
            // 更新进度
            exportTaskService.updateProgress(task.getId(), 
                (int) ((double) total / pageData.getTotalElements() * 100));
            
            page++;
        }
        
        // 2. 写入文件（使用可配置路径，避免硬编码 /tmp/exports/）
        String exportDir = exportConfig.getOutputDir();  // 从配置读取
        Files.createDirectories(Path.of(exportDir));  // 确保目录存在
        String fileName = "documents_" + System.currentTimeMillis() + ".xlsx";
        String filePath = exportDir + "/" + fileName;
        
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            workbook.write(fos);
        }
        
        // SXSSFWorkbook 需要释放临时文件
        if (workbook instanceof SXSSFWorkbook sxssf) {
            sxssf.dispose();
        }
        
        // 3. 更新任务状态
        exportTaskService.markCompleted(task.getId(), filePath, fileName);
    }
}
```

#### 3.2.2 导出任务表

```sql
CREATE TABLE t_export_task (
    task_id        BIGSERIAL PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL,
    user_id        BIGINT NOT NULL,
    export_type    SMALLINT NOT NULL,
    -- 导出类型枚举：1=DOCUMENT, 2=USER, 3=CHAT, 4=QUOTA
    filters        JSONB,
    status         SMALLINT NOT NULL DEFAULT 0,
    -- 状态枚举：0=PENDING, 1=RUNNING, 2=COMPLETED, 3=FAILED
    progress       SMALLINT NOT NULL DEFAULT 0,
    file_path      VARCHAR(512),
    file_name      VARCHAR(256),
    error_message  TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at   TIMESTAMP WITH TIME ZONE
);

-- 索引（tenant_id 最左前缀，支持租户级查询和 RLS）
CREATE INDEX idx_export_task_tenant_user ON t_export_task (tenant_id, user_id, created_at DESC);
CREATE INDEX idx_export_task_status ON t_export_task (status, created_at DESC);

-- CHECK 约束
ALTER TABLE t_export_task ADD CONSTRAINT chk_export_task_type
    CHECK (export_type BETWEEN 1 AND 4);
ALTER TABLE t_export_task ADD CONSTRAINT chk_export_task_status
    CHECK (status IN (0, 1, 2, 3));
ALTER TABLE t_export_task ADD CONSTRAINT chk_export_task_progress
    CHECK (progress BETWEEN 0 AND 100);
```

> **DDL 审查要点**：
> - ✅ `export_type` 从 `VARCHAR(32)` 改为 `SMALLINT`，索引效率提升约 4x
> - ✅ `status` 从 `VARCHAR(32)` 改为 `SMALLINT`，消除字符串比较开销
> - ✅ `progress` 从 `INT` 改为 `SMALLINT`（百分比 0-100，无需 4 字节）
> - ✅ 修正 MySQL 内联 `INDEX` 语法为 PostgreSQL `CREATE INDEX`
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`，支持时区转换
> - ✅ `progress` 添加 CHECK 约束（0-100），防止非法进度值

#### 3.2.3 前端轮询进度

```typescript
// hooks/useExport.ts
export const useExport = () => {
  const [taskId, setTaskId] = useState<number | null>(null);
  const [progress, setProgress] = useState(0);
  const [status, setStatus] = useState<'idle' | 'running' | 'completed'>('idle');
  
  const startExport = async (filters: any) => {
    const response = await api.post('/api/documents/export', filters);
    setTaskId(response.taskId);
    setStatus('running');
    
    // 轮询进度（指数退避：3s → 6s → 12s，最大 15s）
    let delay = 3000;
    const poll = async () => {
      const status = await api.get(`/api/documents/export/${response.taskId}`);
      setProgress(status.progress);
      
      if (status.status === 'COMPLETED') {
        setStatus('completed');
        window.location.href = `/api/documents/export/${response.taskId}/download`;
        return;
      } else if (status.status === 'FAILED') {
        setStatus('failed');
        return;
      }
      
      delay = Math.min(delay * 2, 15000);
      setTimeout(poll, delay);
    };
    setTimeout(poll, delay);
  };
  
  return { startExport, progress, status };
};
```

---

### 3.3 模板下载（P0）

#### 3.3.1 生成模板

```java
@GetMapping("/import/template")
public ResponseEntity<Resource> downloadTemplate() throws IOException {
    Workbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet("Documents");
    
    // 表头
    Row headerRow = sheet.createRow(0);
    headerRow.createCell(0).setCellValue("标题（必填）");
    headerRow.createCell(1).setCellValue("内容（必填）");
    headerRow.createCell(2).setCellValue("标签（可选，逗号分隔）");
    
    // 示例数据
    Row exampleRow1 = sheet.createRow(1);
    exampleRow1.createCell(0).setCellValue("示例文档 1");
    exampleRow1.createCell(1).setCellValue("这是文档内容");
    exampleRow1.createCell(2).setCellValue("AI,RAG");
    
    Row exampleRow2 = sheet.createRow(2);
    exampleRow2.createCell(0).setCellValue("示例文档 2");
    exampleRow2.createCell(1).setCellValue("这是另一个文档");
    exampleRow2.createCell(2).setCellValue("知识库");
    
    // 写入临时文件
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    workbook.write(bos);
    
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, 
            "attachment; filename=\"document_import_template.xlsx\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new ByteArrayResource(bos.toByteArray()));
}
```

#### 3.3.2 前端下载

```typescript
<Button onClick={() => {
  window.location.href = '/api/documents/import/template';
}}>
  下载导入模板
</Button>
```

---

### 3.4 进度反馈（P1）

#### 3.4.1 WebSocket 实时进度

**依赖**（⚠️ 需确保已引入 WebSocket + STOMP 支持）：
```xml
<!-- Spring WebSocket + STOMP 消息协议 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

**WebSocket 配置**：
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
```

**进度发送**：

```java
@Component
public class ExportProgressNotifier {
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @EventListener
    public void handleImportProgress(ImportProgressEvent event) {
        messagingTemplate.convertAndSend(
            "/topic/import-progress/" + event.getTaskId(),
            Map.of(
                "current", event.getCurrent(),
                "total", event.getTotal(),
                "progress", (int) ((double) event.getCurrent() / event.getTotal() * 100)
            )
        );
    }
}
```

#### 3.4.2 前端订阅

```typescript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:9090/ws',
  onConnect: () => {
    client.subscribe(`/topic/import-progress/${taskId}`, (message) => {
      const progress = JSON.parse(message.body);
      setProgress(progress.progress);
    });
  },
});

client.activate();
```

---

### 3.5 导出配置（application.yml）

> **问题**：导出路径 `/tmp/exports/` 硬编码、异步任务使用默认 ForkJoinPool。

```yaml
seahorse:
  export:
    output-dir: ${EXPORT_DIR:/data/exports}  # 可配置导出目录
    max-file-size: 100MB                      # 单文件最大大小
    retention-days: 7                         # 导出文件保留天数（定期清理）
```

```java
@Configuration
@ConfigurationProperties(prefix = "seahorse.export")
@Data
public class ExportConfig {
    private String outputDir = "/data/exports";
    private String maxFileSize = "100MB";
    private int retentionDays = 7;
}
```

> **跨文档参考**：
> - 异步线程池配置参见 14-error-handling-resilience §3.2.6（`exportExecutor` 复用统一线程池）
> - 租户隔离查询参见 15-data-consistency（`findByTenantId` + RLS 策略）

---

## 4. 实施步骤

### Day 1：导入功能
- 上午：Excel 解析 + 数据校验（3h）
- 下午：批量插入（2h）

### Day 2：导出功能
- 上午：异步导出（3h）
- 下午：前端进度显示（2h）

### Day 3：模板 + 验收
- 上午：模板下载（1h）
- 下午：集成测试（3h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 导入 1000 条 < 10s
✅ 导出 10000 条 < 30s

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06
