import { DARK_THEME, LIGHT_THEME } from "@casehubio/pages-runtime";

export const themes = { light: LIGHT_THEME, dark: DARK_THEME };
export type ThemeMode = keyof typeof themes;
