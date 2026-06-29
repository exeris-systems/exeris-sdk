/**
 * @exeris-systems/ui-kit
 *
 * Exeris UI Kit - Base styles and design tokens for generated components.
 *
 * This package provides:
 * - CSS Design Tokens (CSS Custom Properties)
 * - Tailwind CSS preset with Exeris theme
 * - Base component styles
 *
 * @author Exeris Team
 * @since 0.1.0
 */

// Re-export types for TypeScript consumers
export interface ExerisTheme {
  colors: {
    primary: string;
    secondary: string;
    success: string;
    warning: string;
    danger: string;
    info: string;
  };
  spacing: {
    xs: string;
    sm: string;
    md: string;
    lg: string;
    xl: string;
  };
  radius: {
    sm: string;
    md: string;
    lg: string;
    xl: string;
    full: string;
  };
}

export const defaultTheme: ExerisTheme = {
  colors: {
    primary: 'rgb(79, 70, 229)', // indigo-600
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

