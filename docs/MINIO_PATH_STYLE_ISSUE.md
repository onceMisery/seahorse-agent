## MinIO 问题终极诊断

当前状态：
- ✅ MinIO 运行正常
- ✅ Bucket `seahorse` 已创建
- ✅ 测试文件可以上传
- ✅ 后端配置正确
- ❌ 后端上传仍报 NoSuchBucketException

### 可能的原因

AWS SDK 默认使用 **虚拟主机样式**（virtual-hosted-style）访问：
```
http://seahorse.minio:9000/file.txt  ❌ 无法解析域名
```

而 MinIO 在 Docker 环境下应该使用 **路径样式**（path-style）：
```
http://minio:9000/seahorse/file.txt  ✅ 正确
```

### 解决方案

需要在后端配置中添加路径样式参数。检查 S3 适配器代码是否设置了：
```java
S3ClientBuilder builder = S3Client.builder()
    .endpointOverride(URI.create("http://minio:9000"))
    .region(Region.US_EAST_1)
    .credentialsProvider(credentials)
    .forcePathStyle(true);  // 关键：强制使用路径样式
```

### 快速验证

从后端容器内测试（如果可以进入容器）：
```bash
# 测试虚拟主机样式（可能失败）
curl -v http://seahorse.minio:9000/

# 测试路径样式（应该成功）
curl -v http://minio:9000/seahorse/
```

### 临时解决方案

如果代码无法修改，可以：
1. 使用本地存储（已测试可用）
2. 等待 MinIO 配置修复
3. 检查 `S3ObjectStorageAdapter.java` 中是否有 `forcePathStyle` 配置
