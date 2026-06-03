/**
 * JavaParser-based reader/writer between Java source and the canonical
 * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} model.
 *
 * <p><b>Why a separate module (ADR-037).</b> JavaParser is the SDK's only heavy
 * third-party dependency. Keeping it here — not in {@code exeris-sdk-source-model}
 * — means every consumer that only needs the AST records (the annotation
 * processor's JSON layer, the codegen emitters) never inherits it. Only callers
 * that parse or rewrite {@code .java} source (the Studio/IDE LSP) depend on this
 * module. This preserves the SDK's zero-runtime-coupling invariant.
 *
 * <p><b>Two directions.</b>
 * <ul>
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelReader} — {@code .java}
 *       → {@code DomainMetadata} (no {@code javac}; usable in an editor).</li>
 *   <li>{@link eu.exeris.sdk.sourcemodel.io.SourceModelWriter} — idempotent
 *       edits to {@code .java}, preserving user comments, formatting, and
 *       non-Exeris annotations via JavaParser's {@code LexicalPreservingPrinter}.</li>
 * </ul>
 *
 * <p><b>Status: 0.3.0 spike.</b> The reader extracts entity name, package, and
 * fields; the writer demonstrates lexical-preserving field insertion. The full
 * surface (all AST node kinds, conflict resolution) is the rest of 0.3.0.
 *
 * @since 0.3.0
 */
package eu.exeris.sdk.sourcemodel.io;
