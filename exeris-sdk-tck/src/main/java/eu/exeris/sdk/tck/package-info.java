/**
 * Technology Compatibility Kit for the build-time metadata hand-off.
 *
 * <h2>What it is for</h2>
 *
 * <p>The SDK publishes a format and a discipline. The format is
 * {@code exeris-metadata/<entity>.json}; the discipline is ADR-042's "the reader reads what the
 * processor writes". Until now the discipline was enforced by whoever remembered it, and the record
 * of that is not good: three separate parity defects shipped, each with the same shape — one side
 * reading an attribute under a key the other did not use — and each caught by hand, long after.
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
 * <p><strong>No case that cannot fail.</strong> Every case is driven, in this module's own tests,
 * against a conforming binding and one broken in exactly the way the case describes — the second
 * must fail. The rule is not ceremony: it removed three of the four cases originally written for
 * {@code AbstractMapperPostureTck}, each of which asserted something true that no binding could get
 * wrong, and would have shipped as coverage that covered nothing.
 *
 * @since 0.11.0
 */
package eu.exeris.sdk.tck;
