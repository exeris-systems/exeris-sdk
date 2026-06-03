import { describe, expect, it } from 'vitest';
import { defaultTheme, type ExerisTheme } from '../src/index';

/**
 * Shape contract: the exported `defaultTheme` is what generated Angular
 * components fall back to when a host app does not override design tokens.
 * Renaming a key here is a breaking change for consumers of @exeris/ui-kit,
 * so the test pins both the structure and the values.
 */
describe('defaultTheme', () => {
  it('exposes the documented top-level groups', () => {
    expect(Object.keys(defaultTheme).sort()).toEqual(['colors', 'radius', 'spacing']);
  });

  it('declares every documented color token', () => {
    expect(Object.keys(defaultTheme.colors).sort())
      .toEqual(['danger', 'info', 'primary', 'secondary', 'success', 'warning']);
    for (const [key, value] of Object.entries(defaultTheme.colors)) {
      expect(value, `colors.${key} must be an rgb() literal`).toMatch(/^rgb\(\s*\d+,\s*\d+,\s*\d+\s*\)$/);
    }
  });

  it('declares every documented spacing token', () => {
    expect(Object.keys(defaultTheme.spacing).sort()).toEqual(['lg', 'md', 'sm', 'xl', 'xs']);
    for (const [key, value] of Object.entries(defaultTheme.spacing)) {
      expect(value, `spacing.${key} must use rem units`).toMatch(/^\d+(\.\d+)?rem$/);
    }
  });

  it('declares every documented radius token including full', () => {
    expect(Object.keys(defaultTheme.radius).sort()).toEqual(['full', 'lg', 'md', 'sm', 'xl']);
    // `full` is the pill-shape sentinel — 9999px is the documented value.
    expect(defaultTheme.radius.full).toBe('9999px');
  });

  it('matches the ExerisTheme structural type', () => {
    // Compile-time + runtime check: assignability to ExerisTheme.
    const t: ExerisTheme = defaultTheme;
    expect(t).toBe(defaultTheme);
  });

  it('primary colour matches the indigo-600 token CSS uses', () => {
    // The CSS custom property --exeris-primary: 79 70 229 must agree with
    // this constant — generated components read whichever is provided.
    expect(defaultTheme.colors.primary).toBe('rgb(79, 70, 229)');
  });
});
