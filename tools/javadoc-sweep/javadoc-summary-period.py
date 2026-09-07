#!/usr/bin/env python3
"""Terminate the Javadoc summary sentence — javadoc-conventions.md rule 2, JavadocStyle.

Checkstyle's `checkFirstSentence` accepts a sentence end only where `[.?!]` is followed by
whitespace, `<`, or the end of the description. `(Debezium, etc.)` therefore does NOT terminate a
sentence: the period is followed by `)`. Neither does a description that runs straight into its
first block tag.

For every doc comment this finds the description — the text before the first block tag, with
inline tags such as `{@snippet ... }` skipped by brace depth so an `@Injectable()` line inside a
snippet body is not mistaken for `@param` — and, when that description contains no valid
terminator, appends a period to its last line.

Conservative by construction: a description whose last line ends in `>` (an HTML tag such as
`</p>`) is left alone and reported, because the period belongs inside the markup and that is a
judgement call.
"""
import re, sys, pathlib

COMMENT = re.compile(r"/\*\*.*?\*/", re.S)
BLOCK_TAG = re.compile(r"^\s*\*\s*@\w+")
TERMINATOR = re.compile(r"[.?!]([ \t\n\r\f<]|$)")
CONTENT = re.compile(r"^(\s*\*\s?)(.*?)(\s*)$")
SAFE_TAIL = re.compile(r"[\w)\"'`\]}]$")

skipped = []

def strip_inline(text):
    """Blank out {@... } inline-tag bodies so their content cannot look like a block tag."""
    out, depth = [], 0
    i = 0
    while i < len(text):
        if text.startswith("{@", i):
            depth += 1; out.append("  "); i += 2; continue
        c = text[i]
        if depth:
            if c == "{": depth += 1
            elif c == "}": depth -= 1
            out.append("\n" if c == "\n" else " ")
        else:
            out.append(c)
        i += 1
    return "".join(out)

def fix_comment(block, path):
    lines = block.split("\n")
    masked = strip_inline(block).split("\n")
    # description = lines before the first block tag (judged on the masked copy)
    end = len(lines)
    for i, m in enumerate(masked):
        if BLOCK_TAG.match(m):
            end = i; break
    desc_idx = [i for i in range(end)
                if CONTENT.match(lines[i]) and CONTENT.match(lines[i]).group(2).strip()
                and not lines[i].lstrip().startswith("/**") or
                (i == 0 and lines[0].replace("/**", "", 1).strip())]
    # rebuild description text for the terminator test
    parts = []
    for i in range(end):
        t = lines[i]
        if i == 0:
            t = t.split("/**", 1)[1] if "/**" in t else t
        t = re.sub(r"^\s*\*\s?", "", t)
        t = t.replace("*/", "")
        parts.append(t)
    desc = strip_inline("\n".join(parts)).strip()
    if not desc or TERMINATOR.search(desc):
        return block, 0
    # find the last line carrying description text
    last = None
    for i in range(end - 1, -1, -1):
        t = lines[i]
        body = t.split("/**", 1)[1] if i == 0 and "/**" in t else re.sub(r"^\s*\*\s?", "", t)
        if body.replace("*/", "").strip():
            last = i; break
    if last is None:
        return block, 0
    line = lines[last]
    closing = line.rstrip().endswith("*/")
    core = line.rstrip()[:-2].rstrip() if closing else line.rstrip()
    if not SAFE_TAIL.search(core):
        skipped.append(f"{path}: {core.strip()[:70]}")
        return block, 0
    lines[last] = core + "." + (" */" if closing else "")
    return "\n".join(lines), 1

def process(p):
    s0 = s = p.read_text(encoding="utf-8")
    n = 0
    def repl(m):
        nonlocal n
        new, c = fix_comment(m.group(0), str(p))
        n += c
        return new
    s = COMMENT.sub(repl, s)
    if s != s0:
        p.write_text(s, encoding="utf-8")
    return n

if __name__ == "__main__":
    total = 0
    for root in (pathlib.Path(a) for a in sys.argv[1:]):
        for f in sorted(root.rglob("*.java")):
            total += process(f)
    print(f"summary periods added: {total}")
    if skipped:
        print(f"left for review ({len(skipped)}) — description ends in markup:")
        for s in skipped[:20]:
            print("  ", s)
