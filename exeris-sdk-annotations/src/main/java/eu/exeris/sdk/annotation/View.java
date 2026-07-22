package eu.exeris.sdk.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a <strong>backend-anchored presentation artifact</strong> — a page,
 * section, component, or fragment — by annotating a real backend Java type to
 * attach and compose its presentation. It is one of two front-doors onto the
 * shared {@code ViewMetadata} IR: this annotation layer covers the
 * backend+frontend case (a {@code @View} on a backend class) and composes
 * alongside the backend-only case (a backend type with no {@code @View}). The
 * class structure (nested records / inner classes carrying {@link Region} /
 * {@link Block} / {@link Bind}) expresses the composition tree, and the IR
 * references entities, projections, and actions only <em>by name</em>.
 *
 * <p>{@code @View} and its {@code ViewMetadata} IR are the <strong>single, unified
 * presentation model</strong> — they are <em>not</em> a parallel surface beside the
 * legacy entity-attached {@code @UI}. A single-entity view is expressible here just
 * as a multi-source one is, so {@code @UI} is being absorbed into this model rather
 * than kept alongside it (entity-level view selection becomes a {@code @View}; the
 * field-level render detail becomes the leaf field facet of {@code ViewMetadata}).
 * {@code @UI} stays the functional field-level path until the ADR-047 leaf-field
 * facet lands; see {@code @UI} and RFC-2026-06-25 for the convergence plan.
 *
 * <p>A purely frontend-only artifact (no backing Java type) is authored through a
 * separate presentation source — Studio or hand-authored presentation IR — not by
 * declaring a Java class to carry {@code @View} (that fake-carrier shape is
 * exactly what this layer avoids); both front-doors converge on the same
 * {@code ViewMetadata}.
 *
 * <p><strong>Open-Core status — structurally live:</strong> the
 * {@code exeris-tooling} processor extracts {@code @View} / {@code @Region} /
 * {@code @Block} / {@code @Bind} into the IR, and the codegen-ts Angular view
 * generator emits the component tree (RFC-2026-06-28, tooling). The remaining
 * piece is the ADR-047 leaf-field facet — {@code @UI}'s field-level render
 * detail, seeded today as {@code UIMetadata.UIFieldMetadata} on
 * {@code ComponentNodeMetadata.field} (ADR-047 plans its rename/expansion to
 * {@code ViewFieldMetadata}); until the processor populates it, field-level
 * presentation stays on {@code @UI}.
 *
 * <pre>{@code
 * @View(name = "ProductLanding", kind = View.Kind.PAGE, route = "/products")
 * public final class ProductLanding { }
 * }</pre>
 *
 * @author Exeris SDK Team
 * @version 0.8.0
 * @since 0.8.0
 * @see Region
 * @see Block
 * @see Bind
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface View {

    /**
     * The view's unique name (identity within the presentation IR).
     *
     * @return the view name
     */
    String name();

    /**
     * The kind of presentation artifact this is.
     *
     * @return the view kind, defaulting to {@link Kind#PAGE}
     */
    Kind kind() default Kind.PAGE;

    /**
     * The route this view is reachable at — an opaque string, meaningful only for
     * {@link Kind#PAGE}. Empty when the view is not route-bearing.
     *
     * @return the route, or empty
     */
    String route() default "";

    /**
     * Literal title for this view.
     *
     * @return the title, or empty
     */
    String title() default "";

    /**
     * Optional i18n message key for {@link #title()}. When non-empty it is
     * resolved against the message bundle, with {@link #title()} as the fallback.
     *
     * @return the title message key, or empty
     */
    String titleKey() default "";

    /**
     * An opaque layout key naming the shell / layout this view composes into.
     *
     * @return the layout key, or empty
     */
    String layout() default "";

    /**
     * The kind of presentation artifact. Mirrored by the AST-owned
     * {@code eu.exeris.sdk.sourcemodel.ast.ViewKind}.
     */
    enum Kind {
        /** A route-bearing page. */
        PAGE,
        /** A composable section of a page. */
        SECTION,
        /** A reusable component node. */
        COMPONENT,
        /** A non-route fragment (e.g. a navigation tree). */
        FRAGMENT
    }
}
