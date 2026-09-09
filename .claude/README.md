# `.claude/` — generated adapters and provider configuration

This directory is **not** where project rules are authored. Per
[`agents-md-schema.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md)
rules 2, 7 and 10-11, the canonical semantic source is [`.agents/`](../.agents) and this directory
adapts it for Claude Code.

- `agents/` — **generated** from `.agents/agents/<name>/AGENT.md`. The canonical profile declares
  `capabilities`; the renderer maps them onto this runtime's tool names.
- `skills/<name>/` — a **symlink** into `.agents/skills/<name>`, one per skill. Five of the six
  runtimes read `.agents/skills` natively; Claude Code does not, and a copy would be a second
  place a skill lives.
- `skills/<workflow>/SKILL.md` — **generated** from `.agents/workflows/<name>.md`, rendered
  `disable-model-invocation: true` so it stays user-invoked. It replaces the legacy `commands/`.
- `settings.local.json` — provider-owned local configuration. Never semantic content.

A change made in this directory is lost the next time the renderer runs.

## Rendering and checking them

The renderer is **not** in this repository. ADR-085 §C.11 (amended 2026-09-08) puts one
implementation in `exeris-systems/exeris-agents`, pinned in [`../.agents/manifest.yaml`](../.agents/manifest.yaml),
because two implementations of one schema is how the schema stops being one. CI checks the bundle
out and runs it; locally, from a checkout of that repository:

```bash
python3 tools/agents_bundle.py verify  --root <this repo>   # vendored digest matches the pin
python3 tools/agents_file_check.py     --root <this repo>   # schema rules 1-13
python3 tools/agents_render.py         --root <this repo>   # rewrite every adapter
python3 tools/agents_render.py --check --root <this repo>   # assert each matches its source
```
