package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The data binding of a presentation node — what data, if any, a
 * {@link ComponentNodeMetadata} draws from. An opaque descriptor: a
 * {@link BindSource} discriminator plus opaque {@code ref} / {@code path} /
 * {@code expression} / {@code language} strings the AST never resolves (zero
 * runtime coupling). A {@link BindSource#STATIC} / {@link BindSource#NONE} binding
 * marks authored / unbound content.
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} (a deliberate deviation from the
 * prevailing {@code NON_DEFAULT}, per RFC-2026-06-25) so the future numeric
 * binding fields avoid the boxed-zero trap; blank strings normalize to
 * {@code null} in the compact constructor and a null {@code source} is tolerated
 * on the wire, with {@link #effectiveSource()} applying the {@link BindSource#NONE}
 * default. Part of the reserved presentation IR surface — no Open-Core emitter
 * honours it yet.
 *
 * @since 0.8.0
 * @see ComponentNodeMetadata
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BindingMetadata(
        BindSource source,
        String ref,
        String path,
        String expression,
        String language
) {

    public BindingMetadata {
        if (ref != null && ref.isBlank()) {
            ref = null;
        }
        if (path != null && path.isBlank()) {
            path = null;
        }
        if (expression != null && expression.isBlank()) {
            expression = null;
        }
        if (language != null && language.isBlank()) {
            language = null;
        }
    }

    /** A binding to nothing (authored / unbound content). */
    public static BindingMetadata none() {
        return new BindingMetadata(BindSource.NONE, null, null, null, null);
    }

    /** A binding to an entity field path. */
    public static BindingMetadata entity(String ref, String path) {
        return new BindingMetadata(BindSource.ENTITY, ref, path, null, null);
    }

    /** A binding to a projection (read-model) field path. */
    public static BindingMetadata projection(String ref, String path) {
        return new BindingMetadata(BindSource.PROJECTION, ref, path, null, null);
    }

    /** A binding to an action, by name. */
    public static BindingMetadata action(String ref) {
        return new BindingMetadata(BindSource.ACTION, ref, null, null, null);
    }

    /** A static / authored-literal binding. */
    public static BindingMetadata staticBinding() {
        return new BindingMetadata(BindSource.STATIC, null, null, null, null);
    }

    /** The effective source: the declared one, or {@link BindSource#NONE}. */
    @JsonIgnore
    public BindSource effectiveSource() {
        return source != null ? source : BindSource.NONE;
    }
}
