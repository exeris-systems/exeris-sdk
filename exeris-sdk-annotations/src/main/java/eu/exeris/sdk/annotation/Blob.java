package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as holding a <em>binary object</em> — an attachment — rather than
 * an inline column value.
 *
 * <p>This is the Entity-First expression of the kernel's blob-storage seam
 * (kernel ADR-056, {@code eu.exeris.kernel.spi.storage.blob}). Before it, a domain
 * could not say "this entity has an attachment" at all: {@link Field#dataType()} is
 * a free-form presentation hint and the ui-kit's {@code .exeris-file} class is
 * styling. {@code @Blob} is the declaration; {@code @Field.dataType} keeps the role
 * it always had and is not superseded.
 *
 * <h2>Basic usage</h2>
 * <pre>{@code
 * @Field(displayName = "Statement PDF")
 * @Blob(contentTypes = {"application/pdf"})
 * private BlobRef statement;
 * }</pre>
 *
 * <h2>The reference is tenant-relative — always</h2>
 * <p>{@link #container()} names a container <em>within the caller's namespace</em>.
 * It is never a bucket URL, a filesystem path, or any absolute location: the kernel
 * resolves a physical location inside the store from the ambient
 * {@code StorageContext}, never from a value carried on the reference, and that is
 * what makes a forged or replayed reference resolve inside the caller's own
 * namespace instead of escaping it (kernel ADR-056 obligation 4). Writing a location
 * here would not widen that — it would simply be ignored.
 *
 * <h2>No size or format constraint lives here</h2>
 * <p>A {@code maxSizeBytes} is deliberately absent. Constraint rules have one
 * declaration site — {@link Validation} — and one AST carrier
 * ({@code FieldMetadata}); a second one on this annotation is the shape ADR-054
 * closed and must not come back. Independently, the kernel promises nothing about
 * size (ADR-056 makes transfer buffers caller-owned and states no size policy), so
 * the attribute would be inert against a platform that has no opinion to be inert
 * against. {@link #contentTypes()} is not a constraint in that sense: it declares
 * what the generated upload surface accepts, the same kind of design-time statement
 * as {@code @Field.dataType}.
 *
 * <h2>Open-Core status — reserved, extraction pending tooling</h2>
 * <p>Declared shape, not yet a live attachment. The kernel side demonstrably
 * exists — {@code BlobStore} / {@code BlobRef} shipped on the kernel 0.11 line with
 * {@code AbstractBlobStorageTck} — but no {@code exeris-tooling} processor extracts
 * {@code @Blob}, no generator emits the upload/download surfaces from it, and the
 * {@code exeris-sdk-source-model-io} reader does not read it, so declaring it today
 * has no generated effect. The kernel holds {@code …spi.storage.blob} at tier
 * {@code preview}, so this surface is <strong>excluded from the 1.0.0 freeze</strong>
 * and a 1.x minor may still change it; it is promoted when the kernel package leaves
 * {@code preview} <em>and</em> the tooling transcription exists. See
 * {@code docs/adr/ADR-072} and {@code ROADMAP.md}.
 *
 * <p><strong>One combination the platform will refuse:</strong> a {@code @Blob} field
 * on an entity declared {@code @ExerisDomain(dataScope = GLOBAL)}. Global scope leaves
 * {@code StorageContext.isolationKey} empty, and a store must terminally deny a blob
 * operation in that state rather than fall back to an unscoped location (kernel ADR-056
 * obligation 5) — so the pair is unstorable by construction, not merely unsupported.
 * Rejecting it is a build-time job for {@code exeris-tooling}; it is recorded here so
 * the combination is not written in the first place.
 *
 * @since 0.11.0
 * @see Field
 * @see ExerisDomain#dataScope()
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface Blob {

    /**
     * The container this object is addressed in, <em>relative to the caller's
     * namespace</em> — never an absolute or physical location (see the class
     * javadoc).
     *
     * <p>Empty (the default) means the container is derived by tooling from the
     * owning domain, which is the recommended state: a hand-written container name
     * is one more string to keep in step with a rename.
     *
     * @return the tenant-relative container name, or empty to derive it
     */
    String container() default "";

    /**
     * The media types the generated upload surface accepts, e.g.
     * {@code {"application/pdf", "image/png"}}.
     *
     * <p>Empty (the default) declares no restriction. This is a design-time
     * declaration about the generated surface, not a validation rule — see the
     * class javadoc on why no size or format constraint is declared here.
     *
     * @return the accepted media types, or empty for unrestricted
     */
    String[] contentTypes() default {};
}
