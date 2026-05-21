/**
 * 深海背景 — 阳光穿透水面 + 上升气泡，始终可见。
 * 颜色跟随主题变量切换。
 */

const BUBBLES = [
  { size: 6, left: "5%", delay: "0s", dur: "8s", opacity: 0.4 },
  { size: 10, left: "12%", delay: "1.5s", dur: "11s", opacity: 0.5 },
  { size: 4, left: "22%", delay: "3s", dur: "7s", opacity: 0.3 },
  { size: 14, left: "35%", delay: "0.8s", dur: "13s", opacity: 0.45 },
  { size: 8, left: "48%", delay: "2.2s", dur: "9s", opacity: 0.5 },
  { size: 18, left: "60%", delay: "4s", dur: "14s", opacity: 0.35 },
  { size: 5, left: "72%", delay: "1s", dur: "8.5s", opacity: 0.4 },
  { size: 12, left: "82%", delay: "3.5s", dur: "12s", opacity: 0.45 },
  { size: 7, left: "90%", delay: "2.5s", dur: "10s", opacity: 0.35 },
  { size: 16, left: "28%", delay: "5s", dur: "15s", opacity: 0.3 },
  { size: 9, left: "55%", delay: "0.5s", dur: "9.5s", opacity: 0.5 },
  { size: 3, left: "42%", delay: "6s", dur: "6s", opacity: 0.25 },
  { size: 11, left: "78%", delay: "1.8s", dur: "11.5s", opacity: 0.4 },
  { size: 15, left: "15%", delay: "4.5s", dur: "13.5s", opacity: 0.35 },
  { size: 6, left: "65%", delay: "3.2s", dur: "7.5s", opacity: 0.4 },
  { size: 8, left: "8%", delay: "7s", dur: "10s", opacity: 0.3 },
  { size: 13, left: "52%", delay: "2.8s", dur: "12.5s", opacity: 0.4 },
  { size: 5, left: "88%", delay: "5.5s", dur: "8s", opacity: 0.35 },
];

const RAYS = [
  { left: "15%", width: 200, opacity: 0.07, rotate: "-10deg", animDelay: "0s" },
  { left: "30%", width: 160, opacity: 0.09, rotate: "-4deg", animDelay: "2s" },
  { left: "48%", width: 220, opacity: 0.12, rotate: "0deg", animDelay: "0.5s" },
  { left: "66%", width: 160, opacity: 0.09, rotate: "4deg", animDelay: "3s" },
  { left: "82%", width: 200, opacity: 0.07, rotate: "10deg", animDelay: "1.5s" },
];

export function DeepSeaBackground() {
  return (
    <div aria-hidden="true" className="ds-bg pointer-events-none absolute inset-0 overflow-hidden" style={{ zIndex: 0 }}>

      {/* 顶部水下环境光 */}
      <div className="absolute inset-0"
        style={{
          background:
            "radial-gradient(ellipse 90% 45% at 50% -5%, var(--ds-ambient-top) 0%, transparent 55%), " +
            "radial-gradient(ellipse 60% 35% at 50% 100%, var(--ds-ambient-bottom) 0%, transparent 50%)"
        }} />

      {/* 阳光光线 — 带呼吸动画 */}
      {RAYS.map((ray, i) => (
        <div key={`ray-${i}`} className="absolute"
          style={{
            left: ray.left,
            top: "-15%",
            width: ray.width,
            height: "130%",
            transform: `rotate(${ray.rotate})`,
            background: `linear-gradient(180deg, rgba(var(--ds-ray-color),${ray.opacity * 1.5}) 0%, rgba(var(--ds-ray-color),${ray.opacity}) 25%, rgba(var(--ds-ray-color),${ray.opacity * 0.3}) 50%, transparent 70%)`,
            filter: "blur(30px)",
            animation: `ray-breathe 6s ${ray.animDelay} ease-in-out infinite alternate`,
          }} />
      ))}

      {/* 顶部水面波纹光斑 */}
      <div className="absolute top-0 left-0 right-0 h-32"
        style={{
          background: "repeating-linear-gradient(90deg, transparent, var(--ds-water-surface) 100px, transparent 200px)",
          animation: "water-shimmer 8s ease-in-out infinite",
        }} />

      {/* 上升气泡 */}
      {BUBBLES.map((b, i) => (
        <div key={`bubble-${i}`} className="absolute bottom-0"
          style={{
            width: b.size,
            height: b.size,
            left: b.left,
            borderRadius: "50%",
            border: `1px solid rgba(var(--ds-bubble-r),var(--ds-bubble-g),var(--ds-bubble-b),${b.opacity})`,
            background: `radial-gradient(circle at 30% 30%, rgba(var(--ds-bubble-r),var(--ds-bubble-g),var(--ds-bubble-b),${b.opacity * 0.5}), rgba(var(--ds-bubble-r),var(--ds-bubble-g),var(--ds-bubble-b),${b.opacity * 0.15}))`,
            animation: `bubble-rise ${b.dur} ${b.delay} ease-in infinite`,
            boxShadow: `0 0 ${Math.max(b.size / 2, 3)}px rgba(var(--ds-bubble-r),var(--ds-bubble-g),var(--ds-bubble-b),${b.opacity * 0.4}), inset 0 -${b.size / 4}px ${b.size / 3}px rgba(var(--ds-bubble-r),var(--ds-bubble-g),var(--ds-bubble-b),${b.opacity * 0.2})`,
          }} />
      ))}
    </div>
  );
}
