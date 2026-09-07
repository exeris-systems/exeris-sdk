#!/usr/bin/env python3
"""Order Javadoc block tags — javadoc-conventions.md rule 3, Checkstyle AtclauseOrder.

The required order is `@param @return @throws @since @see @deprecated`. What the corpus actually
has, in the files that fail, is `@since` (and sometimes `@author`) written above `@param` — the
shape a stamping pass leaves behind when it inserts at the top of the tag block rather than in
order.

Reordering is decidable: a block tag owns its own line plus every following line until the next
block tag or the end of the comment, so the tags can be lifted out as units, sorted stably by the
required order, and put back. Tags outside the known order keep their relative position at the end,
which is what a stable sort with a large sort key does.

Also normalises the two lines a reordering pass cannot reach on its own, both to the comment's own
indentation: a continuation line that is only whitespace and an asterisk, and the closing `*/`.
The terminator needs its own pass because `reorder` deliberately holds it out of the region it
moves — a comment whose tags are already in order is never rewritten, so a ragged `*/` above a
correct tag block would otherwise survive every run.

Unknown-but-ordered tags are never moved past a known one they already follow. Comments with no
block tags are untouched.

Tag detection runs on a copy with every `{@... }` inline-tag body blanked out, the same mask
`javadoc-summary-period.py` uses. Without it a `{@snippet lang="java" :}` demonstrating an
annotation is read as a block-tag region — `@Field(...)` and `@Blob(...)` open a line exactly the
way `@param` does — and the real tags below get sorted *into* the snippet. That is not a style
regression; it moves prose inside a rendered code sample and changes what the sample says.
"""
import re, sys, pathlib

ORDER = {"@param": 0, "@return": 1, "@throws": 2, "@exception": 2,
         "@since": 3, "@see": 4, "@deprecated": 5}
COMMENT = re.compile(r"(^[ \t]*)/\*\*.*?\*/", re.S | re.M)
TAG = re.compile(r"^[ \t]*\*[ \t]*(@\w+)")
BLANK_STAR = re.compile(r"^[ \t]*\*[ \t]*$")

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

def reorder(block, indent):
    lines = block.split("\n")
    masked = strip_inline(block).split("\n")
    # locate the first block-tag line; everything before it is the description and stays put
    first = None
    for i, m in enumerate(masked):
        if TAG.match(m):
            first = i; break
    if first is None:
        return block, 0
    # the closing `*/` line stays put too
    last = len(lines) - 1
    tail = []
    while last >= first and "*/" in lines[last]:
        tail.insert(0, lines[last]); last -= 1
    region = lines[first:last + 1]
    if not region:
        return block, 0
    # group each tag with its continuation lines
    groups, cur = [], None
    for l, mk in zip(region, masked[first:last + 1]):
        m = TAG.match(mk)
        if m:
            cur = {"tag": m.group(1), "lines": [l]}
            groups.append(cur)
        elif cur is not None:
            cur["lines"].append(l)
        else:
            return block, 0  # text before the first tag inside the region: leave well alone
    if len(groups) < 2:
        changed_blank = False
        return block, 0
    before = [g["tag"] for g in groups]
    keyed = sorted(range(len(groups)),
                   key=lambda i: (ORDER.get(groups[i]["tag"], 99), i))
    after = [groups[i]["tag"] for i in keyed]
    if before == after:
        return block, 0
    out = lines[:first]
    for i in keyed:
        g = list(groups[i]["lines"])
        # a blank ` *` line separates tag blocks; it belongs to the block, not to the tag that
        # happens to precede it, so carrying it along would strand it wherever that tag lands.
        while g and BLANK_STAR.match(g[-1]):
            g.pop()
        # re-prefix, because the corpus has tag lines indented ragged (up to ten spaces before the
        # asterisk). Only lines this pass is already moving are touched, so the diff stays
        # proportional to the reordering rather than reformatting the file.
        for l in g:
            body = re.sub(r"^[ \t]*\*[ \t]?", "", l)
            out.append(indent + " * " + body if body else indent + " *")
    out.extend(tail)
    return "\n".join(out), 1

def normalise_blank_stars(block, indent):
    n = 0
    lines = block.split("\n")
    masked = strip_inline(block).split("\n")
    for i, l in enumerate(lines):
        if i and BLANK_STAR.match(masked[i]) and BLANK_STAR.match(l) and l != indent + " *":
            lines[i] = indent + " *"; n += 1
    # The closing delimiter, when it owns its line. A one-line `/** … */` has no such line, and
    # `i and` above already protects the opener; here the strip test does the same job.
    if len(lines) > 1 and lines[-1].strip() == "*/" and lines[-1] != indent + " */":
        lines[-1] = indent + " */"; n += 1
    return "\n".join(lines), n

def text_block_spans(src):
    """Character ranges covered by Java text blocks, so fixture Javadoc inside one is left alone.

    `AnnotationCatalogProcessorTest` feeds javac a source string containing an annotation whose
    element documents `@deprecated` above `@return` on purpose — that ordering IS the assertion.
    A pass that reorders it rewrites the test's input and the test still passes, which is the
    worst way for a tool to be wrong.
    """
    spans, i = [], 0
    while True:
        a = src.find('"""', i)
        if a < 0:
            return spans
        b = src.find('"""', a + 3)
        if b < 0:
            return spans
        spans.append((a, b + 3)); i = b + 3

def process(p):
    s0 = s = p.read_text(encoding="utf-8")
    stats = [0, 0]
    blocks = text_block_spans(s0)
    def repl(m):
        if any(a <= m.start() < b for a, b in blocks):
            return m.group(0)
        indent = m.group(1)
        b, k = normalise_blank_stars(m.group(0), indent); stats[1] += k
        b, j = reorder(b, indent); stats[0] += j
        return b
    s = COMMENT.sub(repl, s)
    if s != s0:
        p.write_text(s, encoding="utf-8")
    return stats

if __name__ == "__main__":
    t = [0, 0]
    for root in (pathlib.Path(a) for a in sys.argv[1:]):
        for f in sorted(root.rglob("*.java")):
            r = process(f); t[0] += r[0]; t[1] += r[1]
    print(f"comments reordered: {t[0]}; ragged continuation lines fixed: {t[1]}")
