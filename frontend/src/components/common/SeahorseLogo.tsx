import React from "react";

interface SeahorseLogoProps {
  size?: number;
  className?: string;
}

export const SeahorseLogo: React.FC<SeahorseLogoProps> = ({ size = 40, className = "" }) => {
  return (
    <img
      src="/main.png"
      alt="Seahorse"
      width={size}
      height={size}
      className={className}
      style={{
        width: size,
        height: size,
        objectFit: "contain",
        mixBlendMode: "var(--theme-logo-blend, screen)" as React.CSSProperties["mixBlendMode"],
        filter: "var(--theme-logo-filter) drop-shadow(0 0 6px var(--theme-accent))",
      }}
    />
  );
};
