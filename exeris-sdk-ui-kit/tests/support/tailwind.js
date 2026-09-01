import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import tailwindV4 from '@tailwindcss/postcss';

/**
 * Shared plumbing for the guards that put this package's CSS through a real
 * Tailwind: `tailwind-v4-compile.test.js` (the `@theme` token entry) and
 * `component-classes-v4-compile.test.js` (the `.exeris-*` component layer, on
 * both majors).
 *
 * The package's own `tailwindcss` devDep is v3 and stays that way — `index.css`
 * is written against v3 and the preset is a v3 preset. v4 comes in through
 * `@tailwindcss/postcss`, which nests its own copy of the engine, so both majors
 * are installed at once without an alias, and the same stylesheet can be put
 * through each of them in one test run.
 */
export const PACKAGE_ROOT = dirname(dirname(dirname(fileURLToPath(import.meta.url))));

const require = createRequire(import.meta.url);

/**
 * v4's CSS entry, resolved from the copy `@tailwindcss/postcss` itself loads.
 * A consumer writes `@import "tailwindcss"`; here that bare specifier would hit
 * the top-level v3 devDep (which has no `index.css`), so the absolute path to
 * the engine's own entry is what keeps the two halves on one version.
 */
export const V4_ENTRY = require.resolve('tailwindcss/index.css', {
  paths: [require.resolve('@tailwindcss/postcss')],
});

export const V4_VERSION = require(
  require.resolve('tailwindcss/package.json', { paths: [require.resolve('@tailwindcss/postcss')] }),
).version;

/** The `tailwindcss` range this package declares as a devDep (expected to be v3). */
export const DECLARED_V3_RANGE = require(join(PACKAGE_ROOT, 'package.json')).devDependencies.tailwindcss;

/**
 * Compiles one of this package's stylesheets with v4 and returns the output plus
 * an index of its rules.
 *
 * `source(none)` turns off automatic file scanning, so the only candidates are
 * the ones passed in. Without it Tailwind would harvest class names out of this
 * package's own docs and tests, and something could appear to compile because a
 * comment mentioned it.
 */
export async function compileWithV4(stylesheet, candidates = []) {
  const input = [
    `@import "${V4_ENTRY}" source(none);`,
    `@import "${join(PACKAGE_ROOT, stylesheet)}";`,
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

/**
 * The same stylesheet through the package's *own* Tailwind — v3, the major
 * `index.css` is written against. Used to assert that a change made for v4's
 * sake did not move v3, which is the majority consumer today.
 *
 * v3 needs the candidates up front (there is no `@source inline`), and it reads
 * the preset the way a consumer's `tailwind.config.js` would.
 */
export async function compileWithV3(stylesheet, candidates = []) {
  const { default: tailwindV3 } = await import('tailwindcss');
  const { default: preset } = await import('../../tailwind.preset.js');
  const config = {
    presets: [preset],
    content: [{ raw: candidates.join(' '), extension: 'html' }],
  };
  const file = join(PACKAGE_ROOT, stylesheet);
  const { css } = await postcss([tailwindV3(config)]).process(readFileSync(file, 'utf8'), { from: file });

  return { css, rules: index(css) };
}
