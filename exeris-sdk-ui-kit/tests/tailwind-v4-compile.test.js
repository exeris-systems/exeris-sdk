import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import tailwindV4 from '@tailwindcss/postcss';
import preset from '../tailwind.preset.js';

/**
 * The B1 follow-up: `src/styles/theme.css` compiled by a real Tailwind v4.
 *
 * `tests/theme.test.js` guards the same file at the text level — it checks that
 * a declaration is *present*. That is not the same as checking that v4 turns it
 * into the utility the README promises, nor that the utility resolves to the
 * value a v3 consumer would get. Both gaps were real: v4's namespace was never
 * exercised by a compiler here (the package's only `tailwindcss` devDep is v3,
 * and `index.css`'s `@tailwind` directives and `@apply exeris-btn` mean v4
 * cannot compile it), and the text guard's own assertion — v4 literal equals the
 * light-theme channel in `index.css` — was what encoded the bug it missed:
 * hard-coded literals compile fine and make `.dark` a no-op.
 *
 * Everything asserted below is derived from `tailwind.preset.js`, so the v3
 * preset stays the single source of truth for what the namespace contains and
 * for which entries indirect through a runtime `--exeris-*` property.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const require = createRequire(import.meta.url);

/**
 * v4's CSS entry, resolved from the copy `@tailwindcss/postcss` itself loads.
 * A consumer writes `@import "tailwindcss"`; here that bare specifier would hit
 * the top-level v3 devDep (which has no `index.css`), so the absolute path to
 * the engine's own entry is what keeps the two halves on one version.
 */
const V4_ENTRY = require.resolve('tailwindcss/index.css', {
  paths: [require.resolve('@tailwindcss/postcss')],
});
const V4_VERSION = require(
  require.resolve('tailwindcss/package.json', { paths: [require.resolve('@tailwindcss/postcss')] }),
).version;

/** Preset family → the v4 `@theme` namespace and the utility prefix it feeds. */
const FAMILIES = [
  { entries: () => preset.theme.extend.colors.exeris, themeVar: (k) => `--color-exeris-${k}`, utility: (k) => `bg-exeris-${k}` },
  { entries: () => preset.theme.extend.fontFamily, themeVar: (k) => `--font-${k}`, utility: (k) => `font-${k}` },
  { entries: () => preset.theme.extend.spacing, themeVar: (k) => `--spacing-${k}`, utility: (k) => `p-${k}` },
  { entries: () => preset.theme.extend.borderRadius, themeVar: (k) => `--radius-${k}`, utility: (k) => `rounded-${k}` },
  { entries: () => preset.theme.extend.boxShadow, themeVar: (k) => `--shadow-${k}`, utility: (k) => `shadow-${k}` },
  { entries: () => preset.theme.extend.transitionDuration, themeVar: (k) => `--transition-duration-${k}`, utility: (k) => `duration-${k}` },
  { entries: () => preset.theme.extend.animation, themeVar: (k) => `--animate-${k}`, utility: (k) => `animate-${k}` },
];

/**
 * Every utility the preset implies, paired with the theme var behind it and
 * with the runtime property the v3 preset resolves against — `--exeris-shadow-lg`
 * for `boxShadow['exeris-lg']`, and so on. `runtimeVar` is null for the two
 * families the v3 preset writes literally (the font stack and the animation
 * shorthands); v4 must be literal in exactly those places and indirect in all
 * the others, or the two majors disagree about what is themeable at runtime.
 */
const EXPECTED = FAMILIES.flatMap(({ entries, themeVar, utility }) =>
  Object.entries(entries()).map(([key, presetValue]) => ({
    utility: utility(key),
    themeVar: themeVar(key),
    runtimeVar: (String(presetValue).match(/--exeris-[a-z0-9-]+/) ?? [null])[0],
  })),
);

/** Candidates with no token behind them — the compile must ignore them. */
const NONEXISTENT = ['bg-exeris-nonesuch', 'p-exeris-nonesuch', 'rounded-exeris-nonesuch', 'duration-exeris-nonesuch'];

let compiled;
let rules;
let rootVars;

/**
 * `source(none)` turns off automatic file scanning, so the only candidates are
 * the ones named here. Without it Tailwind would harvest class names out of this
 * package's own docs and tests, and a utility could appear to compile because
 * something mentioned it in prose.
 */
beforeAll(async () => {
  const candidates = [...EXPECTED.map((e) => e.utility), ...NONEXISTENT].join(' ');
  const input = [
    `@import "${V4_ENTRY}" source(none);`,
    `@import "${join(root, 'src/styles/theme.css')}";`,
    `@source inline("${candidates}");`,
  ].join('\n');

  const result = await postcss([tailwindV4()]).process(input, { from: join(root, 'v4-compile-probe.css') });
  compiled = result.css;

  rules = new Map();
  rootVars = new Map();
  postcss.parse(compiled).walkRules((rule) => {
    rules.set(rule.selector, rule);
    if (/(^|,\s*):root(\s|,|$)/.test(rule.selector)) {
      rule.walkDecls((decl) => rootVars.set(decl.prop, decl.value));
    }
  });
}, 60_000);

const ruleFor = (utility) => rules.get(`.${utility}`);

/**
 * What a utility's declarations amount to once the compiled `:root` *theme*
 * variables are substituted in. v4 references a theme var for most families but
 * inlines its value for shadows, so comparing the raw declaration would be
 * asserting v4's internals rather than the value the browser ends up with.
 *
 * The `--exeris-*` runtime properties are deliberately left unsubstituted: they
 * are the theming surface, and whether a utility still reaches them is the whole
 * question here.
 */
function resolved(utility) {
  const text = ruleFor(utility).nodes
    .filter((node) => node.type === 'decl')
    .map((node) => node.value)
    .join(' ');
  return text.replace(/var\((--[a-z0-9-]+)\)/g, (whole, name) =>
    name.startsWith('--exeris-') ? whole : rootVars.get(name) ?? whole,
  );
}

describe('theme.css through a real Tailwind v4 compile', () => {
  it('runs against v4, not the package\'s v3 devDep', () => {
    expect(V4_VERSION, `resolved Tailwind for the compile guard is ${V4_VERSION}`).toMatch(/^4\./);
    expect(require(join(root, 'package.json')).devDependencies.tailwindcss, 'the v3 devDep stays: index.css is v3-only')
      .toMatch(/3\./);
  });

  it('generates every utility the v3 preset declares', () => {
    const missing = EXPECTED.filter((e) => !ruleFor(e.utility)).map((e) => e.utility);
    expect(missing, 'v4 produced no rule for these — the @theme namespace does not match the preset').toEqual([]);
    expect(EXPECTED.length).toBeGreaterThan(20);
  });

  it('ignores candidates with no token behind them', () => {
    const spurious = NONEXISTENT.filter((name) => ruleFor(name));
    expect(spurious, 'these have no @theme entry, so a rule for them means the guard proves nothing').toEqual([]);
  });

  it('resolves each utility against the same runtime property the v3 preset uses', () => {
    for (const { utility, runtimeVar } of EXPECTED.filter((e) => e.runtimeVar)) {
      expect(resolved(utility), `${utility} does not read ${runtimeVar}, so it cannot be re-themed at runtime the way v3 can`)
        .toContain(`var(${runtimeVar})`);
    }
  });

  it('leaves the literal entries literal, as the v3 preset has them', () => {
    for (const { utility } of EXPECTED.filter((e) => !e.runtimeVar)) {
      expect(resolved(utility), `${utility} indirects through a runtime property, but the v3 preset writes it literally`)
        .not.toMatch(/var\(--exeris-/);
    }
  });
});

describe('the .dark scope survives the compile and still re-themes', () => {
  const themeCss = readFileSync(join(root, 'src/styles/theme.css'), 'utf8');
  const darkBlock = themeCss.slice(themeCss.indexOf('.dark {'), themeCss.indexOf('}', themeCss.indexOf('.dark {')));

  it('emits the .dark block', () => {
    expect(rules.has('.dark'), 'v4 dropped .dark, so dark mode ships nothing').toBe(true);
  });

  it('recomputes every mapped colour whose channels .dark changes', () => {
    // A custom property's var()s are substituted where it is declared, and the
    // substituted value inherits. So a mapping declared only on :root is already
    // resolved by the time a nested .dark re-points its channels — .dark has to
    // re-declare the mapping to move it. Verified in a browser: without these,
    // bg-exeris-primary inside .dark computes the light colour.
    const repointed = Object.keys(preset.theme.extend.colors.exeris)
      .filter((key) => new RegExp(`--exeris-${key}:`).test(darkBlock));
    expect(repointed, 'expected .dark to re-point at least one preset colour').not.toEqual([]);

    for (const key of repointed) {
      expect(darkBlock, `.dark re-points --exeris-${key} but does not recompute --color-exeris-${key}, so bg-exeris-${key} stays light`)
        .toContain(`--color-exeris-${key}: rgb(var(--exeris-${key}))`);
    }
  });
});
