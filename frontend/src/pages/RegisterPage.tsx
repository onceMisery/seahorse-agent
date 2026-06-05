import { useState, useRef, useCallback } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Mail, Lock, Shield, ArrowRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SeahorseLogo } from "@/components/common/SeahorseLogo";
import { toast } from "sonner";
import { useAuthStore } from "@/stores/authStore";
import { setAuthToken } from "@/services/api";
import { storage } from "@/utils/storage";
import * as registrationService from "@/services/registrationService";

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

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const COUNTDOWN_SECONDS = 60;

export function RegisterPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);

  const [countdown, setCountdown] = useState(0);
  const [sendingCode, setSendingCode] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [focusedField, setFocusedField] = useState<string | null>(null);

  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startCountdown = useCallback(() => {
    setCountdown(COUNTDOWN_SECONDS);
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          if (timerRef.current) clearInterval(timerRef.current);
          timerRef.current = null;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  }, []);

  const handleEmailBlur = () => {
    setFocusedField(null);
    if (!email.trim()) {
      setEmailError(null);
      return;
    }
    if (!EMAIL_RE.test(email.trim())) {
      setEmailError("请输入有效的邮箱地址");
    } else {
      setEmailError(null);
    }
  };

  const handleSendCode = async () => {
    if (!email.trim()) {
      setEmailError("请输入邮箱地址");
      return;
    }
    if (!EMAIL_RE.test(email.trim())) {
      setEmailError("请输入有效的邮箱地址");
      return;
    }
    setSendingCode(true);
    try {
      await registrationService.sendVerificationCode(email.trim());
      toast.success("验证码已发送");
      startCountdown();
    } catch (err) {
      toast.error((err as Error).message || "发送验证码失败");
    } finally {
      setSendingCode(false);
    }
  };

  const validateForm = (): boolean => {
    let valid = true;

    if (!email.trim()) {
      setEmailError("请输入邮箱地址");
      valid = false;
    } else if (!EMAIL_RE.test(email.trim())) {
      setEmailError("请输入有效的邮箱地址");
      valid = false;
    } else {
      setEmailError(null);
    }

    if (!code.trim()) {
      setCodeError("请输入验证码");
      valid = false;
    } else {
      setCodeError(null);
    }

    if (!password) {
      setPasswordError("请输入密码");
      valid = false;
    } else if (password.length < 8) {
      setPasswordError("密码长度不能少于 8 位");
      valid = false;
    } else {
      setPasswordError(null);
    }

    if (!confirmPassword) {
      setConfirmError("请再次输入密码");
      valid = false;
    } else if (password !== confirmPassword) {
      setConfirmError("两次输入的密码不一致");
      valid = false;
    } else {
      setConfirmError(null);
    }

    return valid;
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!validateForm()) return;

    setSubmitting(true);
    try {
      const data = await registrationService.register(email.trim(), code.trim(), password);
      const { token, userId, tenantId } = data as registrationService.RegisterResponse;

      // Persist auth state exactly like authStore.login does
      storage.setToken(token);
      storage.setUser({ userId, username: email.trim(), role: "user", token, avatar: null } as any);
      setAuthToken(token);
      useAuthStore.setState({
        user: { userId, username: email.trim(), role: "user", token, avatar: null } as any,
        token,
        isAuthenticated: true
      });

      toast.success("注册成功，欢迎使用 Seahorse！");
      navigate("/chat");
    } catch (err) {
      toast.error((err as Error).message || "注册失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  };

  const fieldStyle = (name: string) => ({
    backgroundColor: "var(--theme-bg-elevated)",
    borderColor: focusedField === name ? "var(--theme-accent)" : "var(--theme-glass-border)",
    color: "var(--theme-text-primary)",
    boxShadow: focusedField === name ? "0 0 12px var(--theme-accent-alpha-20)" : "none"
  });

  const iconColor = (name: string) =>
    focusedField === name ? "var(--theme-accent)" : "var(--theme-text-muted)";

  const barStyle = (name: string) => ({
    background: focusedField === name ? "var(--theme-accent)" : "transparent",
    boxShadow: focusedField === name ? "0 0 8px var(--theme-accent)" : "none"
  });

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4">
      {/* Corner brackets */}
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

      {/* Ambient glow blobs */}
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

      {/* Rising bubbles */}
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
        {/* Logo + title */}
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
              创建账户
            </p>
            <div className="h-px w-8" style={{ background: "var(--theme-accent-alpha-60)" }} />
          </div>
        </div>

        {/* Glass card */}
        <div
          className="relative overflow-hidden rounded-2xl p-8"
          style={{
            background: "var(--theme-glass-bg)",
            backdropFilter: "blur(20px)",
            border: "1px solid var(--theme-accent-alpha-40)",
            boxShadow:
              "0 0 40px var(--theme-accent-alpha-10), inset 0 1px 0 var(--theme-accent-alpha-20)"
          }}
        >
          {/* Top highlight line */}
          <div
            aria-hidden="true"
            className="absolute top-0 left-8 right-8 h-px"
            style={{ background: "linear-gradient(90deg, transparent, var(--theme-accent), transparent)" }}
          />

          {/* Section label */}
          <div className="mb-6 flex items-center gap-3">
            <div className="h-4 w-1 rounded-full" style={{ background: "var(--theme-accent)" }} />
            <p
              className="text-lg font-semibold tracking-wide"
              style={{ color: "var(--theme-text-primary)" }}
            >
              注册新账户
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
            {/* Email */}
            <div className="space-y-1.5">
              <label
                className="text-xs font-mono uppercase tracking-widest"
                style={{ color: "var(--theme-text-muted)" }}
              >
                邮箱
              </label>
              <div className="relative group">
                <div
                  className="absolute top-0 bottom-0 left-0 w-0.5 rounded-full transition-all duration-300"
                  style={barStyle("email")}
                />
                <Mail
                  className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                  style={{ color: iconColor("email") }}
                />
                <Input
                  type="email"
                  placeholder="you@example.com"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    if (emailError) setEmailError(null);
                  }}
                  onFocus={() => setFocusedField("email")}
                  onBlur={handleEmailBlur}
                  className="pl-10 font-mono transition-all duration-200"
                  style={fieldStyle("email")}
                  autoComplete="email"
                />
              </div>
              {emailError && (
                <p className="text-xs font-mono text-rose-400 mt-1">{emailError}</p>
              )}
            </div>

            {/* Verification Code */}
            <div className="space-y-1.5">
              <label
                className="text-xs font-mono uppercase tracking-widest"
                style={{ color: "var(--theme-text-muted)" }}
              >
                验证码
              </label>
              <div className="flex gap-2">
                <div className="relative flex-1 group">
                  <div
                    className="absolute top-0 bottom-0 left-0 w-0.5 rounded-full transition-all duration-300"
                    style={barStyle("code")}
                  />
                  <Shield
                    className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                    style={{ color: iconColor("code") }}
                  />
                  <Input
                    placeholder="6 位验证码"
                    value={code}
                    onChange={(e) => {
                      setCode(e.target.value);
                      if (codeError) setCodeError(null);
                    }}
                    onFocus={() => setFocusedField("code")}
                    onBlur={() => setFocusedField(null)}
                    className="pl-10 font-mono transition-all duration-200"
                    style={fieldStyle("code")}
                    maxLength={6}
                  />
                </div>
                <Button
                  type="button"
                  variant="outline"
                  disabled={countdown > 0 || sendingCode}
                  onClick={handleSendCode}
                  className="shrink-0 font-mono text-xs px-3"
                  style={{
                    borderColor: "var(--theme-accent-alpha-40)",
                    color: countdown > 0 ? "var(--theme-text-muted)" : "var(--theme-accent)",
                    backgroundColor: "var(--theme-bg-elevated)"
                  }}
                >
                  {sendingCode
                    ? "发送中…"
                    : countdown > 0
                      ? `${countdown}s`
                      : "发送验证码"}
                </Button>
              </div>
              {codeError && (
                <p className="text-xs font-mono text-rose-400 mt-1">{codeError}</p>
              )}
            </div>

            {/* Password */}
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
                  style={barStyle("password")}
                />
                <Lock
                  className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                  style={{ color: iconColor("password") }}
                />
                <Input
                  type="password"
                  placeholder="至少 8 位密码"
                  value={password}
                  onChange={(e) => {
                    setPassword(e.target.value);
                    if (passwordError) setPasswordError(null);
                  }}
                  onFocus={() => setFocusedField("password")}
                  onBlur={() => {
                    setFocusedField(null);
                    if (password && password.length < 8) {
                      setPasswordError("密码长度不能少于 8 位");
                    } else {
                      setPasswordError(null);
                    }
                  }}
                  className="pl-10 font-mono transition-all duration-200"
                  style={fieldStyle("password")}
                  autoComplete="new-password"
                />
              </div>
              {passwordError && (
                <p className="text-xs font-mono text-rose-400 mt-1">{passwordError}</p>
              )}
            </div>

            {/* Confirm Password */}
            <div className="space-y-1.5">
              <label
                className="text-xs font-mono uppercase tracking-widest"
                style={{ color: "var(--theme-text-muted)" }}
              >
                确认密码
              </label>
              <div className="relative group">
                <div
                  className="absolute top-0 bottom-0 left-0 w-0.5 rounded-full transition-all duration-300"
                  style={barStyle("confirm")}
                />
                <Lock
                  className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 transition-colors duration-200"
                  style={{ color: iconColor("confirm") }}
                />
                <Input
                  type="password"
                  placeholder="再次输入密码"
                  value={confirmPassword}
                  onChange={(e) => {
                    setConfirmPassword(e.target.value);
                    if (confirmError) setConfirmError(null);
                  }}
                  onFocus={() => setFocusedField("confirm")}
                  onBlur={() => {
                    setFocusedField(null);
                    if (confirmPassword && confirmPassword !== password) {
                      setConfirmError("两次输入的密码不一致");
                    } else {
                      setConfirmError(null);
                    }
                  }}
                  className="pl-10 font-mono transition-all duration-200"
                  style={fieldStyle("confirm")}
                  autoComplete="new-password"
                />
              </div>
              {confirmError && (
                <p className="text-xs font-mono text-rose-400 mt-1">{confirmError}</p>
              )}
            </div>

            {/* Submit */}
            <div className="relative overflow-hidden rounded-xl">
              <Button
                type="submit"
                className="relative w-full overflow-hidden py-6 text-base font-mono font-semibold tracking-widest"
                disabled={submitting}
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
                {submitting ? (
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
                    注册中
                  </span>
                ) : (
                  <span className="flex items-center gap-2">
                    注册
                    <ArrowRight className="h-4 w-4" />
                  </span>
                )}
              </Button>
            </div>
          </form>
        </div>

        {/* Login link */}
        <p className="mt-6 text-center text-sm" style={{ color: "var(--theme-text-muted)" }}>
          已有账户？
          <Link
            to="/login"
            className="ml-1 font-semibold transition-colors"
            style={{ color: "var(--theme-accent)" }}
          >
            去登录
          </Link>
        </p>
      </div>
    </div>
  );
}
