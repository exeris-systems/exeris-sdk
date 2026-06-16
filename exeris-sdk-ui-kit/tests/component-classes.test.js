import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * B3 — generated forms must not be unstyled for most field types. Every
 * `ComponentType` (eu.exeris.sdk.sourcemodel.ast.UIMetadata.ComponentType) a
 * generator can emit needs a styled `.exeris-*` class to bind to. This map is
 * the contract codegen-ts targets; the test asserts each class is actually
 * defined in index.css so a rename/deletion can't silently leave a control bare.
 *
 * Keep COMPONENT_TYPE_CLASS in sync with the Java enum — a new ComponentType
 * should arrive here with its class (or null when it renders no control).
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');

// null = no dedicated class (AUTO is resolved by codegen; HIDDEN renders nothing;
// CUSTOM defers to an application-supplied component named by UIFieldMetadata.customComponent).
const COMPONENT_TYPE_CLASS = {
  AUTO: null,
  HIDDEN: null,
  CUSTOM: null,
  TEXT_INPUT: 'exeris-input',
  NUMBER_INPUT: 'exeris-input',
  DATE_PICKER: 'exeris-input',
  DATETIME_PICKER: 'exeris-input',
  TIME_PICKER: 'exeris-input',
  PASSWORD: 'exeris-input',
  EMAIL: 'exeris-input',
  PHONE: 'exeris-input',
  URL: 'exeris-input',
  CURRENCY: 'exeris-input',
  AUTOCOMPLETE: 'exeris-input',
  TEXT_AREA: 'exeris-textarea',
  SELECT: 'exeris-select',
  MULTI_SELECT: 'exeris-select',
  CHECKBOX: 'exeris-checkbox',
  RADIO_GROUP: 'exeris-radio',
  TOGGLE: 'exeris-toggle',
  SLIDER: 'exeris-range',
  FILE_UPLOAD: 'exeris-file',
  IMAGE_UPLOAD: 'exeris-file',
  RICH_TEXT_EDITOR: 'exeris-editor',
  CODE_EDITOR: 'exeris-editor',
  COLOR_PICKER: 'exeris-color',
  RATING: 'exeris-rating',
  CHIPS: 'exeris-chips',
};

function classIsDefined(name) {
  // matches `.name {` in the components layer (the only place exeris-* classes are declared)
  return new RegExp(`\\.${name}\\s*\\{`).test(indexCss);
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
