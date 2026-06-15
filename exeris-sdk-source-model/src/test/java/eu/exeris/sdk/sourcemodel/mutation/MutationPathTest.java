package eu.exeris.sdk.sourcemodel.mutation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MutationPath grammar")
class MutationPathTest {

    @Test
    @DisplayName("factories render the canonical path strings")
    void factoriesRenderCanonicalStrings() {
        assertThat(MutationPath.entity("Order")).hasToString("/entities/Order");
        assertThat(MutationPath.field("Order", "total")).hasToString("/entities/Order/fields/total");
        assertThat(MutationPath.relationship("Order", "customer"))
                .hasToString("/entities/Order/relationships/customer");
        assertThat(MutationPath.action("Order", "ship")).hasToString("/entities/Order/actions/ship");
    }

    @Test
    @DisplayName("parse(build) is the identity for every kind")
    void parseRoundTripsEveryKind() {
        for (MutationPath p : new MutationPath[]{
                MutationPath.entity("Order"),
                MutationPath.field("Order", "total"),
                MutationPath.relationship("Order", "customer"),
                MutationPath.action("Order", "ship")}) {
            assertThat(MutationPath.parse(p.toString())).isEqualTo(p);
        }
    }

    @Test
    @DisplayName("parse maps each collection segment to its TargetKind")
    void parseMapsCollectionSegments() {
        assertThat(MutationPath.parse("/entities/Order").kind()).isEqualTo(MutationPath.TargetKind.ENTITY);
        assertThat(MutationPath.parse("/entities/Order/fields/total").kind())
                .isEqualTo(MutationPath.TargetKind.FIELD);
        assertThat(MutationPath.parse("/entities/Order/relationships/customer").kind())
                .isEqualTo(MutationPath.TargetKind.RELATIONSHIP);
        assertThat(MutationPath.parse("/entities/Order/actions/ship").kind())
                .isEqualTo(MutationPath.TargetKind.ACTION);
    }

    @Test
    @DisplayName("parse rejects malformed paths")
    void parseRejectsMalformed() {
        assertThatThrownBy(() -> MutationPath.parse("entities/Order"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must start with '/'");
        assertThatThrownBy(() -> MutationPath.parse("/capabilities/Gateway"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("/entities/<Entity>");
        assertThatThrownBy(() -> MutationPath.parse("/entities/"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entity segment is blank");
        assertThatThrownBy(() -> MutationPath.parse("/entities/Order/widgets/x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown path collection segment");
        assertThatThrownBy(() -> MutationPath.parse("/entities/Order/fields/total/extra"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("member path must be");
        assertThatThrownBy(() -> MutationPath.parse("/entities/Order/fields/"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("member segment is blank");
    }

    @Test
    @DisplayName("constructor rejects inconsistent kind/member combinations")
    void constructorRejectsInconsistentMembers() {
        assertThatThrownBy(() -> new MutationPath("Order", MutationPath.TargetKind.ENTITY, "total"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("carries no member");
        assertThatThrownBy(() -> new MutationPath("Order", MutationPath.TargetKind.FIELD, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires a non-blank member");
        assertThatThrownBy(() -> new MutationPath("  ", MutationPath.TargetKind.ENTITY, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("constructor rejects path-separator characters in names (round-trip safety)")
    void constructorRejectsSeparatorInNames() {
        assertThatThrownBy(() -> MutationPath.entity("Order/Sub"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entity must not contain '/'");
        assertThatThrownBy(() -> MutationPath.field("Order", "total/extra"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("member must not contain '/'");
    }

    @Test
    @DisplayName("entity root is ancestor-or-self of its members; members only of themselves")
    void ancestorRules() {
        MutationPath root = MutationPath.entity("Order");
        MutationPath field = MutationPath.field("Order", "total");
        MutationPath otherField = MutationPath.field("Order", "amount");
        MutationPath otherEntity = MutationPath.field("Customer", "total");

        // root is ancestor-or-self of itself and its members
        assertThat(root.isSameOrAncestorOf(root)).isTrue();
        assertThat(root.isSameOrAncestorOf(field)).isTrue();

        // a member is its own ancestor-or-self, but not its sibling's, nor the root's
        assertThat(field.isSameOrAncestorOf(field)).isTrue();
        assertThat(field.isSameOrAncestorOf(otherField)).isFalse();
        assertThat(field.isSameOrAncestorOf(root)).isFalse();

        // different entities never relate
        assertThat(root.isSameOrAncestorOf(otherEntity)).isFalse();
        assertThat(field.isSameOrAncestorOf(otherEntity)).isFalse();
    }

    @Test
    @DisplayName("TargetKind.segment exposes the collection token")
    void targetKindSegment() {
        assertThat(MutationPath.TargetKind.ENTITY.segment()).isNull();
        assertThat(MutationPath.TargetKind.FIELD.segment()).isEqualTo("fields");
        assertThat(MutationPath.TargetKind.RELATIONSHIP.segment()).isEqualTo("relationships");
        assertThat(MutationPath.TargetKind.ACTION.segment()).isEqualTo("actions");
    }
}
