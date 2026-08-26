package eu.exeris.sdk.tck;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The mapper posture the SDK's wire format is specified against.
 *
 * <p>Exists so a producer under test is never judged by a misconfigured reader. Jackson 3 defaults
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} to {@code true} and the AST is primitive-boolean heavy, so a
 * mapper on the default setting throws on any explicit {@code null} standing where one of them is
 * declared — and a producer TCK failing for that reason would be reporting the kit's own
 * misconfiguration as the binder's defect.
 *
 * <p>Note what this is <em>not</em>: absent properties are fine either way. An absent property
 * binds the primitive's own default and raises nothing, on any mapper. The version of this
 * paragraph that shipped in the first draft of this class said otherwise — that
 * {@code @JsonInclude(NON_DEFAULT)} makes absent fields "arrive as {@code null}" — which is the
 * same folklore {@link AbstractMapperPostureTck}'s self-test falsified by measurement, and it is
 * recorded here rather than quietly deleted because it had already survived one correction pass in
 * two other files.
 *
 * <p>This is the kit's <em>internal</em> reference posture. It is deliberately <strong>not</strong>
 * what {@link AbstractMapperPostureTck} tests a consumer against: that suite takes no Jackson type
 * at all, because its whole subject is whether the consumer's own mapper is configured correctly.
 * Handing them this one would answer the question by assuming it.
 */
public final class TckMappers {

    private TckMappers() {
        // Intentionally empty: static holder — the class exists only to own canonical().
    }

    /**
     * A mapper configured the way the SDK's consumer contract requires.
     *
     * @return a fresh mapper; callers may reconfigure their own copy freely
     */
    public static ObjectMapper canonical() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .build();
    }
}
