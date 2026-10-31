package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("reader: an action parameter's type is the processor's qualified form")
class SourceModelReaderActionParamTypeTest {

    private static final String DOCK = """
            package x.fleet;
            import eu.exeris.sdk.annotation.Action;
            import eu.exeris.sdk.annotation.ActionParam;
            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.QueryParam;
            import java.math.BigDecimal;
            import java.util.List;

            @ExerisDomain
            public class Dock {
                @Action(name = "berth", label = "Berth")
                public <T> void berth(@ActionParam String name,
                                      @ActionParam BigDecimal fee,
                                      @QueryParam("slots") List<Integer> slots,
                                      @ActionParam Drone keeper,
                                      @ActionParam T payload,
                                      @ActionParam int[] bays,
                                      @ActionParam String... tags) {
                }
            }
            """;

    @Test
    @DisplayName("java.lang, imported, same-package, generic, type-variable, array and varargs parameters")
    void parameterTypes() {
        ActionMetadata berth = new SourceModelReader().read(DOCK).orElseThrow().actions().getFirst();
        List<String> types = berth.params().stream().map(ActionParamMetadata::type).toList();
        assertThat(types).containsExactly(
                "java.lang.String", "java.math.BigDecimal", "java.util.List<java.lang.Integer>",
                "x.fleet.Drone", "T", "int[]", "java.lang.String...");
        assertThat(berth.displayName()).isEqualTo("Berth");
    }
}
