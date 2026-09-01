import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { COMPONENT_TYPE_CLASS } from './support/component-types.js';

/**
 * B3 — generated forms must not be unstyled for most field types. Every
 * `ComponentType` a generator can emit needs a styled `.exeris-*` class to bind
 * to; `COMPONENT_TYPE_CLASS` (in `tests/support/component-types.js`) is that
 * contract. This test asserts each class is actually declared in `index.css`, so
 * a rename or deletion cannot silently leave a control bare.
 *
 * Its companion, `component-classes-v4-compile.test.js`, asserts the stronger
 * thing: that a real Tailwind build — v3 *and* v4 — emits a rule for each.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');

function classIsDefined(name) {
  // `.name {` or `.name,` — the button base is declared as a selector list, so a
  // brace is not the only thing that can follow a declared class.
  return new RegExp(`\\.${name}(?![\\w-])\\s*[,{]`).test(indexCss);
}

describe('form-control component classes (B3)', () => {
  it('covers the full ComponentType enum (28 kinds)', () => {
    expect(Object.keys(COMPONENT_TYPE_CLASS)).toHaveLength(28);
  });

  it('defines a styled class in index.css for every renderable ComponentType', () => {
    for (const [type, className] of Object.entries(COMPONENT_TYPE_CLASS)) {
      if (className === null) {
        continue; // AUTO / HIDDEN render no dedicated control
      }
      expect(classIsDefined(className), `${type} maps to .${className}, which must be defined in index.css`)
        .toBe(true);
    }
  });

  it('ships the new control classes B3 added beyond .exeris-input', () => {
    for (const name of [
      'exeris-select', 'exeris-textarea', 'exeris-checkbox', 'exeris-radio', 'exeris-radio-group',
      'exeris-toggle', 'exeris-range', 'exeris-file', 'exeris-color', 'exeris-editor',
      'exeris-rating', 'exeris-chips', 'exeris-chip',
    ]) {
      expect(classIsDefined(name), `.${name} must be defined`).toBe(true);
    }
  });

  it('renders the toggle thumb (switch needs the ::before pseudo-element)', () => {
    expect(indexCss).toContain('.exeris-toggle::before');
    expect(indexCss).toContain('.exeris-toggle:checked::before');
  });
});
