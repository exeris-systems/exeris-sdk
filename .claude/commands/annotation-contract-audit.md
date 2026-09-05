---
# DO NOT EDIT — generated from .agents/workflows/annotation-contract-audit.md (agents-md-schema.md rule 7). Edit the source.
description: Audit annotation contracts for RetentionPolicy.SOURCE, presence of @Target, and zero compile-time dependencies.
argument-hint: Modified annotation files or diff
---
<!-- DO NOT EDIT. Generated from .agents/workflows/annotation-contract-audit.md by the AGENTS.md adapter step
     (agents-md-schema.md rule 7). Edit the source, not this file. -->
Audit annotations touched in this change against Exeris SDK invariants.

Priorities:
1. Zero runtime footprint: verify `@Retention(SOURCE)` across all `@interface` declarations.
2. Target discipline: ensure explicit and appropriate `@Target(...)` on every annotation.
3. Test reach: confirm `AnnotationContractTest` covers any new root or subpackage.
4. Dependency check: verify `exeris-sdk-annotations` introduces zero compile-time dependencies.

Changed scope:
$ARGUMENTS

Please produce:
- Inspected annotations list
- `@Retention(SOURCE)` status
- `@Target` presence and scope
- New dependency check
- Final verdict: APPROVE / CONDITIONAL / REJECT
