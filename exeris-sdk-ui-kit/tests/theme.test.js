import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import preset from '../tailwind.preset.js';

/**
 * The Tailwind v4 `@theme` entry (`src/styles/theme.css`, B1) must stay in sync
 * with the v3 `tailwind.preset.js` and the raw `--exeris-*` tokens in
 * `index.css`: a v3 consumer (preset) and a v4 consumer (this @theme) must get
 * the same `exeris-*` utility namespace, or generated components render
 * differently depending on the host's Tailwind major. This is the drift guard.
 * Also pins the B2 `package.json` exports so the documented imports resolve.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const themeCss = readFileSync(join(root, 'src/styles/theme.css'), 'utf8');
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));

function cssVar(css, name) {
  const m = css.match(new RegExp(`${name}:\\s*([^;]+);`));
  return m ? m[1].trim() : undefined;
}

describe('Tailwind v4 @theme entry (B1)', () => {
  it('declares exactly the preset exeris color set, with channels matching index.css', () => {
    const presetColors = Object.keys(preset.theme.extend.colors.exeris).sort();

    const themeColors = [...themeCss.matchAll(/--color-exeris-([a-z-]+):/g)].map((m) => m[1]).sort();
    expect(themeColors, 'v4 @theme must declare the same exeris colors as the v3 preset')
      .toEqual(presetColors);

    for (const key of presetColors) {
      const themeValue = cssVar(themeCss, `--color-exeris-${key}`);
      const rgb = themeValue && themeValue.match(/rgb\(([^)]+)\)/);
      expect(rgb, `--color-exeris-${key} must be an rgb(...) value, got '${themeValue}'`).toBeTruthy();
      const rawChannels = cssVar(indexCss, `--exeris-${key}`);
      expect(rgb[1].trim(), `color ${key} must match index.css --exeris-${key}`).toEqual(rawChannels);
    }
  });

  it('declares every preset spacing / radius / shadow / duration / animation token', () => {
    for (const key of Object.keys(preset.theme.extend.spacing)) {
      expect(themeCss, `missing --spacing-${key}`).toContain(`--spacing-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.borderRadius)) {
      expect(themeCss, `missing --radius-${key}`).toContain(`--radius-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.boxShadow)) {
      expect(themeCss, `missing --shadow-${key}`).toContain(`--shadow-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.transitionDuration)) {
      expect(themeCss, `missing --transition-duration-${key}`).toContain(`--transition-duration-${key}:`);
    }
    for (const key of Object.keys(preset.theme.extend.animation)) {
      expect(themeCss, `missing --animate-${key}`).toContain(`--animate-${key}:`);
    }
  });

  it('defines the custom keyframes its animations reference', () => {
    expect(themeCss).toContain('@keyframes fadeIn');
    expect(themeCss).toContain('@keyframes slideUp');
  });

  it('uses Inter as the primary exeris font', () => {
    expect(cssVar(themeCss, '--font-exeris')).toContain('Inter');
  });
});

describe('package exports (B2)', () => {
  it('exposes the v4 theme and the v3 preset so the documented imports resolve', () => {
    expect(pkg.exports['./theme'], 'v4 consumers @import "@exeris-systems/ui-kit/theme"')
      .toBe('./src/styles/theme.css');
    expect(pkg.exports['./tailwind.preset.js'], 'README imports @exeris-systems/ui-kit/tailwind.preset.js')
      .toBe('./tailwind.preset.js');
  });
});
