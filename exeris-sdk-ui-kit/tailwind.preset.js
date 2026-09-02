/**
 * Exeris UI Kit Tailwind Configuration
 *
 * Extend your project's tailwind.config.js with this preset.
 *
 * Usage in tailwind.config.js:
 *   import exerisPreset from '@exeris-systems/ui-kit/tailwind.preset.js';
 *   export default {
 *     presets: [exerisPreset],
 *     // ... your config
 *   };
 */

/** @type {import('tailwindcss').Config} */
export default {
  /**
   * Dark mode follows the `.dark` class, not the operating system.
   *
   * This package has two dark surfaces and both answer to that one class: the
   * `--exeris-*` design tokens, whose dark values live under a `.dark` selector,
   * and the `.exeris-*` component classes, whose dark styling is written with
   * `dark:` variants. Tailwind's default for `dark:` is
   * `@media (prefers-color-scheme: dark)`, so without this setting the component
   * chrome would ignore the class the README tells you to toggle and follow the
   * OS instead.
   *
   * v4 has no JS preset, so the v4 half of the package declares the equivalent in
   * `src/styles/theme.css` (`@custom-variant dark (&:where(.dark, .dark *))`).
   * Both are asserted against compiler output in `tests/dark-mode-signal.test.js`.
   *
   * A consumer that wants the OS signal overrides it — `darkMode: 'media'` in
   * their own `tailwind.config.js` wins over a preset, as does a custom strategy
   * such as `['selector', '[data-theme="dark"]']`. Both overrides are asserted
   * too, so the escape hatch cannot rot.
   */
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        exeris: {
          primary: 'rgb(var(--exeris-primary) / <alpha-value>)',
          'primary-hover': 'rgb(var(--exeris-primary-hover) / <alpha-value>)',
          secondary: 'rgb(var(--exeris-secondary) / <alpha-value>)',
          success: 'rgb(var(--exeris-success) / <alpha-value>)',
          warning: 'rgb(var(--exeris-warning) / <alpha-value>)',
          danger: 'rgb(var(--exeris-danger) / <alpha-value>)',
          info: 'rgb(var(--exeris-info) / <alpha-value>)',
        },
      },
      fontFamily: {
        exeris: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'BlinkMacSystemFont',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
      spacing: {
        'exeris-xs': 'var(--exeris-spacing-xs)',
        'exeris-sm': 'var(--exeris-spacing-sm)',
        'exeris-md': 'var(--exeris-spacing-md)',
        'exeris-lg': 'var(--exeris-spacing-lg)',
        'exeris-xl': 'var(--exeris-spacing-xl)',
      },
      borderRadius: {
        'exeris-sm': 'var(--exeris-radius-sm)',
        'exeris-md': 'var(--exeris-radius-md)',
        'exeris-lg': 'var(--exeris-radius-lg)',
        'exeris-xl': 'var(--exeris-radius-xl)',
      },
      boxShadow: {
        'exeris-sm': 'var(--exeris-shadow-sm)',
        'exeris': 'var(--exeris-shadow)',
        'exeris-md': 'var(--exeris-shadow-md)',
        'exeris-lg': 'var(--exeris-shadow-lg)',
      },
      transitionDuration: {
        'exeris-fast': 'var(--exeris-transition-fast)',
        'exeris-normal': 'var(--exeris-transition-normal)',
        'exeris-slow': 'var(--exeris-transition-slow)',
      },
      animation: {
        'exeris-spin': 'spin 1s linear infinite',
        'exeris-pulse': 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'exeris-fade-in': 'fadeIn 0.2s ease-out',
        'exeris-slide-up': 'slideUp 0.3s ease-out',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
};

