package eu.exeris.sdk.catalog;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a parsed javadoc fragment to the plain text a catalog consumer can read.
 *
 * <p>Reading the <em>parsed</em> tree rather than the raw comment is the point. A raw
 * comment carries leading asterisks, inline tags and HTML that every consumer would
 * otherwise have to strip for itself — and a consumer on the far side of a language
 * boundary would be reimplementing javadoc to do it. The compiler has already parsed
 * this; the only work left is choosing what each node becomes.
 *
 * <p>The tag set handled here is the one the SDK's annotation sources actually use
 * ({@code {@code}}, {@code {@link}}, {@code {@linkplain}}; {@code <p>}, {@code <li>},
 * {@code <pre>} and inline emphasis). Anything else falls through to its own source form
 * rather than being dropped, so an unfamiliar tag shows up as visible text in the catalog
 * instead of silently deleting the sentence around it.
 */
final class JavadocText {

    /** The few entities the sources use; anything else is left in its source form. */
    private static final Map<String, String> ENTITIES = Map.of(
            "amp", "&",
            "lt", "<",
            "gt", ">",
            "quot", "\"",
            "apos", "'",
            "nbsp", " ",
            "hellip", "…",
            "mdash", "—",
            "ndash", "–",
            "rarr", "→");

    private JavadocText() {
    }

    /** The first sentence, collapsed onto one line — the "purpose" a listing shows. */
    static String oneLine(List<? extends DocTree> trees) {
        return render(trees).replaceAll("\\s+", " ").strip();
    }

    /**
     * The full body with its paragraph structure kept — the rationale a describe call
     * shows. Runs of blank lines collapse to one, so a {@code <p>} sitting next to a real
     * blank line does not open a gap.
     */
    static String prose(List<? extends DocTree> trees) {
        return render(trees)
                .replaceAll("[ \t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private static String render(List<? extends DocTree> trees) {
        Renderer renderer = new Renderer();
        renderer.appendAll(trees);
        return renderer.out.toString();
    }

    /**
     * Holds the one piece of state the walk needs: whether it is inside {@code <pre>}.
     *
     * <p>Source comments are hard-wrapped at the column the file is formatted to, and
     * those line breaks are an artefact of the source rather than content — a consumer
     * rendering the text would re-wrap it anyway, and JSON gives it no reason to inherit
     * our margin. So a newline inside running text becomes a space. Inside {@code <pre>}
     * it is the opposite: the line breaks are the whole point, and the SDK's annotations
     * use {@code <pre>} for usage examples 176 times over.
     */
    private static final class Renderer {

        private final StringBuilder out = new StringBuilder();
        private int preformattedDepth;

        void appendAll(List<? extends DocTree> trees) {
            for (DocTree tree : trees) {
                append(tree);
            }
        }

        private void append(DocTree tree) {
            switch (tree.getKind()) {
                case TEXT -> appendText(((TextTree) tree).getBody());
                // {@code x} / {@literal x} — the braces are markup, the body is the text
                case CODE, LITERAL -> out.append(((LiteralTree) tree).getBody().getBody());
                // {@link a.b.C#d} — the label when the author gave one, else the signature
                case LINK, LINK_PLAIN -> appendLink((LinkTree) tree);
                case START_ELEMENT -> appendElement(((StartElementTree) tree).getName().toString(), true);
                case END_ELEMENT -> appendElement(((EndElementTree) tree).getName().toString(), false);
                case ENTITY -> appendEntity(((EntityTree) tree).getName().toString());
                default -> out.append(tree);
            }
        }

        private void appendText(String body) {
            out.append(preformattedDepth > 0 ? body : body.replaceAll("\\s*\n\\s*", " "));
        }

        private void appendLink(LinkTree link) {
            Renderer label = new Renderer();
            label.appendAll(link.getLabel());
            String rendered = label.out.toString().strip();
            out.append(rendered.isEmpty() ? link.getReference().getSignature() : rendered);
        }

        /**
         * Block-level HTML becomes whitespace; inline emphasis becomes nothing. The catalog
         * carries text, not markup — a consumer that wants to style it has the raw javadoc
         * in the sources jar.
         */
        private void appendElement(String name, boolean start) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "pre" -> {
                    preformattedDepth = Math.max(0, preformattedDepth + (start ? 1 : -1));
                    out.append("\n\n");
                }
                // a list opens and closes a block; an item only opens one, so a closing
                // </li> must contribute nothing or every bullet gains a blank line
                case "ul", "ol", "dl", "blockquote" -> out.append("\n\n");
                case "p", "br" -> appendIfStart(start, "\n\n");
                case "li" -> appendIfStart(start, "\n- ");
                case "dt" -> appendIfStart(start, "\n");
                default -> {
                    // inline (strong, em, code, dd, …) — contributes no structure
                }
            }
        }

        private void appendIfStart(boolean start, String text) {
            if (start) {
                out.append(text);
            }
        }

        private void appendEntity(String name) {
            String resolved = ENTITIES.get(name);
            out.append(resolved != null ? resolved : "&" + name + ";");
        }
    }
}
