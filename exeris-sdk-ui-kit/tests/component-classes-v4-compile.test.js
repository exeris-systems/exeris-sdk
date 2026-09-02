import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { COMPONENT_TYPE_CLASS, RENDERABLE_CLASSES } from './support/component-types.js';
import { PACKAGE_ROOT, compileWithV3, compileWithV4, rulesFor, selectorsFor } from './support/tailwind.js';

/**
 * B3's open half: the `.exeris-*` component layer, put through a real compiler
 * on **both** majors.
 *
 * `index.css` was v4-unreachable, and the reason turned out to be one construct,
 * not the file's shape: `@apply` of a *custom* class ("Cannot apply unknown
 * utility class"). v4 tolerates the `@tailwind base/components/utilities`
 * directives at the top — they are no-ops there — and understands everything
 * else in the file, including `@layer components`, `@apply` of real utilities,
 * arbitrary values like `duration-[var(--exeris-transition-fast)]`, and the
 * `dark:` / `sm:` / `focus:` variants. The four `@apply exeris-btn` lines in the
 * button variants were the whole blocker; they are now a selector list, which
 * needs no v4-only syntax and no second copy of the base declarations.
 *
 * So this guard has two jobs. That v4 emits the classes at all, and that v3 —
 * the major this file is written against and the one consumers are on today —
 * still gets exactly what it got before. The second is why the button
 * assertions below run on both compilers rather than just the new one.
 */
const indexCss = readFileSync(join(PACKAGE_ROOT, 'src/styles/index.css'), 'utf8');

/** Every `.exeris-*` class this stylesheet declares, read off the source. */
const DECLARED = [...new Set([...indexCss.matchAll(/\.(exeris-[a-z0-9-]+)/g)].map(([, name]) => name))];

/** The button family, the one place the v4 blocker lived. */
const BUTTON_BASE = 'exeris-btn';
const BUTTON_VARIANTS = ['exeris-btn-primary', 'exeris-btn-secondary', 'exeris-btn-danger', 'exeris-btn-ghost'];

/**
 * `display: inline-flex` comes only from the shared button base, so its presence
 * on a variant is what tells us the variant still carries that base — the
 * property `@apply exeris-btn` used to provide and the selector list has to keep.
 */
const BASE_MARKER = { prop: 'display', value: 'inline-flex' };

/** A name nothing declares — if a rule appears for it, the guard proves nothing. */
const NONEXISTENT = 'exeris-nonesuch';

/**
 * Every declaration a class picks up from rules that apply unconditionally —
 * the selector targets the bare class (not a pseudo-class or a descendant form)
 * and sits outside any `@media`, so it is what the element gets at rest.
 */
function unconditionalDeclarations(rules, className) {
  const found = [];
  for (const rule of rulesFor(rules, className)) {
    const targetsPlainly = rule.selector
      .split(',')
      .map((part) => part.trim())
      .some((part) => part === `.${className}`);
    if (!targetsPlainly) continue;
    if (rule.parent && rule.parent.type === 'atrule' && rule.parent.name === 'media') continue;
    rule.walkDecls((decl) => found.push({ prop: decl.prop, value: decl.value }));
  }
  return found;
}

const carriesBase = (rules, className) =>
  unconditionalDeclarations(rules, className)
    .some((decl) => decl.prop === BASE_MARKER.prop && decl.value === BASE_MARKER.value);

let v4;
let v3;

beforeAll(async () => {
  const candidates = [...DECLARED, NONEXISTENT];
  [v4, v3] = await Promise.all([
    compileWithV4('src/styles/index.css', candidates),
    compileWithV3('src/styles/index.css', candidates),
  ]);
}, 60_000);

describe('index.css compiles on Tailwind v4', () => {
  it('emits a rule for every .exeris-* class the stylesheet declares', () => {
    const missing = DECLARED.filter((name) => selectorsFor(v4.rules, name).length === 0);
    expect(missing, 'v4 produced no rule for these, so a consumer on v4 gets unstyled markup').toEqual([]);
    expect(DECLARED.length, 'the declared-class list looks too short to be real').toBeGreaterThan(40);
  });

  it('styles every renderable ComponentType at rest, not only in some state', () => {
    // Deliberately stricter than "a rule mentions it": a class that exists only
    // as `.exeris-toggle:checked` or `.exeris-toggle::before` would satisfy the
    // looser check while the control renders bare in its default state.
    for (const className of RENDERABLE_CLASSES) {
      const types = Object.entries(COMPONENT_TYPE_CLASS)
        .filter(([, mapped]) => mapped === className)
        .map(([type]) => type);
      expect(
        unconditionalDeclarations(v4.rules, className).length,
        `${types.join('/')} bind to .${className}, which v4 emits no unconditional declarations for`,
      ).toBeGreaterThan(0);
    }
  });

  it('emits nothing for a class the stylesheet does not declare', () => {
    expect(selectorsFor(v4.rules, NONEXISTENT), 'a rule for an undeclared class means this guard proves nothing')
      .toEqual([]);
  });

  it('carries the design tokens, so the components it styles are themeable', () => {
    expect(v4.rootVars.get('--exeris-primary'), 'a v4 consumer importing the stylesheet gets no tokens').toBeDefined();
    expect(v4.rules.has('.dark'), 'the dark token scope did not survive the v4 compile').toBe(true);
  });
});

/**
 * The selector-list refactor is only safe while a variant used on its own still
 * behaves like `.exeris-btn .exeris-btn-primary` did. Asserted on both compilers
 * because the change was made for v4 and the risk lands on v3.
 */
describe.each([
  ['v4', () => v4],
  ['v3', () => v3],
])('button variants keep the base declarations (%s)', (_major, compiled) => {
  it('gives the base class the base declarations', () => {
    expect(carriesBase(compiled().rules, BUTTON_BASE)).toBe(true);
  });

  it.each(BUTTON_VARIANTS)('gives %s the base declarations without the base class present', (variant) => {
    expect(
      carriesBase(compiled().rules, variant),
      `.${variant} lost the shared button base — markup using the variant alone renders unstyled`,
    ).toBe(true);
  });

  it.each(BUTTON_VARIANTS)('still gives %s its own distinguishing declarations', (variant) => {
    const own = unconditionalDeclarations(compiled().rules, variant)
      .filter((decl) => decl.prop === 'background-color' || decl.prop === 'color');
    expect(own.length, `.${variant} has no colour of its own, so every variant renders alike`).toBeGreaterThan(0);
  });
});
