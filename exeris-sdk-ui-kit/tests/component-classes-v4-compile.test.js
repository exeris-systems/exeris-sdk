import { beforeAll, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { COMPONENT_TYPE_CLASS, RENDERABLE_CLASSES } from './support/component-types.js';
import { PACKAGE_ROOT, compileWithV4, rulesFor, selectorsFor } from './support/tailwind.js';

/**
 * The `.exeris-*` component layer, compiled through Tailwind v4.
 *
 * This guard asserts that v4 emits every component class, at rest and not only in
 * some state. The button variants share their base declarations through a selector
 * list, not `@apply` of a custom class, which v4 rejects; that they still carry the
 * base is asserted below.
 */
const indexCss = readFileSync(join(PACKAGE_ROOT, 'src/styles/index.css'), 'utf8');

/** Every `.exeris-*` class this stylesheet declares, read off the source. */
const DECLARED = [...new Set([...indexCss.matchAll(/\.(exeris-[a-z0-9-]+)/g)].map(([, name]) => name))];

/** The button family: the classes whose shared base is a selector list. */
const BUTTON_BASE = 'exeris-btn';
const BUTTON_VARIANTS = ['exeris-btn-primary', 'exeris-btn-secondary', 'exeris-btn-danger', 'exeris-btn-ghost'];

/**
 * `display: inline-flex` comes only from the shared button base, so its presence
 * on a variant is what tells us the variant still carries that base — the
 * property the selector list gives every variant.
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

beforeAll(async () => {
  const candidates = [...DECLARED, NONEXISTENT];
  v4 = await compileWithV4('src/styles/index.css', candidates);
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
 * The selector list is only safe while a variant used on its own still behaves
 * like `.exeris-btn .exeris-btn-primary` would.
 */
describe('button variants keep the base declarations', () => {
  const compiled = () => v4;

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

/** Every declaration in the rules that name `className`, nested state rules included. */
function allDeclarations(rules, className) {
  const found = [];
  for (const rule of rulesFor(rules, className)) {
    rule.walkDecls((decl) => found.push({ prop: decl.prop, value: decl.value, selector: decl.parent.selector ?? `@${decl.parent.name}` }));
  }
  return found;
}

/** The text fields: they carry their own border, padding and focus ring under v4's preflight. */
const FIELDS = ['exeris-input', 'exeris-select', 'exeris-textarea'];

/**
 * The classes that take every colour from an `--exeris-*` property. The tinted pairs (alerts,
 * badges, chips, the file button) and the danger red (the danger button, error text) keep
 * Tailwind's palette and are not listed.
 */
const TOKEN_CLASSES = [
  'exeris-btn-primary', 'exeris-btn-secondary', 'exeris-btn-ghost',
  'exeris-input', 'exeris-select', 'exeris-textarea', 'exeris-label', 'exeris-help-text',
  'exeris-checkbox', 'exeris-radio', 'exeris-toggle', 'exeris-range', 'exeris-color',
  'exeris-editor', 'exeris-chips', 'exeris-card', 'exeris-card-header', 'exeris-card-footer',
  'exeris-table', 'exeris-spinner',
];

/** A Tailwind palette shade as v4 compiles it: `var(--color-indigo-600)`. */
const PALETTE_SHADE = /var\(--color-[a-z]+-\d+\)/;

describe('form fields render as fields under v4', () => {
  const compiled = () => v4;

  it.each(FIELDS)('gives %s a border width, not only a border colour', (field) => {
    const decls = unconditionalDeclarations(compiled().rules, field);
    expect(
      decls.some((d) => d.prop === 'border-width' && d.value === '1px'),
      `.${field} names a border colour with no width, and v4's preflight leaves it borderless`,
    ).toBe(true);
  });

  it.each(FIELDS)('gives %s its own padding', (field) => {
    const decls = unconditionalDeclarations(compiled().rules, field);
    expect(decls.some((d) => d.prop === 'padding-inline'), `.${field} has no padding under v4`).toBe(true);
  });

  it.each(FIELDS)('gives %s a focus ring with a width', (field) => {
    const ring = allDeclarations(compiled().rules, field)
      .filter((d) => d.selector.includes(':focus') && d.prop === '--tw-ring-shadow');
    expect(ring.length, `.${field} sets a ring colour on focus but no ring, so focus is invisible`)
      .toBeGreaterThan(0);
  });
});

describe('focus and pointer behaviour', () => {
  it('uses outline-hidden, not outline-none', () => {
    // v4's outline-none sets `outline-style: none`, which also removes the outline in forced-colours
    // mode; outline-hidden keeps a transparent outline there.
    expect(indexCss).not.toMatch(/\boutline-none\b/);
  });

  it('gives the buttons a pointer cursor', () => {
    // v4's preflight no longer sets `cursor: pointer` on buttons.
    expect(unconditionalDeclarations(v4.rules, BUTTON_BASE)
      .some((d) => d.prop === 'cursor' && d.value === 'pointer')).toBe(true);
  });
});

describe('component classes follow the --exeris-* properties', () => {
  const compiled = () => v4;

  it.each(TOKEN_CLASSES)('reads an --exeris-* property in %s', (className) => {
    expect(
      allDeclarations(compiled().rules, className).some((d) => d.value.includes('var(--exeris-')),
      `.${className} reads no --exeris-* property, so theming it does nothing`,
    ).toBe(true);
  });

  it.each(TOKEN_CLASSES)('uses no Tailwind palette shade in %s', (className) => {
    const shades = allDeclarations(compiled().rules, className).filter((d) => PALETTE_SHADE.test(d.value));
    expect(
      shades.map((d) => `${d.selector} { ${d.prop}: ${d.value} }`),
      `.${className} hard-codes a palette colour that neither .dark nor an override reaches`,
    ).toEqual([]);
  });
});

describe('the error state wins over the field base', () => {
  it('declares .exeris-input-error after the shared field rule, with its own border colour', () => {
    // Both rules carry one class, so the later one wins on an element that has both. Moving the
    // error rule above the field base would leave an erroring field with the normal border.
    const ruleStart = (selectorStart) => v4.css.indexOf(selectorStart);
    const base = ruleStart('.exeris-input, .exeris-select, .exeris-textarea {');
    const error = ruleStart('.exeris-input-error {');
    expect(base, 'the shared field rule is missing from the compiled output').toBeGreaterThanOrEqual(0);
    expect(error, '.exeris-input-error comes before the field base it has to override').toBeGreaterThan(base);
    expect(unconditionalDeclarations(v4.rules, 'exeris-input-error')
      .some((d) => d.prop === 'border-color' && d.value.includes('--color-red-500'))).toBe(true);
  });
});
