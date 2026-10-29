package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphPropertyMetadata")
class GraphPropertyMetadataTest {

    @Test
    void theThreeArgumentShapeIsItsOwnField() {
        GraphPropertyMetadata property = new GraphPropertyMetadata("title", "STRING", true);
        assertThat(property.field()).isNull();
        assertThat(property.effectiveField()).isEqualTo("title");
    }

    @Test
    void aRenamedPropertyNamesItsField() {
        GraphPropertyMetadata renamed = new GraphPropertyMetadata("amt", "DOUBLE", false, "amount");
        assertThat(renamed.name()).isEqualTo("amt");
        assertThat(renamed.effectiveField()).isEqualTo("amount");
    }

    @Test
    void aFieldEqualToTheNameIsTheSameValueAsNoField() {
        // One canonical form for "the property keeps its field's name", so a producer that writes
        // the field anyway and one that omits it produce equal metadata and the same JSON.
        assertThat(new GraphPropertyMetadata("title", null, false, "title"))
                .isEqualTo(new GraphPropertyMetadata("title", null, false));
    }

    @Test
    void blankStringsNormaliseToNull() {
        GraphPropertyMetadata blank = new GraphPropertyMetadata("title", " ", false, "\t");
        assertThat(blank.type()).isNull();
        assertThat(blank.field()).isNull();
    }
}
