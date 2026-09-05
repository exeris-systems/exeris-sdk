---
description: Audit field vs validation attribute boundaries across annotations, AST records, and package-info files.
argument-hint: Modified annotation, AST, or package-info files
---

Audit changes to `@Field`, `@Validation`, or `FieldMetadata` against canonical scoping rules.

Priorities:
1. Ownership boundary: `@Field` owns lifecycle only (`required`, `inCreate`, `inUpdate`).
2. Constraint boundary: `@Validation` owns constraints only (`min`, `max`, `pattern`, etc.).
3. AST carrier: `FieldMetadata` carries constraints; no duplicate containers.
4. Documentation sync: confirm both `package-info.java` files stay aligned.

Changed scope:
$ARGUMENTS

Please produce:
- Scoping analysis
- Package-info synchronization check
- Migration implications
- Final verdict: APPROVE / CONDITIONAL / REJECT
