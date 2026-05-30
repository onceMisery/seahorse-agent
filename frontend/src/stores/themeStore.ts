import { create } from "zustand";

import { storage } from "@/utils/storage";

export type ThemeMode = "light" | "dark";

export const COLOR_THEMES = {
  marine: { label: "海洋", class: "" },
  white: { label: "皓白", class: "theme-white" },
  purple: { label: "星云", class: "theme-purple" },
  emerald: { label: "翡翠", class: "theme-emerald" },
  amber: { label: "琥珀", class: "theme-amber" },
  deepSea: { label: "深海蓝", class: "theme-deep-sea" }
} as const;

export type ColorThemeKey = keyof typeof COLOR_THEMES;

export const VISIBLE_COLOR_THEME_KEYS: ColorThemeKey[] = ["marine", "white", "deepSea"];

interface ThemeState {
  theme: ThemeMode;
  colorTheme: ColorThemeKey;
  setTheme: (theme: ThemeMode) => void;
  setColorTheme: (key: ColorThemeKey) => void;
  toggleTheme: () => void;
  initialize: () => void;
}

const COLOR_THEME_STORAGE_KEY = "seahorse-color-theme";

function applyTheme(theme: ThemeMode) {
  const root = document.documentElement;
  root.classList.toggle("dark", theme === "dark");
  root.dataset.theme = theme;
}

function applyColorTheme(key: ColorThemeKey) {
  const root = document.documentElement;
  // Remove all theme classes including legacy ones not in COLOR_THEMES
  ["theme-purple", "theme-emerald", "theme-amber", ...Object.values(COLOR_THEMES).map((t) => t.class)].forEach((cls) => {
    if (cls) root.classList.remove(cls);
  });
  const preset = COLOR_THEMES[key];
  if (preset.class) root.classList.add(preset.class);
  localStorage.setItem(COLOR_THEME_STORAGE_KEY, key);
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: "light",
  colorTheme: "marine",
  setTheme: (theme) => {
    storage.setTheme(theme);
    applyTheme(theme);
    set({ theme });
  },
  setColorTheme: (key) => {
    applyColorTheme(key);
    set({ colorTheme: key });
  },
  toggleTheme: () => {
    const next = get().theme === "light" ? "dark" : "light";
    get().setTheme(next);
  },
  initialize: () => {
    const stored = storage.getTheme();
    const theme = stored === "dark" ? "dark" : "light";
    applyTheme(theme);
    set({ theme });

    const storedColor = localStorage.getItem(COLOR_THEME_STORAGE_KEY);
    const colorTheme = storedColor && storedColor in COLOR_THEMES ? (storedColor as ColorThemeKey) : "marine";
    applyColorTheme(colorTheme);
    set({ colorTheme });
  }
}));
