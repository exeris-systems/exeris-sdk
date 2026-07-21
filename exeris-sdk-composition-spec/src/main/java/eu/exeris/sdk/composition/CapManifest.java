package eu.exeris.sdk.composition;

import java.util.List;

/**
 * Canonical read-schema of {@code cap-manifest.json} — the ADR-024 capability composition
 * manifest the tooling emits at build time and the SKU-boot runtime asserts at startup.
 *
 * <p>This is the <b>single source of truth for the manifest shape</b> shared by both sides
 * (ADR-024 obligation 8b): the tooling that produces the manifest and the runtime that
 * consumes it agree on these records instead of each maintaining a private mirror. It models
 * the <em>consumer-relevant</em> subset — schema version, the validation {@link Stamp}, the
 * resolved {@link Module} set (for the {@link CompositionBinding}), {@link #initOrder()}
 * (the topological cap order the boot conductor replays), and the per-module-body
 * {@code lifecycleOwner} (the conductor's hook-discovery input). Producer-only diagnostics the
 * tooling also emits ({@code name}, {@code packageName}, per-module {@code requires},
 * {@code resolutions}, {@code warnings}) are deliberately absent — a consumer deserializes
 * with {@code FAIL_ON_UNKNOWN_PROPERTIES=false} and ignores them.
 *
 * <h2>Deserialization contract</h2>
 * <p>These are plain, annotation-free records so the module stays dependency-free (no Jackson
 * on the classpath). A consumer brings its own mapper and MUST configure it as the Exeris AST
 * wire-format does:
 * <ul>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} — tolerate the producer-only fields above;</li>
 *   <li>{@code FAIL_ON_NULL_FOR_PRIMITIVES = false} — a missing {@code schemaVersion} /
 *       {@code validated} must deserialize to {@code 0} / {@code false} (and be rejected by the
 *       asserter's handshake) rather than throwing mid-parse.</li>
 * </ul>
 *
 * <h2>Unversioned provides are legal</h2>
 * <p>{@link Provided#version()} may be {@code null}: an unversioned {@code @Provides} (the SDK
 * maps a blank version to {@code null}). It is <b>not</b> a malformed manifest — the canonical
 * binding normalizes a null version to the empty string (see {@link CompositionBinding}), and a
 * consumer's well-formedness check must treat it as legal-unversioned, never a hard reject.
 *
 * @since 0.8.0
 */
public record CapManifest(
        int schemaVersion,
        Stamp stamp,
        List<Module> modules,
        List<String> initOrder
) {

    /**
     * The build-time validation verdict (ADR-024 obligation 7).
     *
     * @param validated          always {@code true} in an emitted manifest — a failed graph
     *                           throws and no manifest is written, so presence is what is asserted
     * @param compositionVersion the composition release identity, or {@code "0.0.0"} until the
     *                           build supplies one (the {@code -Dexeris.composition.version} seam)
     * @param contentBinding     {@code "sha256:" + 64 lowercase hex} over the resolved cap set
     */
    public record Stamp(boolean validated, String compositionVersion, String contentBinding) {}

    /**
     * One {@code @CapabilityModule} in the composition.
     *
     * <p><b>Component name is wire-pinned.</b> These records are annotation-free, so each component
     * name <em>is</em> the JSON key. The producer (the tooling's {@code CapabilityModuleDescriptor})
     * emits the body under the key {@code "module"}; renaming this component (e.g. to {@code body})
     * would silently deserialize the body to {@code null} for every real manifest. Leave it as
     * {@code module} unless the producer's wire shape changes in lockstep.
     *
     * @param qualifiedName the module class's fully-qualified name (the binding's sort key)
     * @param module        the module body; may be {@code null} for a module that provides nothing
     */
    public record Module(String qualifiedName, ModuleBody module) {}

    /**
     * The provided-service body of a module.
     *
     * <p><b>{@code lifecycleOwner} is not part of the content binding.</b> Like {@code initOrder},
     * it is a tooling-derived field the binding does not hash — post-assertion the manifest is
     * trusted as a correctness artifact, so the boot conductor reads it verbatim. The producer
     * ({@code ExerisDomainProcessor}) emits it {@code NON_NULL} inside the module body; absent /
     * {@code null} / blank means the cap declares no lifecycle hooks, matching
     * {@code @CapabilityLifecycle}'s zero-or-one cardinality (a blank value normalizes to
     * {@code null} in the compact constructor). Trailing component since 0.9.0.
     *
     * @param provides       the services this module {@code @Provides}; may be {@code null} or empty
     * @param lifecycleOwner the fully-qualified name of the cap's {@code @CapabilityLifecycle}
     *                       class (the {@code CapabilityLifecycleHooks} implementation the boot
     *                       conductor instantiates), or {@code null} for a cap with no hooks
     */
    public record ModuleBody(List<Provided> provides, String lifecycleOwner) {
        public ModuleBody {
            if (lifecycleOwner != null && lifecycleOwner.isBlank()) lifecycleOwner = null;
        }
    }

    /**
     * One provided service.
     *
     * @param service the service interface's fully-qualified name
     * @param version the semantic version, or {@code null} for an unversioned provide
     */
    public record Provided(String service, String version) {}
}
