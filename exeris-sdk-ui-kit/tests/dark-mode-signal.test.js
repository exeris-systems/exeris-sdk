import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import postcss from 'postcss';
import { PACKAGE_ROOT, compileWithV4 } from './support/tailwind.js';

/**
 * Dark mode responds to a `.dark` class, not the system preference.
 *
 * This package has two dark surfaces, both responding to the same signal:
 * the `--exeris-*` design tokens take their dark values from a `.dark` *class*,
 * and the `.exeris-*` component classes do the same through the
 * `@custom-variant dark` that `theme.css` declares.
 *
 * This guard compiles the documented setup with Tailwind v4 and verifies two
 * invariants: the absence of `prefers-color-scheme` media queries (the failure
 * mode), and the presence of `.dark`-scoped rules (the success case). A consumer
 * who wants the OS signal back declares their own `@custom-variant dark` after
 * importing the theme.
 */
const indexCss = readFileSync(join(PACKAGE_ROOT, 'src/styles/index.css'), 'utf8');

/**
 * A component class whose dark styling is unambiguous, plus a utility a
 * consumer would write in their own markup. Both must follow the same signal:
 * the first covers what this package ships, the second what it lets you build.
 */
const COMPONENT = 'exeris-input';
const CONSUMER_UTILITY = 'dark:bg-exeris-primary';
const CANDIDATES = [COMPONENT, CONSUMER_UTILITY, 'dark'];

/** The v4 setup the README documents: Tailwind, the theme entry, the component layer. */
const V4_SHEETS = ['src/styles/theme.css', 'src/styles/index.css'];

/**
 * Every selector chain in the output that reaches `text`, ancestors included.
 *
 * v4 *nests* the variant inside the base rule (`.exeris-input { &:where(.dark,
 * .dark *) { … } }`), where neither half names the other, so a flat selector
 * index cannot see it. Joining the chain is what lets one assertion see it.
 *
 * `DARK_SCOPE` deliberately refuses to match the escaped *class name* Tailwind
 * generates for a `dark:` utility. `.dark\:bg-exeris-primary` contains the
 * literal `.dark`, so a plain substring test passed whenever the utility was
 * emitted at all — including under `@media (prefers-color-scheme: dark)`, which
 * is the state this guard exists to fail on.
 */
const DARK_SCOPE = /\.dark(?![\w\\-])/;

function chainsMatching(css, text) {
  const chains = [];
  postcss.parse(css).walkRules((rule) => {
    const chain = [];
    for (let node = rule; node; node = node.parent) {
      if (node.type === 'rule') chain.unshift(node.selector);
      else if (node.type === 'atrule') chain.unshift(`@${node.name} ${node.params}`);
    }
    const joined = chain.join(' ');
    if (joined.includes(text) && DARK_SCOPE.test(joined)) chains.push(joined);
  });
  return chains;
}

describe('dark mode answers to the `.dark` class, not the OS', () => {
  let v4;

  beforeAll(async () => {
    v4 = await compileWithV4(V4_SHEETS, CANDIDATES);
  });

  it('the source still declares dark styling worth guarding', () => {
    // Guards the guard: if the `dark:` variants were ever dropped from
    // index.css, every assertion below would pass vacuously.
    expect(indexCss).toMatch(/dark:/);
  });

  describe('Tailwind v4', () => {
    const compiled = () => v4;

    it('emits no prefers-color-scheme query', () => {
      expect(compiled().css).not.toContain('prefers-color-scheme');
    });

    it('routes the component layer through .dark', () => {
      expect(
        chainsMatching(compiled().css, COMPONENT),
        `no .dark-scoped rule for .${COMPONENT}`,
      ).not.toEqual([]);
    });

    it("routes a consumer's own dark: utility through .dark", () => {
      expect(
        chainsMatching(compiled().css, 'bg-exeris-primary'),
        `${CONSUMER_UTILITY} did not compile against .dark`,
      ).not.toEqual([]);
    });
  });
});

/**
 * The theme entry sets a default, not a policy. A consumer who wants the OS
 * signal back — or a different attribute entirely — declares their own `dark`
 * variant after the imports, and theirs must win, because a default that could
 * not be overridden would be a breaking change rather than a new default.
 */
describe('a consumer can override the signal', () => {
  it('a media variant after the imports puts the media query back', async () => {
    const { css } = await compileWithV4(V4_SHEETS, CANDIDATES,
      '@custom-variant dark (@media (prefers-color-scheme: dark));');
    expect(css).toContain('prefers-color-scheme');
  });

  it('a custom selector variant wins too', async () => {
    const { css } = await compileWithV4(V4_SHEETS, CANDIDATES,
      '@custom-variant dark (&:where([data-theme="dark"], [data-theme="dark"] *));');
    expect(css).not.toContain('prefers-color-scheme');
    expect(css).toContain('[data-theme="dark"]');
  });
});
