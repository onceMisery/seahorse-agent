# 块N · 前端状态管理增强 — Zustand 全局状态方案

> 文档定位：SaaS MVP 执行计划第 13 篇。功能增强系列之「状态管理」。  
> 关键属性：**P2 优先级、架构优化、独立可实施**。  
> 编写依据：2026-06-05 状态管理痛点 + Zustand 最佳实践。  
> 工作量口径：1 人 × 2 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 状态管理混乱（useState 散落各处）
- ❌ Props 钻取严重（层级 > 3 层）
- ❌ 状态不持久（刷新页面丢失）
- ❌ 跨组件通信困难（需要提升状态）
- ❌ 无全局状态（用户信息重复请求）

**用户痛点**：
- 😤 切换路由后状态丢失（输入的内容没了）
- 😤 登录状态刷新后丢失（需要重新登录）
- 😤 组件间传参复杂（props 层层传递）

**本方案价值**：
- ✅ 统一状态管理（Zustand，比 Redux 简单）
- ✅ 状态持久化（localStorage 自动同步）
- ✅ 跨组件通信（无需 props 传递）
- ✅ 类型安全（TypeScript 全支持）
- ✅ DevTools 调试（实时查看状态变化）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | Zustand 状态管理 | **P0** | useState ⚠️ | 全局状态 |
| G2 | 状态持久化 | **P0** | 无 ❌ | localStorage |
| G3 | 中间件（日志、性能） | P1 | 无 ❌ | 开发调试 |
| G4 | 状态分片（多 store） | P1 | 单一 ❌ | 按模块分离 |
| G5 | DevTools 集成 | P1 | 无 ❌ | Chrome 插件 |
| G6 | 状态重置（登出时） | **P0** | 手动 ⚠️ | 自动重置 |

### 1.2 管理的状态

| 状态类型 | 优先级 | 持久化 | 示例 |
|---------|--------|--------|------|
| 用户信息 | **P0** | ✅ | 用户名、头像、权限 |
| 登录状态 | **P0** | ✅ | token、refreshToken |
| 租户信息 | **P0** | ✅ | tenantId、配额 |
| 主题设置 | P1 | ✅ | 深色/浅色模式 |
| 语言偏好 | P1 | ✅ | zh-CN / en-US |
| 对话历史 | P1 | ❌ | 当前对话内容 |

### 1.3 验收信号

#### P0 验收

1. ✅ 用户信息全局可访问（任意组件）
2. ✅ 刷新页面后状态保持（token 不丢失）
3. ✅ 登出后状态自动清空
4. ✅ 类型安全（TypeScript 提示完整）

#### P1 验收

5. ⚠️ DevTools 实时显示状态变化
6. ⚠️ 状态更新日志（开发环境）

---

## 2. 现状（状态管理审查）

### 2.1 现有问题

**问题 1：Props 钻取**
```typescript
// ❌ props 层层传递
<Dashboard user={user}>
  <Header user={user}>
    <UserMenu user={user} />
  </Header>
</Dashboard>
```

**问题 2：状态不持久**
```typescript
// ❌ 刷新后丢失
const [user, setUser] = useState<User | null>(null);
```

**问题 3：重复请求**
```typescript
// ❌ 每个组件都请求一次
useEffect(() => {
  fetchCurrentUser().then(setUser);
}, []);
```

---

## 3. 技术方案

### 3.1 Zustand 状态管理（P0）

#### 3.1.1 安装依赖

```bash
npm install zustand
```

#### 3.1.2 创建用户状态 Store

```typescript
// stores/useUserStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface User {
  id: number;
  username: string;
  email: string;
  avatar?: string;
  roles: string[];
}

interface UserState {
  // 状态
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  
  // 操作
  setUser: (user: User) => void;
  setToken: (token: string) => void;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
}

export const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      // 初始状态
      user: null,
      token: null,
      isAuthenticated: false,
      
      // 设置用户
      setUser: (user) => set({ user, isAuthenticated: true }),
      
      // 设置 token
      setToken: (token) => set({ token }),
      
      // 登录
      login: async (username, password) => {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
          headers: { 'Content-Type': 'application/json' },
        });
        
        const data = await response.json();
        
        set({
          user: data.user,
          token: data.token,
          isAuthenticated: true,
        });
      },
      
      // 登出
      logout: () => {
        set({
          user: null,
          token: null,
          isAuthenticated: false,
        });
      },
      
      // 刷新 token
      refreshToken: async () => {
        const { token } = get();
        
        const response = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        });
        
        const data = await response.json();
        set({ token: data.token });
      },
    }),
    {
      name: 'user-storage',  // localStorage key
      partialize: (state) => ({
        // 只持久化这些字段
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
```

#### 3.1.3 使用 Store

```typescript
// components/Header.tsx
import { useUserStore } from '@/stores/useUserStore';

export const Header = () => {
  const user = useUserStore((state) => state.user);
  const logout = useUserStore((state) => state.logout);
  
  return (
    <div>
      <span>欢迎，{user?.username}</span>
      <Button onClick={logout}>登出</Button>
    </div>
  );
};
```

**优化：选择性订阅**
```typescript
// ✅ 只订阅 user，token 变化不会触发重渲染
const user = useUserStore((state) => state.user);

// ❌ 订阅整个 store，任何变化都会重渲染
const { user, token } = useUserStore();
```

---

### 3.2 租户状态 Store

```typescript
// stores/useTenantStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface Tenant {
  id: string;
  name: string;
  quota: {
    storage: number;
    storageUsed: number;
    tokens: number;
    tokensUsed: number;
  };
}

interface TenantState {
  tenant: Tenant | null;
  setTenant: (tenant: Tenant) => void;
  updateQuota: (quota: Partial<Tenant['quota']>) => void;
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set) => ({
      tenant: null,
      
      setTenant: (tenant) => set({ tenant }),
      
      updateQuota: (quota) =>
        set((state) => ({
          tenant: state.tenant
            ? { ...state.tenant, quota: { ...state.tenant.quota, ...quota } }
            : null,
        })),
    }),
    {
      name: 'tenant-storage',
    }
  )
);
```

---

### 3.3 UI 设置 Store

```typescript
// stores/useSettingsStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface SettingsState {
  theme: 'light' | 'dark';
  language: 'zh-CN' | 'en-US';
  sidebarCollapsed: boolean;
  
  setTheme: (theme: 'light' | 'dark') => void;
  setLanguage: (language: 'zh-CN' | 'en-US') => void;
  toggleSidebar: () => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'light',
      language: 'zh-CN',
      sidebarCollapsed: false,
      
      setTheme: (theme) => set({ theme }),
      setLanguage: (language) => set({ language }),
      toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
    }),
    {
      name: 'settings-storage',
    }
  )
);
```

---

### 3.4 中间件（P1）

#### 3.4.1 日志中间件

```typescript
// stores/middleware/logger.ts
import { StateCreator, StoreMutatorIdentifier } from 'zustand';

type Logger = <
  T,
  Mps extends [StoreMutatorIdentifier, unknown][] = [],
  Mcs extends [StoreMutatorIdentifier, unknown][] = []
>(
  f: StateCreator<T, Mps, Mcs>,
  name?: string
) => StateCreator<T, Mps, Mcs>;

export const logger: Logger = (f, name) => (set, get, store) => {
  const loggedSet: typeof set = (...args) => {
    console.group(`[${name || 'Store'}] Update`);
    console.log('Previous State:', get());
    set(...args);
    console.log('Next State:', get());
    console.groupEnd();
  };
  
  return f(loggedSet, get, store);
};
```

**使用**：
```typescript
export const useUserStore = create<UserState>()(
  logger(
    persist(
      (set, get) => ({ /* ... */ }),
      { name: 'user-storage' }
    ),
    'UserStore'
  )
);
```

#### 3.4.2 性能监控中间件

```typescript
// stores/middleware/performance.ts
export const performance = (f, name) => (set, get, store) => {
  const perfSet: typeof set = (...args) => {
    const start = Date.now();
    set(...args);
    const end = Date.now();
    
    if (end - start > 16) {  // > 1 帧（16ms）
      console.warn(`[${name}] Slow state update: ${end - start}ms`);
    }
  };
  
  return f(perfSet, get, store);
};
```

---

### 3.5 DevTools 集成（P1）

```typescript
// stores/useUserStore.ts
import { devtools } from 'zustand/middleware';

export const useUserStore = create<UserState>()(
  devtools(
    persist(
      (set, get) => ({ /* ... */ }),
      { name: 'user-storage' }
    ),
    { name: 'UserStore' }
  )
);
```

**安装 Redux DevTools 扩展**：
- Chrome: [Redux DevTools](https://chrome.google.com/webstore/detail/redux-devtools/)
- 查看状态变化：F12 → Redux 标签

---

### 3.6 状态重置（P0）

> **架构决策**：不在 `useUserStore.logout` 中直接引用其他 store（造成紧耦合）。
> 使用事件总线模式：各 store 监听 `app:logout` 事件，自行清理。

```typescript
// utils/eventBus.ts
type Listener = () => void;
const listeners = new Map<string, Set<Listener>>();

export const eventBus = {
  on: (event: string, listener: Listener) => {
    if (!listeners.has(event)) listeners.set(event, new Set());
    listeners.get(event)!.add(listener);
    return () => listeners.get(event)?.delete(listener);
  },
  emit: (event: string) => {
    listeners.get(event)?.forEach(listener => listener());
  },
};
```

**各 store 自行注册清理逻辑**：
```typescript
// stores/useUserStore.ts
export const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      // ...
      
      logout: () => {
        // 只清理当前 store
        set({
          user: null,
          token: null,
          isAuthenticated: false,
        });
        
        // 发布登出事件（其他 store 自行监听清理）
        eventBus.emit('app:logout');
        
        // 清除 localStorage
        localStorage.removeItem('user-storage');
      },
    }),
    { name: 'user-storage' }
  )
);

// stores/useTenantStore.ts — 在 store 创建时注册监听
const unsubLogout = eventBus.on('app:logout', () => {
  useTenantStore.setState({ tenant: null });
  localStorage.removeItem('tenant-storage');
});

// stores/useSettingsStore.ts — 在 store 创建时注册监听
eventBus.on('app:logout', () => {
  useSettingsStore.setState({ theme: 'light', language: 'zh-CN' });
  localStorage.removeItem('settings-storage');
});
```

**全局重置工具**：
```typescript
// utils/resetStores.ts
export const resetAllStores = () => {
  // 发布登出事件，各 store 自行清理
  eventBus.emit('app:logout');
};
```

---

### 3.7 状态分片（P1）

**按模块拆分 store**：
```
stores/
├── useUserStore.ts       # 用户相关
├── useTenantStore.ts     # 租户相关
├── useSettingsStore.ts   # 设置相关
├── useChatStore.ts       # 对话相关（不持久化）
└── useKBStore.ts         # 知识库相关
```

**原则**：
- 独立领域独立 store（不要一个大 store）
- 需要持久化的 store 使用 `persist` 中间件
- 临时状态（如对话输入框）不需要 store，用 `useState`

---

### 3.8 TypeScript 类型支持

```typescript
// stores/types.ts
export interface User {
  id: number;
  username: string;
  email: string;
  avatar?: string;
  roles: string[];
}

export interface Tenant {
  id: string;
  name: string;
  quota: {
    storage: number;
    storageUsed: number;
    tokens: number;
    tokensUsed: number;
  };
}

// 导出 store 类型（用于非组件场景）
export type UserStore = ReturnType<typeof useUserStore.getState>;
```

**在非组件中使用**：
```typescript
// utils/api.ts
import { useUserStore } from '@/stores/useUserStore';

export const fetchWithAuth = async (url: string) => {
  const token = useUserStore.getState().token;
  
  // ✅ token 为空时拦截，避免发送无认证请求
  if (!token) {
    // 跳转登录页
    window.location.href = '/login';
    throw new Error('Unauthorized: token is null');
  }
  
  return fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
};
```

---

### 3.9 与 React Query 集成

```typescript
// hooks/useCurrentUser.ts
import { useQuery } from '@tanstack/react-query';
import { useUserStore } from '@/stores/useUserStore';
import { useEffect } from 'react';

export const useCurrentUser = () => {
  const setUser = useUserStore((state) => state.setUser);
  
  const { data, isLoading } = useQuery({
    queryKey: ['currentUser'],
    queryFn: async () => {
      const response = await fetch('/api/user/current');
      return response.json();
    },
    // ✅ React Query v5 已废弃 onSuccess，改用 useEffect 同步状态
  });
  
  // 将服务端数据同步到 Zustand store
  useEffect(() => {
    if (data) {
      setUser(data);
    }
  }, [data, setUser]);
  
  return { user: data, isLoading };
};
```

> **注意**：React Query v5 已废弃 `onSuccess`/`onError` 回调。
> 推荐使用 `useEffect` 监听 `data`/`error` 变化来同步状态。

**职责划分**：
- **React Query**：服务端状态（API 数据、缓存、重试）
- **Zustand**：客户端状态（UI 设置、全局共享数据）

---

## 4. 实施步骤

### Day 1：核心 Store
- 上午：创建 useUserStore + useTenantStore（3h）
- 下午：状态持久化 + 类型定义（2h）

### Day 2：中间件 + DevTools
- 上午：日志中间件 + DevTools 集成（2h）
- 下午：迁移现有 useState → Zustand（3h）

---

## 5. 迁移指南

### 5.1 从 useState 迁移

**迁移前**：
```typescript
const [user, setUser] = useState<User | null>(null);

// props 传递
<Header user={user} onLogout={() => setUser(null)} />
```

**迁移后**：
```typescript
// 无需 props
<Header />

// Header.tsx
const user = useUserStore((state) => state.user);
const logout = useUserStore((state) => state.logout);
```

---

## 6. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 所有全局状态迁移到 Zustand
✅ 刷新页面后状态保持

---

## 7. 最佳实践

### 7.1 性能优化

```typescript
// ✅ 选择性订阅（只订阅需要的字段）
const username = useUserStore((state) => state.user?.username);

// ❌ 订阅整个对象（任何字段变化都会重渲染）
const user = useUserStore((state) => state.user);
```

### 7.2 避免过度使用

```typescript
// ❌ 不要把所有 state 都放 store
const [inputValue, setInputValue] = useState('');  // 局部状态，用 useState

// ✅ 只放全局共享的状态
const user = useUserStore((state) => state.user);
```

### 7.3 命名规范

```typescript
// Store 命名：use{Domain}Store
useUserStore
useTenantStore
useSettingsStore

// Action 命名：动词开头
setUser
login
logout
updateQuota
```

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06  
**修订说明**：store 解耦（事件总线模式）；fetchWithAuth token 空值拦截；React Query v5 onSuccess→useEffect
