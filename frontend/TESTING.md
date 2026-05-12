# 前端本地验证指南

## API 代理

前端开发服务器通过 `vite.config.ts` 将 `/api` 请求代理到后端。

默认 API 前缀为 `/api/seahorse-agent`，配置位置为 `frontend/.env`：

```env
VITE_API_BASE_URL=/api/seahorse-agent
VITE_APP_NAME=Seahorse Agent
```

## 快速检查

1. 启动后端服务，确认 Seahorse Agent API 可访问：

```bash
curl http://localhost:8080/api/seahorse-agent/knowledge-base
```

未登录时返回 401 属于正常现象，说明请求已经到达后端。

2. 启动前端开发服务器：

```bash
cd frontend
npm run dev
```

3. 打开浏览器访问 `http://localhost:5173`，登录后进入聊天页或管理后台。

## 常见问题

- `No static resource /api/seahorse-agent/...`：通常是 Vite 代理未生效，重启前端开发服务器后再试。
- `401 Unauthorized`：未登录或 token 失效，重新登录即可。
- 端口被占用：Vite 会自动尝试下一个端口，也可以按终端输出访问实际端口。

## 路径说明

| 前端请求 | 代理目标 | 后端实际路径 |
| --- | --- | --- |
| `/api/seahorse-agent/knowledge-base` | `http://localhost:8080/api/seahorse-agent/knowledge-base` | 由后端 `context-path` 处理 |

## 管理员账号

如需手动设置管理员，可在数据库中更新用户角色：

```sql
UPDATE t_user SET role = 'admin' WHERE username = 'your_username';
```
