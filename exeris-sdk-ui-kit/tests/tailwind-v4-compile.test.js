import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { PACKAGE_ROOT, THEME_UTILITIES, V4_VERSION, compileWithV4 } from './support/tailwind.js';

/**
 * The `src/styles/theme.css` Tailwind v4 `@theme` entry, compiled by a real v4.
 *
 * The companion `tests/theme.test.js` verifies the theme entry's shape at the text
 * level. This test verifies the stronger property: that v4 turns every `exeris`
 * variable in the `@theme` block into the utility the README documents, and that
 * each utility still reaches the `--exeris-*` runtime property its variable names —
 * the property a host overrides to re-theme the application.
 */
const root = PACKAGE_ROOT;

/** Every utility the `@theme` block implies — see `THEME_UTILITIES`. */
const EXPECTED = THEME_UTILITIES;

/** Candidates with no token behind them — the compile must ignore them. */
const NONEXISTENT = ['bg-exeris-nonesuch', 'p-exeris-nonesuch', 'rounded-exeris-nonesuch', 'duration-exeris-nonesuch'];

let rules;
let rootVars;

beforeAll(async () => {
  ({ rules, rootVars } = await compileWithV4('src/styles/theme.css', [
    ...EXPECTED.map((entry) => entry.utility),
    ...NONEXISTENT,
  ]));
}, 60_000);

const rulesForUtility = (utility) => rules.get(`.${utility}`) ?? [];

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
  const text = rulesForUtility(utility)
    .flatMap((rule) => rule.nodes.filter((node) => node.type === 'decl'))
    .map((node) => node.value)
    .join(' ');
  return text.replace(/var\((--[a-z0-9-]+)\)/g, (whole, name) =>
    name.startsWith('--exeris-') ? whole : rootVars.get(name) ?? whole,
  );
}

describe('theme.css through a real Tailwind v4 compile', () => {
  it('runs against Tailwind v4', () => {
    expect(V4_VERSION, `resolved Tailwind for the compile guard is ${V4_VERSION}`).toMatch(/^4\./);
  });

  it('generates every utility the @theme block declares', () => {
    const missing = EXPECTED.filter((e) => rulesForUtility(e.utility).length === 0).map((e) => e.utility);
    expect(missing, 'v4 produced no rule for these @theme variables').toEqual([]);
    expect(EXPECTED.length, 'the @theme walk looks too short to be real').toBeGreaterThan(20);
  });

  it('ignores candidates with no token behind them', () => {
    const spurious = NONEXISTENT.filter((name) => rulesForUtility(name).length > 0);
    expect(spurious, 'these have no @theme entry, so a rule for them means the guard proves nothing').toEqual([]);
  });

  it('resolves each utility against the runtime property its @theme variable names', () => {
    for (const { utility, runtimeVar } of EXPECTED.filter((e) => e.runtimeVar)) {
      expect(resolved(utility), `${utility} does not read ${runtimeVar}, so a host cannot re-theme it at runtime`)
        .toContain(`var(${runtimeVar})`);
    }
  });

  it('indirects every family but the font stack and the animation shorthands', () => {
    // The literal families are a decision, not an accident: a font stack and an
    // animation shorthand are not values a host re-themes per tenant, so they have
    // no `--exeris-*` property. Everything else must, or it is not themeable.
    const literal = EXPECTED.filter((e) => !e.runtimeVar).map((e) => e.themeVar);
    expect(literal.every((name) => name.startsWith('--font-') || name.startsWith('--animate-')),
      `only fonts and animations may be literal, found: ${literal.join(', ')}`).toBe(true);
    for (const { utility } of EXPECTED.filter((e) => !e.runtimeVar)) {
      expect(resolved(utility), `${utility} is literal in @theme but indirects through a runtime property`)
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
    const repointed = EXPECTED.filter((e) => e.themeVar.startsWith('--color-exeris-'))
      .map((e) => e.key.replace(/^exeris-/, ''))
      .filter((key) => new RegExp(`--exeris-${key}:`).test(darkBlock));
    expect(repointed, 'expected .dark to re-point at least one @theme colour').not.toEqual([]);

    for (const key of repointed) {
      expect(darkBlock, `.dark re-points --exeris-${key} but does not recompute --color-exeris-${key}, so bg-exeris-${key} stays light`)
        .toContain(`--color-exeris-${key}: rgb(var(--exeris-${key}))`);
    }
  });
});
