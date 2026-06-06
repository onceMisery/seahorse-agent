import * as React from "react";
import { Eye, EyeOff, Lock, User } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { useAuthStore } from "@/stores/authStore";
import { SeahorseLogo } from "@/components/common/SeahorseLogo";

const BUBBLES = [
  { size: 10, left: "7%", delay: "0s", dur: "9s" },
  { size: 18, left: "16%", delay: "1.8s", dur: "12s" },
  { size: 7, left: "28%", delay: "3.2s", dur: "7s" },
  { size: 14, left: "43%", delay: "0.6s", dur: "14s" },
  { size: 22, left: "58%", delay: "2.5s", dur: "10s" },
  { size: 9, left: "70%", delay: "4.1s", dur: "11s" },
  { size: 16, left: "80%", delay: "1.2s", dur: "13s" },
  { size: 5, left: "91%", delay: "3.8s", dur: "8s" },
  { size: 12, left: "52%", delay: "5.2s", dur: "15s" },
  { size: 20, left: "23%", delay: "2.1s", dur: "11s" }
];

export function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading } = useAuthStore();
  const [showPassword, setShowPassword] = React.useState(false);
  const [remember, setRemember] = React.useState(true);
  const [form, setForm] = React.useState({ username: "admin", password: "admin123" });
  const [error, setError] = React.useState<string | null>(null);
  const [focusedField, setFocusedField] = React.useState<string | null>(null);

  React.useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const reason = params.get("reason");
    if (reason) {
      setError(reason);
    }
  }, []);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!form.username.trim() || !form.password.trim()) {
      setError("请输入用户名和密码。");
      return;
    }
    try {
      await login(form.username.trim(), form.password.trim());
      const params = new URLSearchParams(window.location.search);
      navigate(params.get("redirect") || "/chat");
    } catch (err) {
      setError((err as Error).message || "登录失败，请稍后重试。");
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
      {[
        "top-6 left-6 border-t-2 border-l-2",
        "top-6 right-6 border-t-2 border-r-2",
        "bottom-6 left-6 border-b-2 border-l-2",
        "bottom-6 right-6 border-b-2 border-r-2"
      ].map((cls, i) => (
        <div
          key={i}
          aria-hidden="true"
          className={`pointer-events-none absolute h-8 w-8 ${cls}`}
          style={{ borderColor: "var(--theme-accent-alpha-60)" }}
        />
      ))}

      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 right-0 h-96 w-96 rounded-full blur-3xl animate-float"
        style={{ background: "radial-gradient(var(--theme-accent-alpha-20), transparent 70%)" }}
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -bottom-40 -left-20 h-96 w-96 rounded-full blur-3xl animate-float"
        style={{
          background: "radial-gradient(var(--theme-accent-alpha-10), transparent 70%)",
          animationDelay: "3s"
        }}
      />

      {BUBBLES.map((b, i) => (
        <div
          key={i}
          aria-hidden="true"
          className="pointer-events-none absolute bottom-0 rounded-full"
          style={{
            width: b.size,
            height: b.size,
            left: b.left,
            border: "1px solid var(--theme-accent-alpha-40)",
            background: "var(--theme-accent-alpha-10)",
            animation: `bubble-rise ${b.dur} ${b.delay} ease-in infinite`
          }}
        />
      ))}

      <div className="relative z-10 w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="relative mb-4">
            <div
              aria-hidden="true"
              className="absolute inset-0 rounded-full"
              style={{
                width: 160,
                height: 160,
                top: -10,
                left: -10,
                border: "1px solid var(--theme-accent-alpha-40)",
                animation: "spin-slow 12s linear infinite",
                backgroundImage:
                  "conic-gradient(from 0deg, transparent 70%, var(--theme-accent-alpha-60) 100%)",
                borderRadius: "50%"
              }}
            />
            <div
              aria-hidden="true"
              className="absolute inset-0 rounded-full"
              style={{
                width: 180,
                height: 180,
                top: -20,
                left: -20,
                border: "1px dashed var(--theme-accent-alpha-20)",
                animation: "spin-slow 20s linear infinite reverse",
                borderRadius: "50%"
              }}
            />
            <div className="relative animate-float">
              <SeahorseLogo size={140} />
            </div>
          </div>
          <h1
            className="font-display mt-2 text-3xl font-bold tracking-widest glow-text"
            style={{ color: "var(--theme-text-primary)", letterSpacing: "0.15em" }}
          >
            SEAHORSE
          </h1>
          <div className="mt-1 flex items-center gap-2">
            <div className="h-px w-8" style={{ background: "var(--theme-accent-alpha-60)" }} />
            <p className="text-xs uppercase tracking-widest" style={{ color: "var(--theme-accent)" }}>
              RAG 智能系统
            </p>
            <div className="h-px w-8" style={{ background: "var(--theme-accent-alpha-60)" }} />
          </div>
        </div>

        <div
          className="relative overflow-hidden rounded-2xl p-8"
          style={{
            background: "var(--theme-glass-bg)",
            backdropFilter: "blur(20px)",
            border: "1px solid var(--theme-accent-alpha-40)",
            boxShadow: "0 0 40px var(--theme-accent-alpha-10), inset 0 1px 0 var(--theme-accent-alpha-20)"
          }}
        >
          <div
            aria-hidden="true"
            className="absolute top-0 left-8 right-8 h-px"
            style={{ background: "linear-gradient(90deg, transparent, var(--theme-accent), transparent)" }}
          />

          <div className="mb-6 flex items-center gap-3">
            <div className="h-4 w-1 rounded-full" style={{ background: "var(--theme-accent)" }} />
            <p className="text-lg font-semibold tracking-wide" style={{ color: "var(--theme-text-primary)" }}>
              身份验证
            </p>
            <div className="ml-auto flex items-center gap-1.5">
              <div
                className="h-1.5 w-1.5 rounded-full animate-pulse"
                style={{ background: "var(--theme-accent)" }}
              />
              <span className="text-xs" style={{ color: "var(--theme-accent)" }}>
                在线
              </span>
            </div>
          </div>

          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="space-y-1.5">
              <label
                className="text-xs font-mono uppercase tracking-widest"
                style={{ color: "var(--theme-text-muted)" }}
              >
                用户名
              </label>
              <div className="relative group">
                <div
                  className="absolute top-0 bottom-0 left-0 w-0.5 rounded-full transition-all duration-300"
                  style={{
                    background: focusedField === "username" ? "var(--theme-accent)" : "transparent",
                    boxShadow: focusedField === "username" ? "0 0 8px var(--theme-accent)" : "none"
                  }}
                />
                <User
                  className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                  style={{
                    color: focusedField === "username" ? "var(--theme-accent)" : "var(--theme-text-muted)"
                  }}
                />
                <Input
                  placeholder="输入用户名"
                  value={form.username}
                  onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
                  onFocus={() => setFocusedField("username")}
                  onBlur={() => setFocusedField(null)}
                  className="pl-10 font-mono transition-all duration-200"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    borderColor:
                      focusedField === "username" ? "var(--theme-accent)" : "var(--theme-glass-border)",
                    color: "var(--theme-text-primary)",
                    boxShadow:
                      focusedField === "username" ? "0 0 12px var(--theme-accent-alpha-20)" : "none"
                  }}
                  autoComplete="username"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label
                className="text-xs font-mono uppercase tracking-widest"
                style={{ color: "var(--theme-text-muted)" }}
              >
                密码
              </label>
              <div className="relative group">
                <div
                  className="absolute top-0 bottom-0 left-0 w-0.5 rounded-full transition-all duration-300"
                  style={{
                    background: focusedField === "password" ? "var(--theme-accent)" : "transparent",
                    boxShadow: focusedField === "password" ? "0 0 8px var(--theme-accent)" : "none"
                  }}
                />
                <Lock
                  className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                  style={{
                    color: focusedField === "password" ? "var(--theme-accent)" : "var(--theme-text-muted)"
                  }}
                />
                <Input
                  type={showPassword ? "text" : "password"}
                  placeholder="输入密码"
                  value={form.password}
                  onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
                  onFocus={() => setFocusedField("password")}
                  onBlur={() => setFocusedField(null)}
                  className="pl-10 pr-10 font-mono transition-all duration-200"
                  style={{
                    backgroundColor: "var(--theme-bg-elevated)",
                    borderColor:
                      focusedField === "password" ? "var(--theme-accent)" : "var(--theme-glass-border)",
                    color: "var(--theme-text-primary)",
                    boxShadow:
                      focusedField === "password" ? "0 0 12px var(--theme-accent-alpha-20)" : "none"
                  }}
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((p) => !p)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 transition-colors duration-200"
                  style={{ color: "var(--theme-text-muted)" }}
                  aria-label="显示或隐藏密码"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between text-sm">
              <label className="flex cursor-pointer items-center gap-2" style={{ color: "var(--theme-text-muted)" }}>
                <Checkbox checked={remember} onCheckedChange={(v) => setRemember(Boolean(v))} />
                <span className="text-xs font-mono">记住会话</span>
              </label>
              <span className="text-xs font-mono" style={{ color: "var(--theme-text-muted)" }}>
                v1.0 · admin init
              </span>
            </div>

            {error && (
              <div
                className="flex items-center gap-2 rounded-lg px-3 py-2"
                style={{ background: "rgba(244,63,94,0.1)", border: "1px solid rgba(244,63,94,0.3)" }}
              >
                <div className="h-1.5 w-1.5 rounded-full bg-rose-400 animate-pulse" />
                <p className="text-sm font-mono text-rose-400">{error}</p>
              </div>
            )}

            <div className="relative overflow-hidden rounded-xl">
              <Button
                type="submit"
                className="relative w-full overflow-hidden py-6 text-base font-mono font-semibold tracking-widest"
                disabled={isLoading}
                style={{
                  background: "var(--theme-gradient)",
                  color: "var(--theme-bg-deep)",
                  boxShadow: "0 0 30px var(--theme-accent-alpha-30)"
                }}
              >
                <span
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-0"
                  style={{
                    animation: "btn-sweep 3s ease-in-out infinite",
                    background:
                      "linear-gradient(105deg, transparent 40%, rgba(255,255,255,0.25) 50%, transparent 60%)"
                  }}
                />
                {isLoading ? (
                  <span className="flex items-center gap-2">
                    <span className="flex gap-1">
                      {[0, 1, 2].map((i) => (
                        <span
                          key={i}
                          className="h-1.5 w-1.5 animate-bounce rounded-full bg-current"
                          style={{ animationDelay: `${i * 0.15}s` }}
                        />
                      ))}
                    </span>
                    验证中
                  </span>
                ) : (
                  "进入系统"
                )}
              </Button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
