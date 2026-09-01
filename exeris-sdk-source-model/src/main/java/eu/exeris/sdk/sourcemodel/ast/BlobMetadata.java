package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AST facet for a {@code @Blob} field — a binary object (an attachment) held in
 * the kernel's blob store rather than inline in a column. Carried by
 * {@link FieldMetadata#blob()}.
 *
 * <p>{@code container} is <em>tenant-relative</em>: it names a container within
 * the caller's namespace, never a bucket URL or filesystem path. The kernel
 * resolves the physical location inside the store from the ambient
 * {@code StorageContext} and never from a value carried on the reference (kernel
 * ADR-056 obligation 4), which is what makes the isolation property structural
 * rather than validated. {@code null} means "derive it from the owning domain",
 * which is the recommended state. {@code contentTypes} declares what the
 * generated upload surface accepts; it is not a constraint rule, and this record
 * deliberately carries no size bound — constraint values have one carrier,
 * {@link FieldMetadata} (ADR-054), and the kernel states no size policy to carry.
 *
 * <p><strong>Reserved surface (ADR-072).</strong> No {@code exeris-tooling}
 * processor populates this record and no generator consumes it; the kernel holds
 * {@code …spi.storage.blob} at tier {@code preview}, so the shape is excluded from
 * the 1.0.0 freeze and a 1.x minor may still change it.
 *
 * @param container the container the object is addressed in, relative to the caller's
 *        namespace — never an absolute or physical location. Absent means the tooling derives
 *        it from the domain
 *
 * @param contentTypes the media types the generated upload surface accepts; empty means
 *        unrestricted
 *
 * @since 0.11.0
 * @see FieldMetadata#blob()
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlobMetadata(
        String container,
        List<String> contentTypes
) {

    /**
     * Compact constructor; applies this record's normalization rules.
     */
    public BlobMetadata {
        if (container != null && container.isBlank()) {
            container = null;
        }
        // Empty contentTypes serializes as [] (the AST's NON_NULL list convention).
        contentTypes = contentTypes == null ? List.of() : List.copyOf(contentTypes);
    }

    /**
     * A blob facet with a derived container and no declared media types.
     *
     * @return the {@code BlobMetadata}
     */
    public static BlobMetadata unrestricted() {
        return new BlobMetadata(null, List.of());
    }

    /**
     * A blob facet restricted to the given media types, with a derived container.
     *
     * @param contentTypes the {@code contentTypes} the result carries
     * @return the {@code BlobMetadata}
     */
    public static BlobMetadata ofContentTypes(List<String> contentTypes) {
        return new BlobMetadata(null, contentTypes);
    }

    /**
     * Whether an explicit container was declared (rather than left to be derived).
     *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasContainer() {
        return container != null;
    }

    /**
     * Whether the accepted media types were narrowed at all.
     *
     * @return the {@code boolean}
     */
    @JsonIgnore
    public boolean hasContentTypes() {
        return !contentTypes.isEmpty();
    }
}
