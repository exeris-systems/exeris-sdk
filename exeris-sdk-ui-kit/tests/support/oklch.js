/**
 * Colour conversions for the guard that holds each `--exeris-*` colour of `theme-exeris.css`
 * equal to the sRGB value of the `--ex-*` token it names, which is a hex value or an oklch one.
 *
 * A value outside sRGB is gamut-mapped by the CSS Color 4 algorithm: chroma is reduced by
 * binary search at constant lightness and hue until clipping the candidate moves it less
 * than one just-noticeable difference (deltaEOK 0.02). A plain clip would shift the hue
 * of the saturated accents; this keeps it.
 */
const DEG = Math.PI / 180;
const JND = 0.02;
const EPSILON = 1e-4;

function oklabOf(L, C, h) {
  return [L, C * Math.cos(h * DEG), C * Math.sin(h * DEG)];
}

function linearSrgb([L, a, b]) {
  const l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3;
  const m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3;
  const s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3;
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
  ];
}

function oklab([r, g, b]) {
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return [
    0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
  ];
}

const clip = (rgb) => rgb.map((v) => Math.min(1, Math.max(0, v)));
const inGamut = (rgb) => rgb.every((v) => v >= -1e-6 && v <= 1 + 1e-6);
const deltaEOK = (p, q) => Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
const decode = (c) => {
  const x = c / 255;
  return x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4;
};
const encode = (x) => Math.round(255 * (x <= 0.0031308 ? 12.92 * x : 1.055 * x ** (1 / 2.4) - 0.055));

/** `oklch(L C h)` → `{ rgb: [r, g, b], mapped }`, where `mapped` says the value lay outside sRGB. */
export function oklchToSrgb(L, C, h) {
  let linear = linearSrgb(oklabOf(L, C, h));
  const mapped = !inGamut(linear);
  if (mapped) {
    let lo = 0;
    let hi = C;
    let current = clip(linear);
    while (hi - lo > EPSILON) {
      const chroma = (lo + hi) / 2;
      const candidate = linearSrgb(oklabOf(L, chroma, h));
      if (inGamut(candidate)) {
        lo = chroma;
        current = candidate;
        continue;
      }
      const clipped = clip(candidate);
      const distance = deltaEOK(oklab(clipped), oklabOf(L, chroma, h));
      if (distance < JND) {
        current = clipped;
        if (JND - distance < EPSILON) break;
        lo = chroma;
      } else {
        hi = chroma;
      }
    }
    linear = current;
  }
  return { rgb: clip(linear).map(encode), mapped };
}

/** Parses `oklch(L C h)` with unitless components; null for anything else. */
export function parseOklch(value) {
  const m = value.match(/^oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)$/);
  return m ? [Number(m[1]), Number(m[2]), Number(m[3])] : null;
}

/** Parses `#rrggbb` (either case) into 8-bit channels; null for anything else. */
export function parseHex(value) {
  const m = value.match(/^#([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  return m ? m.slice(1).map((pair) => parseInt(pair, 16)) : null;
}

/**
 * The sRGB value of a `#rrggbb` or `oklch(L C h)` token as `{ rgb, mapped, lab }`, where `lab` is
 * the token's own oklab coordinates (before any gamut mapping); null for anything else.
 */
export function srgbOf(value) {
  const hex = parseHex(value);
  if (hex) return { rgb: hex, mapped: false, lab: oklab(hex.map(decode)) };
  const lch = parseOklch(value);
  if (!lch) return null;
  return { ...oklchToSrgb(...lch), lab: oklabOf(...lch) };
}

/** deltaEOK, the Euclidean distance in oklab, between two `srgbOf` results. */
export function distance(p, q) {
  return deltaEOK(p.lab, q.lab);
}
