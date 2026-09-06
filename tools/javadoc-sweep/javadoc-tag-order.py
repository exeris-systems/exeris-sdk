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

Also normalises a continuation line that is only whitespace and an asterisk to the comment's own
indentation. Four of those exist on main, each directly below an inserted `@since`, and they are
what makes the tag block look ragged.

Unknown-but-ordered tags are never moved past a known one they already follow. Comments with no
block tags are untouched.
"""
import re, sys, pathlib

ORDER = {"@param": 0, "@return": 1, "@throws": 2, "@exception": 2,
         "@since": 3, "@see": 4, "@deprecated": 5}
COMMENT = re.compile(r"(^[ \t]*)/\*\*.*?\*/", re.S | re.M)
TAG = re.compile(r"^[ \t]*\*[ \t]*(@\w+)")
BLANK_STAR = re.compile(r"^[ \t]*\*[ \t]*$")

def reorder(block, indent):
    lines = block.split("\n")
    # locate the first block-tag line; everything before it is the description and stays put
    first = None
    for i, l in enumerate(lines):
        if TAG.match(l):
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
    for l in region:
        m = TAG.match(l)
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
    for i, l in enumerate(lines):
        if i and BLANK_STAR.match(l) and l != indent + " *":
            lines[i] = indent + " *"; n += 1
    return "\n".join(lines), n

def process(p):
    s0 = s = p.read_text(encoding="utf-8")
    stats = [0, 0]
    def repl(m):
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
