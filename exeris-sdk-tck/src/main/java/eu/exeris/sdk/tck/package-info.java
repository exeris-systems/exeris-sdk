/**
 * Technology Compatibility Kit for the build-time metadata hand-off.
 *
 * <h2>What it is for</h2>
 *
 * <p>The SDK publishes a format and a discipline. The format is
 * {@code exeris-metadata/<entity>.json}; the discipline is ADR-042's "the reader reads what the
 * processor writes". This kit turns the discipline into a gate. The defect it exists for has one
 * shape: one side reads an attribute under a key the other does not use.
 *
 * <p>They are hard to catch for one reason worth stating plainly: <strong>nothing fails.</strong>
 * Both sides emit well-formed metadata, no exception is raised, no diagnostic appears, and the
 * defect only becomes visible when someone compares the two outputs. That comparison is what
 * {@link eu.exeris.sdk.tck.AbstractMetadataParityTck} does, and it is the reason this module exists.
 *
 * <h2>The four suites</h2>
 *
 * <ul>
 *   <li>{@link eu.exeris.sdk.tck.AbstractMetadataProducerTck} — for whatever writes the baseline.
 *       Is the output readable, stamped, and carrying what the source declared?
 *   <li>{@link eu.exeris.sdk.tck.AbstractMetadataReaderTck} — for whatever reads
 *       {@code @ExerisDomain} source into the AST. Same questions from the other side.
 *   <li>{@link eu.exeris.sdk.tck.AbstractMetadataParityTck} — for a binding that has both. Do they
 *       agree?
 *   <li>{@link eu.exeris.sdk.tck.AbstractMapperPostureTck} — for anyone deserializing SDK JSON with
 *       their own mapper.
 * </ul>
 *
 * <h2>Two rules the kit holds itself to</h2>
 *
 * <p><strong>Implementation-agnostic.</strong> A binder supplies the implementation through abstract
 * methods; the kit never reaches for one. An enforcer rule keeps JavaParser and
 * {@code exeris-sdk-source-model-io} off this module's dependency tree, because a kit that could
 * reach the SDK's own reader would quietly be testing that instead of the binding.
 *
 * <p><strong>The corpus is compiled code.</strong> A producer binding drives javac over it, so
 * {@code CorpusCompilesTest} does too — under {@code -Werror -Xlint:deprecation}, which enforces
 * both that every mandatory attribute is supplied and that nothing deprecated for removal is used.
 *
 * <p><strong>No case that cannot fail.</strong> Every case is driven, in this module's own tests,
 * against a conforming binding and one broken in exactly the way the case describes — the second
 * must fail. A case that asserts something true no binding can get wrong is coverage that covers
 * nothing, and the rule keeps such a case out of the kit.
 *
 * @since 0.11
 */
package eu.exeris.sdk.tck;
