import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import tailwindV4 from '@tailwindcss/postcss';

/**
 * Shared plumbing for the guards that put this package's CSS through a real
 * Tailwind v4: `tailwind-v4-compile.test.js` (the `@theme` token entry),
 * `component-classes-v4-compile.test.js` (the `.exeris-*` component layer) and
 * `dark-mode-signal.test.js`.
 *
 * It also reads the `@theme` block of `src/styles/theme.css`, which is the single
 * source of truth for the utility namespace: every `exeris` utility a consumer can
 * write exists because a `--<namespace>-exeris…` variable is declared there.
 */
export const PACKAGE_ROOT = dirname(dirname(dirname(fileURLToPath(import.meta.url))));

const require = createRequire(import.meta.url);

/**
 * v4's CSS entry, resolved from the copy `@tailwindcss/postcss` itself loads, so
 * the compiler and the stylesheet it imports are always the same version.
 */
export const V4_ENTRY = require.resolve('tailwindcss/index.css', {
  paths: [require.resolve('@tailwindcss/postcss')],
});

export const V4_VERSION = require(
  require.resolve('tailwindcss/package.json', { paths: [require.resolve('@tailwindcss/postcss')] }),
).version;

/** The body of the first `<opener> {` … `}` block in `css`, comments and all. */
export function block(css, opener) {
  const start = css.indexOf(`${opener} {`);
  if (start < 0) throw new Error(`no '${opener}' block found`);
  let depth = 0;
  for (let i = css.indexOf('{', start); i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    else if (css[i] === '}') {
      depth -= 1;
      if (depth === 0) return css.slice(css.indexOf('{', start) + 1, i);
    }
  }
  throw new Error(`unterminated '${opener}' block`);
}

/** `--name: value` pairs in a block, comments stripped and whitespace collapsed. */
export function declarations(body) {
  const found = new Map();
  for (const [, name, value] of body.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    found.set(name, value.trim().replace(/\s+/g, ' '));
  }
  return found;
}

/** The `@theme` declarations of `src/styles/theme.css`, by variable name. */
export const THEME = declarations(block(readFileSync(join(PACKAGE_ROOT, 'src/styles/theme.css'), 'utf8'), '@theme'));

/**
 * Each `@theme` namespace this package fills, and the utility a variable in it
 * produces. `exeris` is the prefix every key carries, so `--spacing-exeris-md`
 * is the key `exeris-md` and produces `p-exeris-md`.
 */
export const NAMESPACES = [
  { prefix: '--color-', utility: (k) => `bg-${k}` },
  { prefix: '--font-', utility: (k) => `font-${k}` },
  { prefix: '--spacing-', utility: (k) => `p-${k}` },
  { prefix: '--radius-', utility: (k) => `rounded-${k}` },
  { prefix: '--shadow-', utility: (k) => `shadow-${k}` },
  { prefix: '--transition-duration-', utility: (k) => `duration-${k}` },
  { prefix: '--animate-', utility: (k) => `animate-${k}` },
];

/**
 * Every `exeris` utility the `@theme` block implies: the utility, the theme
 * variable behind it, its declared value, and the `--exeris-*` runtime property
 * the value reads (null where the theme writes it literally).
 */
export const THEME_UTILITIES = NAMESPACES.flatMap(({ prefix, utility }) =>
  [...THEME].filter(([name]) => name.startsWith(`${prefix}exeris`)).map(([name, value]) => {
    const key = name.slice(prefix.length);
    return {
      key,
      utility: utility(key),
      themeVar: name,
      value,
      runtimeVar: (value.match(/--exeris-[a-z0-9-]+/) ?? [null])[0],
    };
  }),
);

/**
 * Compiles one — or, for a setup that needs both entries, several — of this
 * package's stylesheets with v4 and returns the output plus an index of its
 * rules.
 *
 * `source(none)` turns off automatic file scanning, so the only candidates are
 * the ones passed in. Without it Tailwind would harvest class names out of this
 * package's own docs and tests, and something could appear to compile because a
 * comment mentioned it.
 */
export async function compileWithV4(stylesheet, candidates = [], consumerCss = '') {
  const sheets = Array.isArray(stylesheet) ? stylesheet : [stylesheet];
  const input = [
    `@import "${V4_ENTRY}" source(none);`,
    ...sheets.map((sheet) => `@import "${join(PACKAGE_ROOT, sheet)}";`),
    // What a consumer writes after the imports, in the same stylesheet.
    consumerCss,
    candidates.length ? `@source inline("${candidates.join(' ')}");` : '',
  ].filter(Boolean).join('\n');

  const { css } = await postcss([tailwindV4()]).process(input, {
    from: join(PACKAGE_ROOT, 'v4-compile-probe.css'),
  });

  const rootVars = new Map();
  postcss.parse(css).walkRules((rule) => {
    if (/(^|,\s*):root(\s|,|$)/.test(rule.selector)) {
      rule.walkDecls((decl) => rootVars.set(decl.prop, decl.value));
    }
  });

  return { css, rules: index(css), rootVars };
}

/**
 * Selector → every rule carrying it. A list rather than one rule per selector:
 * the same selector legitimately appears more than once (a base declaration and
 * its `dark:` counterpart inside `@media`, say), and keeping only the last of
 * them silently loses declarations.
 */
function index(css) {
  const rules = new Map();
  postcss.parse(css).walkRules((rule) => {
    const existing = rules.get(rule.selector);
    if (existing) existing.push(rule);
    else rules.set(rule.selector, [rule]);
  });
  return rules;
}

/**
 * Every selector in the compiled output that targets `.<className>`, whether on
 * its own, in a selector list, or carrying a pseudo-class or nested context.
 */
export function selectorsFor(rules, className) {
  const pattern = new RegExp(`\\.${className}(?![\\w-])`);
  return [...rules.keys()].filter((selector) => pattern.test(selector));
}

/** Flattens `rules` back to every rule that mentions `.<className>`. */
export function rulesFor(rules, className) {
  return selectorsFor(rules, className).flatMap((selector) => rules.get(selector));
}
