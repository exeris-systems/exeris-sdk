import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defaultTheme } from '../src/index.ts';

/**
 * `defaultTheme` must agree with the CSS custom properties, token for token.
 *
 * Why this exists, given index.test.ts already tests defaultTheme: that file pins the
 * constant against hard-coded lists it carries itself, so it verifies the shape a reader
 * wrote down rather than the shape consumers actually get. Exactly one value — `primary` —
 * was checked against the CSS. Change `success` to the wrong rgb, or `spacing.md` from
 * `1rem` to `2rem`, and every existing test still passes: the regexes only assert the
 * *form* of the value.
 *
 * That matters more here than the numbers suggest. `defaultTheme` is documented as what
 * generated components fall back to when a host app overrides nothing, so a value that
 * drifts from the CSS does not fail anywhere — it renders differently, quietly, in the
 * half of the surface that took the fallback.
 *
 * This test derives from the CSS instead, the way `theme.test.js` derives from the preset:
 * nothing is listed twice, so a token added on either side has to be added on both.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');

/**
 * The token block for one selector.
 *
 * Scoping to a selector is not tidiness — it is required. `index.css` declares every token
 * twice, once under `:root` and once under `.dark`, and a whole-file scan silently returns
 * the dark value because it comes last. `defaultTheme` is the light set, so reading the
 * whole file compared it against the wrong half.
 */
function block(selector) {
  const start = indexCss.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`no ${selector} block in index.css`);
  let depth = 0;
  for (let i = indexCss.indexOf('{', start); i < indexCss.length; i++) {
    if (indexCss[i] === '{') depth++;
    else if (indexCss[i] === '}' && --depth === 0) return indexCss.slice(start, i);
  }
  throw new Error(`unterminated ${selector} block`);
}

const LIGHT = block(':root');
const DARK = block('.dark');

/** `--exeris-<prefix><name>: <value>` → Map(name → value), within one selector's block. */
function tokens(prefix, css = LIGHT) {
  const found = new Map();
  const pattern = new RegExp(`--exeris-${prefix}([a-z-]+):\\s*([^;]+);`, 'g');
  for (const [, name, value] of css.matchAll(pattern)) {
    found.set(name, value.trim());
  }
  return found;
}

/**
 * CSS colour tokens with no `defaultTheme` counterpart, each one a decision rather than an
 * oversight. Anything not listed here must exist on both sides.
 *
 * Empty, and worth keeping that way: `primary-hover` was the sole entry, recorded when this
 * test first found it missing, and it has since been added to `defaultTheme` rather than
 * excused. An entry here should be rare and should say why.
 */
const COLOURS_WITHOUT_A_JS_FALLBACK = new Map([]);

/**
 * Palette colours deliberately not re-tuned under `.dark` — status colours held constant
 * across themes. Listed so the choice is visible; anything new must be listed or overridden.
 */
const HELD_ACROSS_THEMES = new Set(['success', 'warning', 'danger', 'info']);

/**
 * Tokens that are not semantic palette colours: surface/text/border scales, and the
 * geometry and motion tokens that do not vary with colour scheme. Both checks below need
 * this same distinction — one to decide what needs a JS fallback, the other to decide what
 * needs a dark override — so it is defined once. A new prefix belongs here, not in a copy.
 */
const NOT_A_PALETTE_COLOUR = /^(bg|text|border|shadow|spacing|radius|transition)(-|$)/;

describe('defaultTheme agrees with the CSS custom properties', () => {
  it('every colour it declares matches the CSS channels exactly', () => {
    const css = tokens('');
    for (const [key, value] of Object.entries(defaultTheme.colors)) {
      const channels = css.get(key);
      expect(channels, `--exeris-${key} is missing from index.css`).toBeDefined();
      expect(value, `colors.${key} must equal --exeris-${key}`).toBe(`rgb(${channels.replace(/\s+/g, ', ')})`);
    }
  });

  it('every semantic CSS colour has a JS fallback, or a recorded reason it does not', () => {
    const declared = new Set(Object.keys(defaultTheme.colors));
    const missing = [];
    for (const name of tokens('').keys()) {
      if (NOT_A_PALETTE_COLOUR.test(name) || declared.has(name) || COLOURS_WITHOUT_A_JS_FALLBACK.has(name)) continue;
      missing.push(name);
    }
    expect(missing, 'add it to defaultTheme.colors, or record why it has no JS fallback')
      .toEqual([]);
  });

  it('every spacing value matches its CSS token', () => {
    const css = tokens('spacing-');
    expect([...css.keys()].sort(), 'spacing tokens must exist on both sides')
      .toEqual(Object.keys(defaultTheme.spacing).sort());
    for (const [key, value] of Object.entries(defaultTheme.spacing)) {
      expect(value, `spacing.${key} must equal --exeris-spacing-${key}`).toBe(css.get(key));
    }
  });

  it('the dark block overrides every palette colour, or records why it does not', () => {
    // Same drift one level down, and the shape of it is worth stating: .dark carries 11 of
    // :root's 32 tokens. Sixteen of the difference are theme-invariant — radius, spacing,
    // shadow and transition do not change with colour scheme, so declaring them once and
    // inheriting is correct, and demanding parity for them would be wrong.
    //
    // Four are not: success, warning, danger and info keep their light values under .dark
    // while primary, primary-hover and secondary are re-tuned. That reads as deliberate —
    // status colours are often held constant — so it is recorded rather than asserted to be
    // a defect. What this guards is the next one: a palette colour added to :root and
    // forgotten in .dark fails here instead of rendering a light value on a dark surface.
    const light = [...tokens('', LIGHT).keys()].filter((k) => !NOT_A_PALETTE_COLOUR.test(k)).sort();
    const dark = new Set(tokens('', DARK).keys());
    const notOverridden = light.filter((k) => !dark.has(k) && !HELD_ACROSS_THEMES.has(k));
    expect(notOverridden, 'add a .dark value, or record it in HELD_ACROSS_THEMES')
      .toEqual([]);
  });

  it('every radius value matches its CSS token', () => {
    const css = tokens('radius-');
    expect([...css.keys()].sort(), 'radius tokens must exist on both sides')
      .toEqual(Object.keys(defaultTheme.radius).sort());
    for (const [key, value] of Object.entries(defaultTheme.radius)) {
      expect(value, `radius.${key} must equal --exeris-radius-${key}`).toBe(css.get(key));
    }
  });
});
