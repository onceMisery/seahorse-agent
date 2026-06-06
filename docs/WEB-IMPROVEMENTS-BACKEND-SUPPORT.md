# Web 功能改进 - 后端支持设计

版本：v1.0  
日期：2026-06-02  
作者：后端团队

---

## 概述

本文档补充 Web 功能改进方案中需要的后端支持，包括：
- 文件上传和管理 API
- 代码保存 API
- SSE 流式优化
- 安全考虑

---

## 1. 文件上传 API

### FileUploadController.java

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {
    
    private final FileStorageService fileStorageService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam("userId") String userId,
        @RequestParam("sessionId") String sessionId
    ) {
        String fileId = fileStorageService.store(file, userId, sessionId);
        return ResponseEntity.ok(Map.of(
            "fileId", fileId,
            "fileName", file.getOriginalFilename(),
            "fileSize", file.getSize()
        ));
    }
    
    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        Resource resource = fileStorageService.load(fileId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=\"" + resource.getFilename() + "\"")
            .body(resource);
    }
    
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        fileStorageService.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}
```

### FileStorageService.java

```java
@Service
@RequiredArgsConstructor
public class FileStorageService {
    
    private final StoragePort storagePort;
    
    @Value("${seahorse.agent.storage.max-file-size:10485760}")
    private long maxFileSize;
    
    public String store(MultipartFile file, String userId, String sessionId) {
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds limit");
        }
        
        String fileId = SnowflakeIds.nextIdString();
        String objectKey = String.format("user-files/%s/%s/%s", 
            userId, sessionId, fileId);
        
        try {
            storagePort.putObject(objectKey, 
                file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
        
        return fileId;
    }
}
```

---

## 2. 代码保存 API

### ArtifactController.java

```java
@RestController
@RequestMapping("/api/artifacts")
@RequiredArgsConstructor
public class ArtifactController {
    
    private final ArtifactService artifactService;
    
    @PostMapping("/{artifactId}/code")
    public ResponseEntity<Map<String, Object>> saveCode(
        @PathVariable String artifactId,
        @RequestBody CodeSaveRequest request
    ) {
        artifactService.saveCode(artifactId, 
            request.getContent(), request.getLanguage());
        return ResponseEntity.ok(Map.of("success", true));
    }
    
    @GetMapping("/{artifactId}/code")
    public ResponseEntity<Map<String, Object>> getCode(
        @PathVariable String artifactId
    ) {
        Artifact artifact = artifactService.getArtifact(artifactId);
        return ResponseEntity.ok(Map.of(
            "content", artifact.getContent(),
            "language", artifact.getLanguage()
        ));
    }
}
```

---

## 3. SSE 流式优化

```java
@Service
public class ChatStreamService {
    
    private static final int BATCH_SIZE = 10;
    private static final long BATCH_INTERVAL_MS = 50;
    
    public void streamResponse(String sessionId, SseEmitter emitter) {
        List<ChatMessage> buffer = new ArrayList<>();
        ScheduledExecutorService scheduler = 
            Executors.newSingleThreadScheduledExecutor();
        
        scheduler.scheduleAtFixedRate(() -> {
            if (!buffer.isEmpty()) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("message-batch")
                        .data(buffer));
                    buffer.clear();
                } catch (IOException e) {
                    scheduler.shutdown();
                }
            }
        }, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS, 
           TimeUnit.MILLISECONDS);
    }
}
```

---

## 4. 配置

### application.yml

```yaml
seahorse:
  agent:
    storage:
      max-file-size: 10485760  # 10MB
      allowed-types:
        - image/png
        - image/jpeg
        - application/pdf
        - text/plain
```

---

## 5. 安全考虑

```java
@Component
public class FileUploadSecurityFilter {
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    
    public String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9.-]", "_");
    }
    
    public boolean validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        List<String> allowedTypes = List.of(
            "image/png", "image/jpeg", "application/pdf", "text/plain"
        );
        return allowedTypes.contains(contentType);
    }
}
```

---

## 总结

后端需要实现：
- ✅ 文件上传和管理 API（2 人天）
- ✅ 代码保存 API（1 人天）
- ✅ SSE 流式优化（可选）

**总工作量**：3 人天

---

**文档版本**：v1.0  
**最后更新**：2026-06-02
