import { beforeAll, describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import postcss from 'postcss';
import { PACKAGE_ROOT, THEME, block, compileWithV4, declarations } from './support/tailwind.js';
import { distance, srgbOf } from './support/oklch.js';

/**
 * `@exeris/ui-kit/theme-exeris` is the opt-in brand theme. Five properties make it safe to ship
 * next to the default theme and true to the brand kit, and each is held here:
 *
 *   - it is inert outside `[data-theme="exeris"]`: every rule it declares is scoped, and the
 *     default `:root` tokens compile identically with and without it;
 *   - it re-points every token the component layer reads, and re-declares every `@theme` mapping
 *     that reads a token it re-points, so the existing classes and utilities follow it at any
 *     nesting depth without new markup;
 *   - each `--exeris-*` colour is the sRGB value of the `--ex-*` token its comment names (hex,
 *     or oklch converted), recomputed here, so the brand value and the channel value cannot drift;
 *   - Evidence Orange marks verified results only: it appears in the evidence modifiers and
 *     nowhere else, and no kit token or status colour resembles it;
 *   - it is preview tier: none of the names it introduces is in the frozen snapshot.
 */
const read = (path) => readFileSync(join(PACKAGE_ROOT, path), 'utf8');
const stripComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '');

const SCOPE = '[data-theme="exeris"]';
const TOKEN_SELECTOR = `${SCOPE},\n${SCOPE} .dark`;
const themeExerisCss = read('src/styles/theme-exeris.css');
const indexCss = read('src/styles/index.css');
const previewCss = read('src/styles/preview.css');
const tokenBlock = block(themeExerisCss, TOKEN_SELECTOR);
const scoped = declarations(tokenBlock);
const defaultTokens = declarations(block(indexCss, ':root'));

/** The brand kit's colours (v6), by their token names, with the exact value the kit gives. */
const BRAND_COLOURS = {
  '--ex-bg': '#0A0F1A',
  '--ex-fg': '#F8FAFC',
  '--ex-flow-blue': '#2563EB',
  '--ex-flow-cyan': '#06B6D4',
  '--ex-evidence': '#F59E0B',
};

/** Every brand token the scope declares: the kit's colours, the steps derived from them, type. */
const BRAND_TOKENS = [
  ...Object.keys(BRAND_COLOURS),
  '--ex-flow',
  '--ex-bg-1', '--ex-surface', '--ex-surface-2', '--ex-surface-3', '--ex-surface-4',
  '--ex-fg-2', '--ex-fg-3', '--ex-fg-4', '--ex-fg-5',
  '--ex-line', '--ex-line-2', '--ex-line-strong',
  '--ex-flow-blue-hover', '--ex-flow-blue-text',
  '--ex-ok', '--ex-warn', '--ex-err',
  '--ex-font', '--ex-mono',
];

/** The only classes that may paint Evidence Orange. */
const EVIDENCE_MODIFIERS = ['exeris-tag-evidence', 'exeris-kpi-value-evidence'];

/** The brand primitives the theme adds, each a selector the compiled output must carry. */
const PRIMITIVES = [
  'exeris-eyebrow',
  'exeris-tag', 'exeris-tag-flow', 'exeris-tag-cyan', 'exeris-tag-evidence',
  'exeris-tag-ok', 'exeris-tag-warn', 'exeris-tag-err', 'exeris-tag-muted',
  'exeris-led', 'exeris-led-ok', 'exeris-led-flow', 'exeris-led-warn', 'exeris-led-err', 'exeris-led-live',
  'exeris-kpi', 'exeris-kpi-label', 'exeris-kpi-value', 'exeris-kpi-value-flow', 'exeris-kpi-value-cyan',
  'exeris-kpi-value-evidence', 'exeris-kpi-sub',
  'exeris-code', 'exeris-code-kw', 'exeris-code-an', 'exeris-code-ty', 'exeris-code-str', 'exeris-code-num',
  'exeris-code-cm', 'exeris-code-fn', 'exeris-code-pn', 'exeris-code-ln',
  'exeris-def', 'exeris-section-head', 'exeris-link',
];

const classesIn = (css) => new Set([...stripComments(css).matchAll(/\.(exeris-[a-z0-9-]+)/g)].map(([, n]) => n));

describe('@exeris/ui-kit/theme-exeris — the entry', () => {
  it('is exported and points at a file the package ships', () => {
    const pkg = JSON.parse(read('package.json'));
    expect(pkg.exports['./theme-exeris']).toBe('./src/styles/theme-exeris.css');
    expect(existsSync(join(PACKAGE_ROOT, 'src/styles/theme-exeris.css'))).toBe(true);
    expect(pkg.files).toContain('src');
  });

  it('fetches nothing: no @import, no url()', () => {
    // Fonts are the consumer's to load; a stylesheet that hot-links them makes every consumer
    // depend on a third-party origin at runtime.
    const css = stripComments(themeExerisCss);
    expect(css).not.toMatch(/@import\b/);
    expect(css).not.toMatch(/\burl\(/);
  });

  it('is plain CSS, so it compiles the same with or without Tailwind', () => {
    const css = stripComments(themeExerisCss);
    expect(css).not.toMatch(/@apply\b/);
    expect(css).not.toMatch(/@theme\b/);
  });

  it('scopes every rule to [data-theme="exeris"]', () => {
    const unscoped = [];
    postcss.parse(themeExerisCss).walkRules((rule) => {
      if (rule.parent?.type === 'atrule' && rule.parent.name === 'keyframes') return;
      for (const part of rule.selectors) {
        if (!part.trim().startsWith(SCOPE)) unscoped.push(part);
      }
    });
    expect(unscoped, 'a rule outside the scope would change the default theme').toEqual([]);
  });
});

describe('@exeris/ui-kit/theme-exeris — the tokens', () => {
  it('declares every brand token by its brand name', () => {
    const declared = [...scoped.keys()].filter((name) => name.startsWith('--ex-'));
    expect(declared.sort()).toEqual([...BRAND_TOKENS].sort());
  });

  it('re-points every colour, radius and shadow token the component layer reads', () => {
    const themed = [...defaultTokens.keys()].filter((name) => /^--exeris-(?!spacing-|transition-)/.test(name));
    expect(themed.length, 'the default token set looks too short to be real').toBeGreaterThan(20);
    for (const name of themed) {
      expect(scoped.has(name), `${name} keeps its default value inside the brand scope`).toBe(true);
    }
    for (const [name, value] of scoped) {
      if (name.startsWith('--exeris-radius-')) expect(value, `${name}: the brand is square`).toBe('0');
      if (name.startsWith('--exeris-shadow')) expect(value, `${name}: the brand is flat`).toBe('0 0 #0000');
    }
  });

  it('declares the brand kit colours verbatim, and the flow gradient from Flow Blue to Flow Cyan', () => {
    for (const [name, value] of Object.entries(BRAND_COLOURS)) {
      expect(scoped.get(name)?.toUpperCase(), `${name} is not the brand kit value`).toBe(value);
    }
    expect(scoped.get('--ex-flow')).toBe('linear-gradient(90deg, var(--ex-flow-blue), var(--ex-flow-cyan))');
  });

  it('gives each colour the sRGB value of the --ex-* token its comment names', () => {
    const lines = tokenBlock.split('\n').filter((line) => /^\s*--exeris-[a-z-]+:\s*\d{1,3} \d{1,3} \d{1,3};/.test(line));
    expect(lines.length).toBeGreaterThan(10);
    for (const line of lines) {
      const [, name, channels, source, note] = line.match(/(--exeris-[a-z-]+):\s*([^;]+);\s*\/\*\s*(--ex-[a-z0-9-]+)(.*?)\*\//)
        ?? [null, line];
      expect(source, `${name} must name the --ex-* token it converts`).toBeTruthy();
      const colour = srgbOf(scoped.get(source) ?? '');
      expect(colour, `${source} is not a hex or oklch() token in the scope`).not.toBeNull();
      const { rgb, mapped } = colour;
      expect(channels.trim(), `${name} is not the sRGB value of ${source}`).toBe(rgb.join(' '));
      expect(note.includes('gamut-mapped'), `${name}: the gamut-mapped note must match the conversion`).toBe(mapped);
    }
  });

  it('re-declares every @theme mapping that reads a token it re-points', () => {
    // A custom property's var() resolves where it is declared, so without the repeat a nested
    // scope would leave bg-exeris-primary &c. on the default value.
    const reads = [...THEME].filter(([, value]) => {
      const token = value.match(/--exeris-[a-z0-9-]+/)?.[0];
      return token && scoped.has(token);
    });
    expect(reads.length).toBeGreaterThan(10);
    for (const [name, value] of reads) {
      expect(scoped.get(name), `${name} is not recomputed in the brand scope`).toBe(value);
    }
  });

  it('is dark-only: the token block outranks a nested .dark', () => {
    expect(themeExerisCss).toContain(`${TOKEN_SELECTOR} {`);
    expect(stripComments(tokenBlock)).toMatch(/color-scheme:\s*dark;/);
  });
});

describe('@exeris/ui-kit/theme-exeris — Evidence Orange', () => {
  const evidence = srgbOf(BRAND_COLOURS['--ex-evidence']);

  it('is painted only by the evidence modifiers', () => {
    // The brand reserves it for verified results; a rule that used it as an accent would make
    // an ordinary control read as a measured claim.
    const painters = [];
    postcss.parse(themeExerisCss).walkDecls((decl) => {
      if (decl.prop === '--ex-evidence') return;
      if (!/--ex-evidence\b|#f59e0b|245\s+158\s+11\b/i.test(decl.value)) return;
      painters.push(...decl.parent.selectors);
    });
    expect(painters.length, 'nothing paints Evidence Orange, so the modifiers are dead').toBeGreaterThan(0);
    for (const selector of painters) {
      const classes = [...selector.matchAll(/\.(exeris-[a-z0-9-]+)/g)].map(([, n]) => n);
      expect(classes.length > 0 && classes.every((n) => EVIDENCE_MODIFIERS.includes(n)),
        `${selector} paints Evidence Orange`).toBe(true);
    }
    expect(new Set(painters.map((sel) => sel.match(/\.(exeris-[a-z0-9-]+)/)[1]))).toEqual(new Set(EVIDENCE_MODIFIERS));
  });

  it('is carried by no kit token, and the warning colour is a distinct hue', () => {
    for (const [name, value] of scoped) {
      if (!name.startsWith('--exeris-') || !/^\d{1,3} \d{1,3} \d{1,3}$/.test(value)) continue;
      const colour = srgbOf(`#${value.split(' ').map((c) => Number(c).toString(16).padStart(2, '0')).join('')}`);
      expect(distance(colour, evidence), `${name} reads as Evidence Orange`).toBeGreaterThan(0.1);
    }
    // The brand token itself, as well as its channel: the overrides read --ex-warn directly.
    const warn = srgbOf(scoped.get('--ex-warn'));
    expect(distance(warn, evidence)).toBeGreaterThan(0.1);
  });
});

describe('@exeris/ui-kit/theme-exeris — compiled with Tailwind v4', () => {
  const SHEETS = ['src/styles/theme.css', 'src/styles/index.css', 'src/styles/preview.css'];
  let base;
  let branded;

  beforeAll(async () => {
    const candidates = [...classesIn(`${indexCss}\n${previewCss}`)];
    base = await compileWithV4(SHEETS, candidates);
    branded = await compileWithV4([...SHEETS, 'src/styles/theme-exeris.css'], candidates);
  }, 60_000);

  it('leaves the default :root tokens exactly as they are', () => {
    expect([...branded.rootVars]).toEqual([...base.rootVars]);
  });

  it('emits the token block unlayered and after the default .dark block it overrides', () => {
    let tokenRule;
    postcss.parse(branded.css).walkRules((rule) => {
      if (!tokenRule && rule.selector.startsWith(SCOPE) && rule.selector.includes('.dark')) tokenRule = rule;
    });
    expect(tokenRule, 'the token block is missing from the compiled output').toBeDefined();
    expect(tokenRule.parent.type, 'inside a layer it would lose to the unlayered default tokens').toBe('root');
    const darkAt = branded.css.indexOf('\n.dark {');
    expect(darkAt).toBeGreaterThanOrEqual(0);
    expect(branded.css.indexOf(tokenRule.selector)).toBeGreaterThan(darkAt);
  });

  it('carries every brand primitive', () => {
    for (const name of PRIMITIVES) {
      expect(branded.css, `.${name} is missing`).toMatch(new RegExp(`\\.${name}(?![\\w-])`));
    }
  });

  it('stops the LED pulse for a user who asked for reduced motion', () => {
    let steady = false;
    postcss.parse(branded.css).walkAtRules('media', (media) => {
      if (!media.params.includes('prefers-reduced-motion: reduce')) return;
      media.walkRules((rule) => {
        if (rule.selector.includes('.exeris-led-flow') && rule.selector.includes('.exeris-led-live')) {
          rule.walkDecls('animation', (decl) => { steady = steady || decl.value === 'none'; });
        }
      });
    });
    expect(steady).toBe(true);
    expect(branded.css).toContain('@keyframes exeris-led-pulse');
  });
});

describe('@exeris/ui-kit/theme-exeris — preview tier', () => {
  it('adds no name to the frozen snapshot', () => {
    const frozen = read('tests/public-surface.txt').split('\n').map((line) => line.trim());
    const existing = classesIn(`${indexCss}\n${previewCss}`);
    const introduced = [...classesIn(themeExerisCss)].filter((name) => !existing.has(name));
    expect(introduced.sort()).toEqual([...PRIMITIVES].sort());
    expect(introduced.filter((name) => frozen.includes(`class:${name}`)), 'a brand primitive is pinned as frozen')
      .toEqual([]);
    expect(frozen.filter((line) => line.includes('--ex-')), 'a brand token is pinned as frozen').toEqual([]);
  });

  it('introduces no --exeris-* property the default theme lacks', () => {
    // A new --exeris-* name would read as a frozen token name; the brand's own names are --ex-*.
    const introduced = [...scoped.keys()].filter((name) => name.startsWith('--exeris-') && !defaultTokens.has(name));
    expect(introduced).toEqual([]);
  });
});
