import * as React from "react";
import { Eye, EyeOff, Lock, User } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useAuthStore } from "@/stores/authStore";

export function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuthStore();
  const [showPassword, setShowPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({ username: "admin", password: "admin" });
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    try {
      await login(form.username.trim(), form.password.trim());
      if (!remember) {
        // 如需仅在内存中保存登录态，可在此扩展。
      }
      navigate("/chat");
    } catch (err) {
      setError((err as Error).message || "登录失败，请稍后重试。");
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center px-4">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-grid-pattern opacity-20 [background-size:40px_40px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-32 right-[-40px] h-72 w-72 rounded-full blur-3xl animate-float"
        style={{ background: "radial-gradient(var(--theme-accent-alpha-20), transparent 70%)" }}
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-36 left-[-80px] h-80 w-80 rounded-full blur-3xl animate-float"
        style={{ background: "radial-gradient(var(--theme-accent-alpha-10), transparent 70%)", animationDelay: "3s" }}
      />

      <div className="relative z-10 w-full max-w-md">
        {/* Logo */}
        <div className="flex flex-col items-center text-center mb-8">
          <div className="relative mb-6">
            <div className="absolute inset-0 rounded-full blur-3xl scale-110" style={{ backgroundColor: "var(--theme-accent-alpha-20)" }} />
            <div className="relative animate-float">
              <img
                src="/seahorse-logo.png"
                alt="Seahorse Agent"
                className="w-[140px] h-[140px] object-contain drop-shadow-[0_0_30px_rgba(6,182,212,0.4)]"
              />
            </div>
          </div>
          <h1 className="font-display text-3xl font-bold tracking-tight glow-text" style={{ color: "var(--theme-text-primary)" }}>
            Seahorse Agent
          </h1>
          <p className="mt-2 text-sm" style={{ color: "var(--theme-accent)" }}>
            RAG 智能问答助手
          </p>
        </div>

        <div className="glass glow-border rounded-3xl p-8">
          <div className="mb-6">
            <p className="font-display text-2xl font-semibold" style={{ color: "var(--theme-text-primary)" }}>欢迎回来</p>
            <p className="mt-1 text-sm" style={{ color: "var(--theme-text-muted)" }}>
              登录后继续你的检索增强对话。
            </p>
          </div>
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-xs font-semibold uppercase tracking-wide" style={{ color: "var(--theme-text-muted)" }}>
                用户名
              </label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2" style={{ color: "var(--theme-text-muted)" }} />
                <Input
                  placeholder="请输入用户名"
                  value={form.username}
                  onChange={(event) => setForm((prev) => ({ ...prev, username: event.target.value }))}
                  className="pl-10"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    borderColor: "var(--theme-glass-border)",
                    color: "var(--theme-text-primary)"
                  }}
                  autoComplete="username"
                />
              </div>
            </div>
            <div className="space-y-2">
              <label className="text-xs font-semibold uppercase tracking-wide" style={{ color: "var(--theme-text-muted)" }}>
                密码
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2" style={{ color: "var(--theme-text-muted)" }} />
                <Input
                  type={showPassword ? "text" : "password"}
                  placeholder="请输入密码"
                  value={form.password}
                  onChange={(event) => setForm((prev) => ({ ...prev, password: event.target.value }))}
                  className="pl-10 pr-10"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    borderColor: "var(--theme-glass-border)",
                    color: "var(--theme-text-primary)"
                  }}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-3 top-1/2 -translate-y-1/2"
                  style={{ color: "var(--theme-text-muted)" }}
                  aria-label="显示或隐藏密码"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>
            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center gap-2" style={{ color: "var(--theme-text-muted)" }}>
                <Checkbox checked={remember} onCheckedChange={(value) => setRemember(Boolean(value))} />
                记住我
              </label>
              <span className="text-xs" style={{ color: "var(--theme-text-muted)" }}>账号由管理员初始化</span>
            </div>
            {error ? <p className="text-sm text-rose-400">{error}</p> : null}
            <Button
              type="submit"
              className="w-full rounded-xl py-6 text-base font-semibold"
              disabled={isLoading}
              style={{
                background: "var(--theme-gradient)",
                color: "var(--theme-bg-deep)",
                boxShadow: "0 0 20px var(--theme-accent-alpha-30)"
              }}
            >
              {isLoading ? "正在登录..." : "登录"}
            </Button>
          </form>
          <p className="mt-4 text-center text-xs" style={{ color: "var(--theme-text-muted)" }}>
            默认账号: admin / admin
          </p>
        </div>
      </div>
    </div>
  );
}
