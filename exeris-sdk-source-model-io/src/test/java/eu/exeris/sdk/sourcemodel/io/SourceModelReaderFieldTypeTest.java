package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: a field's type is the processor's qualified form, and a relationship field is a field")
class SourceModelReaderFieldTypeTest {

    private static final String HANGAR = """
            package x.fleet;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.Field;
            import eu.exeris.sdk.annotation.Relationship;
            import java.math.BigDecimal;
            import java.util.List;
            import java.util.Map;

            @ExerisDomain
            public class Hangar<T> {
                private int bays;
                private String[] codes;
                @Field private BigDecimal fee;
                private Map<String, List<Integer>> slots;
                private Drone keeper;
                private Status status;
                private java.time.Instant openedAt;
                private T payload;
                private List<? extends Number> readings;
                @Relationship private List<Drone> drones;

                public enum Status { OPEN, CLOSED }
            }
            """;

    private final SourceModelReader reader = new SourceModelReader();

    private FieldMetadata field(String name) {
        DomainMetadata hangar = reader.read(HANGAR).orElseThrow();
        return hangar.findField(name).orElseThrow(() -> new AssertionError("no field " + name));
    }

    @Test
    @DisplayName("primitives and arrays keep the keyword, an array's element is qualified")
    void primitivesAndArrays() {
        assertThat(field("bays").type()).isEqualTo("int");
        assertThat(field("codes").type()).isEqualTo("java.lang.String[]");
    }

    @Test
    @DisplayName("an imported, a java.lang, a same-package and a member type are qualified")
    void simpleNamesResolve() {
        assertThat(field("fee").type()).isEqualTo("java.math.BigDecimal");
        assertThat(field("keeper").type()).isEqualTo("x.fleet.Drone");
        assertThat(field("status").type()).isEqualTo("x.fleet.Hangar.Status");
        assertThat(field("openedAt").type()).isEqualTo("java.time.Instant");
    }

    @Test
    @DisplayName("type arguments are qualified and joined without a space, wildcards and type variables kept")
    void typeArguments() {
        assertThat(field("slots").type())
                .isEqualTo("java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>");
        assertThat(field("payload").type()).isEqualTo("T");
        assertThat(field("readings").type()).isEqualTo("java.util.List<? extends java.lang.Number>");
    }

    @Test
    @DisplayName("a @Relationship field is a simple field as well as a relationship")
    void relationshipFieldIsAField() {
        DomainMetadata hangar = reader.read(HANGAR).orElseThrow();
        assertThat(field("drones")).isEqualTo(FieldMetadata.simple("drones", "java.util.List<x.fleet.Drone>"));
        assertThat(hangar.relationships()).anySatisfy(r -> assertThat(r.fieldName()).isEqualTo("drones"));
    }

    @Test
    @DisplayName("under an on-demand import an unresolved simple name is kept as written")
    void onDemandImportKeepsTheName() {
        String source = HANGAR.replace("import java.util.Map;", "import java.util.Map;\nimport x.dock.*;");
        DomainMetadata hangar = reader.read(source).orElseThrow();
        assertThat(hangar.findField("keeper").orElseThrow().type()).isEqualTo("Drone");
    }
}
