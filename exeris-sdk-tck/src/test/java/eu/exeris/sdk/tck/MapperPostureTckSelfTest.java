package eu.exeris.sdk.tck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link AbstractMapperPostureTck}'s case is not vacuous.
 *
 * <p>This self-test earned its keep before the suite shipped: written against four cases, it showed
 * three of them passing on a deliberately misconfigured mapper, and those three were removed rather
 * than kept as decoration. What is left is the one configuration a consumer can genuinely get
 * wrong — and the non-conforming binding below is not a contrivance but the mapper a consumer gets
 * by reaching for the obvious constructor.
 */
@DisplayName("the mapper posture TCK rejects a misconfigured mapper")
class MapperPostureTckSelfTest {

    @Test
    @DisplayName("a correctly configured mapper passes")
    void canonicalMapperPasses() {
        Posture posture = new Posture(TckMappers.canonical());
        assertThatCode(() -> {
            posture.explicitNullOnAPrimitiveIsTolerated();
            posture.mandatoryFacetsAreNotDeclaredUnsupported();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Jackson 3 on its own defaults is caught")
    void stockMapperIsCaught() {
        Posture stock = new Posture(JsonMapper.builder().build());
        assertThatThrownBy(stock::explicitNullOnAPrimitiveIsTolerated)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("FAIL_ON_NULL_FOR_PRIMITIVES");
    }

    /** A consumer binding backed by one mapper. */
    static class Posture extends AbstractMapperPostureTck {

        private final ObjectMapper mapper;

        Posture(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected <T> T readValue(String json, Class<T> type) {
            return mapper.readValue(json, type);
        }
    }
}
