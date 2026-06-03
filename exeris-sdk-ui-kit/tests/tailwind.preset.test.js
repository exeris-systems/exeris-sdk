import { describe, expect, it } from 'vitest';
import preset from '../tailwind.preset.js';

/**
 * The Tailwind preset declares the `exeris-*` design-token namespace. Class
 * names like `bg-exeris-primary`, `rounded-exeris-md`, `shadow-exeris-lg`
 * resolve only because of the keys declared here. An accidental rename or
 * deletion would break every generated Angular component silently — Tailwind
 * just emits no rule for an unknown utility. These tests pin the surface.
 */
describe('tailwind preset', () => {
  it('extends theme without overriding the base scale', () => {
    expect(preset).toBeTypeOf('object');
    expect(preset.theme).toBeTypeOf('object');
    expect(preset.theme.extend).toBeTypeOf('object');
  });

  it('declares every documented exeris color', () => {
    const colors = preset.theme.extend.colors.exeris;
    expect(Object.keys(colors).sort()).toEqual([
      'danger',
      'info',
      'primary',
      'primary-hover',
      'secondary',
      'success',
      'warning',
    ]);
    for (const [key, value] of Object.entries(colors)) {
      expect(value, `${key} must use the rgb(var(--exeris-*) / <alpha-value>) pattern`)
        .toMatch(/^rgb\(var\(--exeris-[a-z-]+\) \/ <alpha-value>\)$/);
    }
  });

  it('declares spacing keys with the exeris- prefix', () => {
    const spacing = preset.theme.extend.spacing;
    expect(Object.keys(spacing).sort()).toEqual([
      'exeris-lg',
      'exeris-md',
      'exeris-sm',
      'exeris-xl',
      'exeris-xs',
    ]);
  });

  it('declares borderRadius keys with the exeris- prefix', () => {
    const radius = preset.theme.extend.borderRadius;
    expect(Object.keys(radius).sort()).toEqual([
      'exeris-lg',
      'exeris-md',
      'exeris-sm',
      'exeris-xl',
    ]);
  });

  it('declares boxShadow keys including the unprefixed default', () => {
    const shadow = preset.theme.extend.boxShadow;
    expect(Object.keys(shadow).sort()).toEqual([
      'exeris',
      'exeris-lg',
      'exeris-md',
      'exeris-sm',
    ]);
  });

  it('declares animations and the keyframes they reference', () => {
    const animation = preset.theme.extend.animation;
    const keyframes = preset.theme.extend.keyframes;

    expect(Object.keys(animation).sort()).toEqual([
      'exeris-fade-in',
      'exeris-pulse',
      'exeris-slide-up',
      'exeris-spin',
    ]);
    // Custom keyframes referenced by name in the animation values must exist.
    expect(keyframes.fadeIn).toBeDefined();
    expect(keyframes.slideUp).toBeDefined();
    // Spin & pulse use Tailwind built-ins — not redefined here.
    expect(animation['exeris-spin']).toContain('spin');
    expect(animation['exeris-pulse']).toContain('pulse');
  });

  it('uses Inter as the primary exeris font with a sensible fallback chain', () => {
    const fonts = preset.theme.extend.fontFamily.exeris;
    expect(fonts[0]).toBe('Inter');
    expect(fonts).toContain('sans-serif');
  });

  it('does not register any plugins (preset is theme-only)', () => {
    expect(preset.plugins).toEqual([]);
  });
});
