/**
 * `eu.exeris.sdk.sourcemodel.ast.UIMetadata.ComponentType` → the `.exeris-*`
 * class a generator binds to it. This map is the contract codegen-ts targets.
 *
 * Shared by the two guards that need it: `component-classes.test.js` (the class
 * is declared in `index.css`) and `component-classes-v4-compile.test.js` (a
 * Tailwind v4 build actually emits it). Keep it in sync with the Java enum — a
 * new ComponentType should arrive here with its class, or null when it renders
 * no control of its own.
 *
 * null = no dedicated class: AUTO is resolved by codegen, HIDDEN renders
 * nothing, and CUSTOM defers to an application-supplied component named by
 * `UIFieldMetadata.customComponent`.
 */
export const COMPONENT_TYPE_CLASS = {
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

/** The distinct classes a renderable ComponentType can map to. */
export const RENDERABLE_CLASSES = [...new Set(Object.values(COMPONENT_TYPE_CLASS).filter(Boolean))];
