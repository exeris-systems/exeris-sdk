/**
 * Domain metadata models for Exeris annotation processing.
 * <p>
 * Contains records representing metadata extracted from SDK annotations.
 * These models are serialized to JSON and consumed by code generators.
 *
 * <h2>JSON wire-format contract for consumers</h2>
 * <p>The processor writes these records to {@code *.json} files at build
 * time and downstream tooling (codegen, LSP, IDE plugins) reads them back.
 * For round-trip fidelity, any consumer constructing its own Jackson
 * {@code ObjectMapper} <strong>must</strong> configure:
 * <ul>
 *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false} — Jackson 3 defaults this
 *       to {@code true}, but our records use primitive booleans heavily with
 *       {@code @JsonInclude(NON_DEFAULT)} / {@code NON_NULL}, so absent fields
 *       arrive as {@code null}. Without this flag, deserialization throws on
 *       any record that has a default-valued boolean.</li>
 * </ul>
 * The wire-format guard test {@code AstJsonRoundTripTest} configures the
 * mapper accordingly and is the canonical reference.
 *
 * @since 0.1.0
 */
package eu.exeris.sdk.sourcemodel.ast;
