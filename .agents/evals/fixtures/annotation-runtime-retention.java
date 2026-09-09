// Proposed addition to exeris-sdk-annotations.
package eu.exeris.sdk.annotation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a field the generated client should cache. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cached {
    @JsonProperty("ttl")
    int ttlSeconds() default 60;
}
