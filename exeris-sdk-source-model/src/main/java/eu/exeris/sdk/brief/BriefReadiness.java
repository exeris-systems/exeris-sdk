/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a brief is ready for authoring: the readiness gate of ADR-106, as a function.
 *
 * <p>A brief is <b>ready</b> when it is valid and no blocking question is {@code open}. It is
 * <b>valid</b> when it meets every constraint {@code brief-schema.json} states: a non-empty
 * purpose, at least one actor, at least one workflow with at least one criterion, at least one
 * non-goal, EARS criteria with the fields their kind requires, an {@code assumed} question with
 * its assumption and owner, and references of the kinds their position admits. An absent brief
 * ({@code null}) is not a brief and is never ready; an empty one is invalid and so is not ready
 * either.
 *
 * <p>Two of the schema's checks are not visible on a bound record, and the caller supplies what
 * they need:
 * <ul>
 *   <li><b>Unknown properties.</b> The schema rejects any property it does not name, and that is
 *       what keeps a brief from re-encoding a source structure. A binding reader that ignores
 *       unknown properties (Jackson 3's default) drops such a property before this function sees
 *       the record. Validate the document against the schema, or bind with unknown properties
 *       failing, before calling this.</li>
 *   <li><b>The concern-hint vocabulary.</b> The schema admits the annotation names of the SDK
 *       catalog. Pass those names to check hints here; pass {@code null} to leave them to the
 *       schema.</li>
 * </ul>
 *
 * <p>References ({@link FulfilledBy}) are resolved only against a set of known source elements
 * the caller builds from the project's emitted metadata. Without one they are reported
 * {@linkplain Report#unchecked() unchecked}, not failed. A reference that resolves to nothing is
 * reported {@linkplain Report#unresolved() unresolved}; it does not change readiness, which the
 * contract defines by validity and open questions alone.
 *
 * @since 0.13
 */
public final class BriefReadiness {

    private static final Pattern NON_BLANK = Pattern.compile("\\S");
    private static final Pattern SDK_VERSION = Pattern.compile(Brief.SDK_VERSION_PATTERN);
    private static final Pattern ENTITY_REF = Pattern.compile(FulfilledBy.ENTITY_REF_PATTERN);
    private static final Pattern MEMBER_REF = Pattern.compile(FulfilledBy.MEMBER_REF_PATTERN);

    private BriefReadiness() {
    }

    /**
     * Evaluates a brief with concern hints left to the schema and references unchecked.
     *
     * @param brief the bound brief, or {@code null} when there is none
     * @return the verdict
     */
    public static Report evaluate(Brief brief) {
        return evaluate(brief, null, null);
    }

    /**
     * Evaluates a brief.
     *
     * @param brief             the bound brief, or {@code null} when there is none
     * @param concernVocabulary the admitted concern-hint names, or {@code null} to leave them to
     *                          the schema
     * @param source            the source elements that exist, or {@code null} when the project
     *                          has emitted no metadata; references are then reported unchecked
     * @return the verdict
     */
    public static Report evaluate(Brief brief, Set<String> concernVocabulary, Set<FulfilledBy> source) {
        if (brief == null) {
            return new Report(false, false,
                    List.of(new Problem("", "no brief: an absent or empty document is not a brief")),
                    List.of(), List.of(), List.of());
        }
        List<Problem> errors = new ArrayList<>();
        List<FulfilledBy> references = new ArrayList<>();

        if (brief.briefSchema() == null) {
            errors.add(missing("/briefSchema"));
        } else if (!SDK_VERSION.matcher(brief.briefSchema()).matches()) {
            errors.add(new Problem("/briefSchema", "must be an SDK version such as 0.13.0"));
        }
        requiredText(errors, "/purpose", brief.purpose());
        texts(errors, "/goals", brief.goals());
        minItems(errors, "/nonGoals", brief.nonGoals());
        texts(errors, "/nonGoals", brief.nonGoals());
        minItems(errors, "/actors", brief.actors());
        minItems(errors, "/workflows", brief.workflows());
        texts(errors, "/candidateCapabilities", brief.candidateCapabilities());

        for (int i = 0; i < brief.actors().size(); i++) {
            Actor actor = brief.actors().get(i);
            String at = "/actors/" + i;
            requiredText(errors, at + "/name", actor.name());
            texts(errors, at + "/roles", actor.roles());
        }
        for (int i = 0; i < brief.glossary().size(); i++) {
            GlossaryTerm term = brief.glossary().get(i);
            String at = "/glossary/" + i;
            requiredText(errors, at + "/term", term.term());
            requiredText(errors, at + "/meaning", term.meaning());
            if (concernVocabulary != null) {
                for (int h = 0; h < term.concernHints().size(); h++) {
                    if (!concernVocabulary.contains(term.concernHints().get(h))) {
                        errors.add(new Problem(at + "/concernHints/" + h,
                                "names no annotation of the SDK catalog"));
                    }
                }
            }
            references(errors, references, at + "/fulfilledBy", term.fulfilledBy(),
                    Set.of(FulfilledBy.Kind.ENTITY));
        }
        for (int i = 0; i < brief.workflows().size(); i++) {
            Workflow workflow = brief.workflows().get(i);
            String at = "/workflows/" + i;
            requiredText(errors, at + "/name", workflow.name());
            texts(errors, at + "/actors", workflow.actors());
            minItems(errors, at + "/criteria", workflow.criteria());
            for (int c = 0; c < workflow.criteria().size(); c++) {
                criterion(errors, at + "/criteria/" + c, workflow.criteria().get(c));
            }
            references(errors, references, at + "/fulfilledBy", workflow.fulfilledBy(),
                    Set.of(FulfilledBy.Kind.ACTION, FulfilledBy.Kind.RULE, FulfilledBy.Kind.SAGA));
        }
        for (int i = 0; i < brief.nonFunctional().size(); i++) {
            NonFunctionalRequirement nfr = brief.nonFunctional().get(i);
            String at = "/nonFunctional/" + i;
            if (nfr.category() == null) {
                errors.add(missing(at + "/category"));
            }
            requiredText(errors, at + "/requirement", nfr.requirement());
        }
        for (int i = 0; i < brief.integrations().size(); i++) {
            Integration integration = brief.integrations().get(i);
            String at = "/integrations/" + i;
            requiredText(errors, at + "/name", integration.name());
            optionalText(errors, at + "/purpose", integration.purpose());
        }

        List<Assumption> assumed = new ArrayList<>();
        boolean blockingOpen = false;
        for (int i = 0; i < brief.openQuestions().size(); i++) {
            OpenQuestion question = brief.openQuestions().get(i);
            String at = "/openQuestions/" + i;
            requiredText(errors, at + "/id", question.id());
            requiredText(errors, at + "/question", question.question());
            optionalText(errors, at + "/assumption", question.assumption());
            optionalText(errors, at + "/owner", question.owner());
            optionalText(errors, at + "/answer", question.answer());
            if (question.blocking() == null) {
                errors.add(missing(at + "/blocking"));
            }
            if (question.status() == null) {
                errors.add(missing(at + "/status"));
            } else if (question.status() == OpenQuestion.Status.ASSUMED) {
                if (question.assumption() == null) {
                    errors.add(new Problem(at + "/assumption", "an assumed question must record its assumption"));
                }
                if (question.owner() == null) {
                    errors.add(new Problem(at + "/owner", "an assumed question must name its owner"));
                }
                assumed.add(new Assumption(question.id(), question.owner(), question.assumption()));
            } else if (question.status() == OpenQuestion.Status.OPEN && Boolean.TRUE.equals(question.blocking())) {
                blockingOpen = true;
            }
        }

        List<FulfilledBy> unresolved = new ArrayList<>();
        List<FulfilledBy> unchecked = new ArrayList<>();
        for (FulfilledBy reference : references) {
            if (source == null) {
                unchecked.add(reference);
            } else if (!source.contains(reference)) {
                unresolved.add(reference);
            }
        }

        boolean valid = errors.isEmpty();
        return new Report(valid, valid && !blockingOpen, errors, assumed, unresolved, unchecked);
    }

    private static void criterion(List<Problem> errors, String at, EarsCriterion criterion) {
        requiredText(errors, at + "/response", criterion.response());
        optionalText(errors, at + "/trigger", criterion.trigger());
        optionalText(errors, at + "/condition", criterion.condition());
        if (criterion.kind() == null) {
            errors.add(missing(at + "/kind"));
            return;
        }
        switch (criterion.kind()) {
            case UBIQUITOUS -> {
                forbidden(errors, at + "/trigger", criterion.trigger(), "a ubiquitous criterion has no trigger");
                forbidden(errors, at + "/condition", criterion.condition(), "a ubiquitous criterion has no condition");
            }
            case WHEN, IF -> {
                if (criterion.trigger() == null) {
                    errors.add(new Problem(at + "/trigger", "a when or if criterion needs a trigger"));
                }
            }
            case WHILE -> {
                if (criterion.condition() == null) {
                    errors.add(new Problem(at + "/condition", "a while criterion needs a condition"));
                }
                forbidden(errors, at + "/trigger", criterion.trigger(), "a while criterion has no trigger");
            }
        }
    }

    private static void references(List<Problem> errors, List<FulfilledBy> into, String at,
                                   List<FulfilledBy> references, Set<FulfilledBy.Kind> admitted) {
        for (int i = 0; i < references.size(); i++) {
            FulfilledBy reference = references.get(i);
            String here = at + "/" + i;
            boolean ok = true;
            if (reference.kind() == null) {
                errors.add(missing(here + "/kind"));
                ok = false;
            } else if (!admitted.contains(reference.kind())) {
                errors.add(new Problem(here + "/kind", "this position does not admit the "
                        + reference.kind().name().toLowerCase(java.util.Locale.ROOT) + " kind"));
                ok = false;
            }
            if (reference.ref() == null) {
                errors.add(missing(here + "/ref"));
                ok = false;
            } else if (reference.kind() != null) {
                Pattern shape = reference.kind() == FulfilledBy.Kind.ENTITY ? ENTITY_REF : MEMBER_REF;
                if (!shape.matcher(reference.ref()).matches()) {
                    errors.add(new Problem(here + "/ref", reference.kind() == FulfilledBy.Kind.ENTITY
                            ? "an entity reference is a qualified class name"
                            : "a member reference is <entity qualified name>#<member name>"));
                    ok = false;
                }
            }
            if (ok) {
                into.add(reference);
            }
        }
    }

    private static Problem missing(String path) {
        return new Problem(path, "is required");
    }

    private static void requiredText(List<Problem> errors, String path, String value) {
        if (value == null) {
            errors.add(missing(path));
        } else {
            optionalText(errors, path, value);
        }
    }

    private static void optionalText(List<Problem> errors, String path, String value) {
        if (value != null && !NON_BLANK.matcher(value).find()) {
            errors.add(new Problem(path, "must not be blank"));
        }
    }

    private static void texts(List<Problem> errors, String path, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            optionalText(errors, path + "/" + i, values.get(i));
        }
    }

    private static void minItems(List<Problem> errors, String path, List<?> values) {
        if (values.isEmpty()) {
            errors.add(new Problem(path, "needs at least one entry"));
        }
    }

    private static void forbidden(List<Problem> errors, String path, String value, String message) {
        if (value != null) {
            errors.add(new Problem(path, message));
        }
    }

    /**
     * The verdict on one brief.
     *
     * @param valid      whether the brief meets every constraint of the brief schema this function
     *                   can see
     * @param ready      whether the brief is valid and no blocking question is {@code open}
     * @param errors     what makes the brief invalid, each at its JSON Pointer; empty when valid
     * @param assumed    every question decided on credit, with its owner and assumption
     * @param unresolved references that name no element of the supplied source
     * @param unchecked  references not checked, because no source was supplied
     * @since 0.13
     */
    public record Report(
            boolean valid,
            boolean ready,
            List<Problem> errors,
            List<Assumption> assumed,
            List<FulfilledBy> unresolved,
            List<FulfilledBy> unchecked
    ) {

        /**
         * Compact constructor; normalizes an absent list to an empty one.
         *
         * @throws NullPointerException if a list holds a {@code null} element
         */
        public Report {
            errors = BriefLists.copy(errors, "errors");
            assumed = BriefLists.copy(assumed, "assumed");
            unresolved = BriefLists.copy(unresolved, "unresolved");
            unchecked = BriefLists.copy(unchecked, "unchecked");
        }
    }

    /**
     * One constraint a brief does not meet.
     *
     * @param path    the JSON Pointer of the offending value, empty for the document itself
     * @param message what is wrong with it
     * @since 0.13
     */
    public record Problem(String path, String message) {
    }

    /**
     * A question decided on credit.
     *
     * @param questionId the question's {@link OpenQuestion#id()}
     * @param owner      who answers for the assumption
     * @param assumption the assumption authoring proceeds on
     * @since 0.13
     */
    public record Assumption(String questionId, String owner, String assumption) {
    }
}
