#!/usr/bin/env python3
"""Deterministic half of the ADR-085 §F.21 Javadoc pass.

Three transformations, each provably local and each verified by re-running Checkstyle:
  1. rule 5  — delete `@author` / `@version` lines from doc comments (Git is the author record)
  2. rule 4  — `@since X.Y.Z` -> `@since X.Y`
  3. rule 2  — a single-line `/** ... */` summary gets its terminating period

Nothing else is touched. Anything needing judgement is left for review.
"""
import re, sys, pathlib

AUTHOR_VERSION = re.compile(r"^[ \t]*\*[ \t]*@(?:author|version)\b.*\n", re.M)
SINCE_PATCH    = re.compile(r"(@since\s+\d+\.\d+)\.\d+\b")
# One-line doc comment whose text does not end in . ? ! : ; or a closing brace/tag.
ONELINE = re.compile(r"^([ \t]*/\*\*[ \t]*)(.+?)([ \t]*\*/[ \t]*)$", re.M)

def fix_oneline(m):
    head, body, tail = m.group(1), m.group(2).rstrip(), m.group(3)
    if not body or body.endswith(('.', '?', '!', '.)', '.}', '</p>')):
        return m.group(0)
    # a bare tag-only comment ("@param x the thing") is rule 3's business, not rule 2's
    if body.lstrip().startswith('@'):
        return m.group(0)
    return f"{head}{body}.{tail}"

def process(p: pathlib.Path):
    s0 = s = p.read_text(encoding="utf-8")
    counts = {}
    s, counts['author_version'] = AUTHOR_VERSION.subn("", s)
    s, counts['since'] = SINCE_PATCH.subn(r"\1", s)
    s, counts['oneline_period'] = ONELINE.subn(fix_oneline, s)
    if s != s0:
        p.write_text(s, encoding="utf-8")
    return counts

if __name__ == "__main__":
    roots = [pathlib.Path(a) for a in sys.argv[1:]]
    tot = {}
    for r in roots:
        for f in sorted(r.rglob("*.java")):
            for k, v in process(f).items():
                tot[k] = tot.get(k, 0) + v
    for k, v in sorted(tot.items()):
        print(f"{k:18s} {v}")
