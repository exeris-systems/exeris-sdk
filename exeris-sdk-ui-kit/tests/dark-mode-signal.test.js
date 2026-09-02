import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import postcss from 'postcss';
import { PACKAGE_ROOT, compileWithV3, compileWithV4 } from './support/tailwind.js';

/**
 * One switch for dark mode, on both majors.
 *
 * <h2>What was wrong</h2>
 * This package has two dark surfaces, and until now they answered to different
 * signals. The `--exeris-*` design tokens take their dark values from a `.dark`
 * *class* — that is what the README tells you to toggle, and it is how the
 * token block in `index.css` and `theme.css` is written. The `.exeris-*`
 * component classes take theirs from `dark:` variants, and Tailwind's default
 * for `dark:` is `@media (prefers-color-scheme: dark)` on v3 and v4 alike. So
 * an app that toggled `.dark` got dark tokens and light component chrome unless
 * the operating system happened to agree — dark text on a light input, in
 * practice.
 *
 * <h2>What holds it together now</h2>
 * `darkMode: 'class'` in `tailwind.preset.js` for v3, and
 * `@custom-variant dark (&:where(.dark, .dark *))` in `theme.css` for v4, which
 * has no JS preset to read the former from. Two declarations for one rule, in
 * two files, is exactly the shape that drifts, so this guard compiles the real
 * setup a consumer is documented to use on each major and asserts the *absence*
 * of the media query — the failure mode is silent and invisible in the source.
 *
 * <h2>Why `prefers-color-scheme` is the assertion</h2>
 * Asserting a `.dark` selector is present would pass while a media query sat
 * beside it, which is the split this closes. The absence is the invariant; the
 * presence of the class form is asserted alongside it so the test cannot pass
 * on a build that simply emitted no dark styling at all.
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
 * The two majors shape the same rule differently and a flat selector index sees
 * only one of them: v3 emits a sibling rule (`.exeris-input:is(.dark *)`), while
 * v4 *nests* the variant inside the base rule (`.exeris-input { &:where(.dark,
 * .dark *) { … } }`), where neither half names the other. Joining the chain is
 * what lets one assertion run against both.
 *
 * `DARK_SCOPE` deliberately refuses to match the escaped *class name* Tailwind
 * generates for a `dark:` utility. `.dark\:bg-exeris-primary` contains the
 * literal `.dark`, so a plain substring test passed whenever the utility was
 * emitted at all — including under `@media (prefers-color-scheme: dark)`, which
 * is the state this guard exists to fail on. Mutating the preset is what
 * surfaced it: the assertion stayed green with the fix removed.
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
  let v3;
  let v4;

  beforeAll(async () => {
    v3 = await compileWithV3('src/styles/index.css', CANDIDATES);
    v4 = await compileWithV4(V4_SHEETS, CANDIDATES);
  });

  it('the source still declares dark styling worth guarding', () => {
    // Guards the guard: if the `dark:` variants were ever dropped from
    // index.css, every assertion below would pass vacuously.
    expect(indexCss).toMatch(/dark:/);
  });

  describe.each([
    ['v3', () => v3],
    ['v4', () => v4],
  ])('%s', (_major, compiled) => {
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
 * The preset sets a default, not a policy. A consumer who wants the OS signal
 * back — or a different attribute entirely — must be able to say so in their
 * own config, because a preset that could not be overridden would be a
 * breaking change rather than a new default.
 */
describe('a v3 consumer can override the signal', () => {
  it('darkMode: media puts the media query back', async () => {
    const { css } = await compileWithV3('src/styles/index.css', CANDIDATES, { darkMode: 'media' });
    expect(css).toContain('prefers-color-scheme');
  });

  it('a custom selector strategy wins too', async () => {
    const { css } = await compileWithV3('src/styles/index.css', CANDIDATES, {
      darkMode: ['selector', '[data-theme="dark"]'],
    });
    expect(css).not.toContain('prefers-color-scheme');
    expect(css).toContain('[data-theme="dark"]');
  });
});
