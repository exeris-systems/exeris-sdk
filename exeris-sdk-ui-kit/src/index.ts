/**
 * Design tokens and component styles a generated Exeris application renders against.
 *
 * @remarks
 * The package ships three artifacts that name the same tokens three ways: CSS custom properties
 * (`src/styles/index.css`), a Tailwind preset (`tailwind.preset.js`), and the TypeScript values
 * below. The names are the contract and are pinned by `tests/public-surface.txt`; the values are
 * the theming surface an application is meant to override.
 *
 * @packageDocumentation
 * @since 0.1
 */

/**
 * Design tokens a generated Angular application reads at build time.
 *
 * @remarks
 * A theme is data, not a class: consumers spread {@link defaultTheme} and override the tokens they
 * need. Every token here has a CSS custom property and a Tailwind preset key of the same name, so
 * a value can be read from whichever of the three surfaces a consumer already uses. Removing or
 * renaming a token is a MAJOR change for this package; adding one is MINOR.
 *
 * @public
 * @since 0.1
 */
export interface ExerisTheme {
  /** Colour tokens by semantic role; each value is a CSS colour string, mirrored by `--exeris-<role>`. */
  colors: {
    /** The brand colour every default action, link and focus ring is drawn in. */
    primary: string;
    /**
     * The hover state of `primary`.
     *
     * @remarks
     * Kebab-cased, matching `--exeris-primary-hover` in `index.css` and the `primary-hover` key in
     * the Tailwind preset. All three name design tokens, and naming them identically is what lets
     * the drift test map one onto another without a translation step — a camel-cased `primaryHover`
     * here would need one, and a translation is a place two sides can disagree.
     */
    'primary-hover': string;
    /** The supporting colour for actions that must not compete with `primary`. */
    secondary: string;
    /** The colour of a completed or accepted outcome. */
    success: string;
    /** The colour of an outcome a user should look at before continuing. */
    warning: string;
    /** The colour of a failed outcome or a destructive action. */
    danger: string;
    /** The colour of neutral, non-urgent information. */
    info: string;
  };
  /** Spacing scale by size step, as CSS lengths, mirrored by `--exeris-spacing-<step>`. */
  spacing: {
    /** The tightest step, for gaps inside a single control. */
    xs: string;
    /** The step between closely related controls. */
    sm: string;
    /** The default step, and the one an unspecified gap falls back to. */
    md: string;
    /** The step between groups within a section. */
    lg: string;
    /** The widest step, for gaps between sections. */
    xl: string;
  };
  /** Border radii by size step, as CSS lengths, mirrored by `--exeris-radius-<step>`. */
  radius: {
    /** The radius of a small control such as a badge or a chip. */
    sm: string;
    /** The default radius, and the one inputs and buttons use. */
    md: string;
    /** The radius of a container such as a card. */
    lg: string;
    /** The radius of a surface that sits above the page, such as a dialog. */
    xl: string;
    /** Fully rounded, for pills and circular controls. */
    full: string;
  };
}

/**
 * The theme every generated application starts from.
 *
 * @remarks
 * Spread it and override the tokens an application changes, rather than assembling an
 * {@link ExerisTheme} from scratch: a token added in a future minor then arrives with a value
 * instead of leaving the object incomplete.
 *
 * @public
 * @since 0.1
 */
export const defaultTheme: ExerisTheme = {
  colors: {
    primary: 'rgb(79, 70, 229)', // indigo-600
    'primary-hover': 'rgb(67, 56, 202)', // indigo-700
    secondary: 'rgb(100, 116, 139)', // slate-500
    success: 'rgb(34, 197, 94)', // green-500
    warning: 'rgb(245, 158, 11)', // amber-500
    danger: 'rgb(239, 68, 68)', // red-500
    info: 'rgb(59, 130, 246)', // blue-500
  },
  spacing: {
    xs: '0.25rem',
    sm: '0.5rem',
    md: '1rem',
    lg: '1.5rem',
    xl: '2rem',
  },
  radius: {
    sm: '0.25rem',
    md: '0.375rem',
    lg: '0.5rem',
    xl: '0.75rem',
    full: '9999px',
  },
};
