/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The constraints {@code BriefSchemaProcessor} writes into {@code brief-schema.json}, declared on
 * the record components they constrain.
 *
 * <p>Package-private and {@code @Retention(SOURCE)}: the processor reads them during this
 * module's own compilation and nothing else does. They are not part of the published surface,
 * and a class file carries none of them. {@link BriefReadiness} applies the same rules to a bound
 * brief, and {@code BriefSchemaContractTest} runs every fixture through both, so the schema and
 * the Java check cannot disagree unnoticed.
 */
final class SchemaRules {

    private SchemaRules() {
    }

    /** The property must be present: it enters its object's {@code required} list. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface Required {
    }

    /**
     * A string, or each string of a list, must contain a non-whitespace character.
     * Emitted as {@code minLength: 1} with {@code pattern: "\\S"}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface NonBlank {
    }

    /** A string must match {@link #value()}, an anchored ECMA-262 regular expression. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface Pattern {

        /**
         * The regular expression, anchored with {@code ^} and {@code $}.
         *
         * @return the regular expression
         */
        String value();
    }

    /** A list must hold at least {@link #value()} elements; implies {@link Required}. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface MinItems {

        /**
         * The minimum element count.
         *
         * @return the minimum element count
         */
        int value();
    }

    /**
     * Narrows a list of {@link FulfilledBy} to the reference kinds admitted at this position, by
     * their wire names.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface Kinds {

        /**
         * The admitted kinds' wire names.
         *
         * @return the admitted kinds' wire names
         */
        String[] value();
    }

    /**
     * Each string of the list names an annotation of the SDK catalog. The schema draws the
     * admitted names from the annotation sources of the same build.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.RECORD_COMPONENT)
    @interface ConcernHint {
    }

    /**
     * A conditional rule on a record: when {@link #property()} has one of {@link #values()}, the
     * properties in {@link #require()} must be present, those in {@link #forbid()} must be
     * absent, and {@link #patternProperty()}, when set, must match {@link #pattern()}. Emitted as
     * one {@code if}/{@code then} entry of the definition's {@code allOf}.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @Repeatable(WhenAll.class)
    @interface When {

        /**
         * The discriminating property.
         *
         * @return the discriminating property
         */
        String property();

        /**
         * The discriminator's wire values this rule applies to.
         *
         * @return the discriminator's wire values
         */
        String[] values();

        /**
         * Properties that must be present.
         *
         * @return properties that must be present
         */
        String[] require() default {};

        /**
         * Properties that must be absent.
         *
         * @return properties that must be absent
         */
        String[] forbid() default {};

        /**
         * A string property whose value {@link #pattern()} constrains; empty for none.
         *
         * @return the constrained property, or empty
         */
        String patternProperty() default "";

        /**
         * The ECMA-262 regular expression {@link #patternProperty()} must match.
         *
         * @return the regular expression, or empty
         */
        String pattern() default "";
    }

    /** Container for repeated {@link When}. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface WhenAll {

        /**
         * The repeated rules.
         *
         * @return the repeated rules
         */
        When[] value();
    }
}
