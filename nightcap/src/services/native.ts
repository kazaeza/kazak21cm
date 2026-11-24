export interface NightcapNativeBridge {
  haptic?: (style: 'light' | 'medium' | 'success' | 'blast') => void;
  getInsets?: () => string;
  exitApp?: () => void;
}

declare global {
  interface Window {
    NightcapNative?: NightcapNativeBridge;
    __nightcapBack?: () => boolean;
    __nightcapApplyInsets?: (topPx: number, bottomPx: number) => void;
  }
}

export function haptic(style: 'light' | 'medium' | 'success' | 'blast' = 'light'): void {
  try {
    if (window.NightcapNative?.haptic) {
      window.NightcapNative.haptic(style);
      return;
    }
    if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
      if (style === 'blast') {
        navigator.vibrate([16, 28, 38, 22, 14]);
      } else {
        const ms = style === 'light' ? 10 : style === 'medium' ? 20 : 28;
        navigator.vibrate(ms);
      }
    }
  } catch {
    // No-op on unsupported platforms
  }
}

export function applyNativeInsets(): void {
  const apply = (top: number, bottom: number) => {
    const root = document.documentElement;
    root.style.setProperty('--safe-top', `${Math.max(0, Math.round(top))}px`);
    root.style.setProperty('--safe-bottom', `${Math.max(0, Math.round(bottom))}px`);
  };

  window.__nightcapApplyInsets = apply;

  try {
    if (window.NightcapNative?.getInsets) {
      const raw = window.NightcapNative.getInsets();
      const parsed = JSON.parse(raw) as { top?: number; bottom?: number };
      apply(parsed.top ?? 0, parsed.bottom ?? 0);
    }
  } catch {
    // Fallback to CSS env(safe-area-inset-*)
  }
}

export function registerBackHandler(handler: () => boolean): void {
  window.__nightcapBack = () => {
    const handled = handler();
    if (!handled && window.NightcapNative?.exitApp) {
      window.NightcapNative.exitApp();
      return true;
    }
    return handled;
  };
}
