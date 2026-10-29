package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata.CascadeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: @Relationship target, cascade and display field as the processor reads them")
class SourceModelReaderRelationshipTest {

    private static final String INVOICE = """
            package x;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.Relationship;

            @ExerisDomain
            public class Invoice {
                @Relationship(targetEntity = Customer.class, displayField = "name",
                        cascadeDelete = true, cascadeUpdate = true)
                private Customer both;

                @Relationship(targetEntity = Customer.class, displayField = "name", cascadeUpdate = true)
                private Customer updateOnly;

                @Relationship(targetEntity = Customer.class, displayField = "name",
                        cascadeDelete = false, cascadeUpdate = false)
                private Customer neither;

                @Relationship(targetEntity = com.acme.crm.Owner.class, displayField = "fullName")
                private java.util.UUID ownerId;

                @Relationship(targetEntity = void.class, displayField = "label")
                private java.util.List<Line> lines;

                @Relationship(displayField = "label")
                private Line primaryLine;
            }
            """;

    private final DomainMetadata invoice = new SourceModelReader().read(INVOICE).orElseThrow();

    private RelationshipMetadata relationship(String field) {
        return invoice.relationships().stream()
                .filter(r -> field.equals(r.fieldName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no relationship " + field));
    }

    @Test
    void bothCascadeFlagsReadAsAll() {
        assertThat(relationship("both").cascade()).isEqualTo(CascadeType.ALL);
    }

    @Test
    void updateAloneReadsAsMerge() {
        assertThat(relationship("updateOnly").cascade()).isEqualTo(CascadeType.MERGE);
    }

    @Test
    void flagsDeclaredFalseLeaveTheDefault() {
        assertThat(relationship("neither").cascade()).isEqualTo(CascadeType.NONE);
    }

    @Test
    void aQualifiedTargetEntityReadsAsItsSimpleName() {
        assertThat(relationship("ownerId").targetEntity()).isEqualTo("Owner");
        assertThat(relationship("ownerId").displayField()).isEqualTo("fullName");
    }

    @Test
    void voidTargetEntityFallsBackToTheCollectionElementType() {
        assertThat(relationship("lines").targetEntity()).isEqualTo("Line");
    }

    @Test
    void anAbsentTargetEntityFallsBackToTheFieldType() {
        assertThat(relationship("primaryLine").targetEntity()).isEqualTo("Line");
    }
}
