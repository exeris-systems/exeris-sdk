/*
 * Copyright (C) 2025 Exeris. All rights reserved.
 */
package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Locale;

/**
 * Domain metadata extracted from {@code @ExerisDomain} annotation.
 * <p>
 * This is the SINGLE SOURCE OF TRUTH for domain metadata.
 * Used by:
 * <ul>
 *   <li>exeris-codegen-java (backend code generation)</li>
 *   <li>exeris-codegen-ts (frontend code generation)</li>
 * </ul>
 *
 * <p>Field names match {@code @ExerisDomain} annotation attributes.
 *
 * @param entityName the annotated class's simple name — the entity's identity throughout the
 *        generated tree, and what {@link #pluralName()}, {@link #effectivePath()} and
 *        {@link #effectiveTableName()} derive from
 *
 * @param packageName the package the annotated class is declared in; generated Java is emitted
 *        relative to it
 *
 * @param module the logical module the entity belongs to ({@code @ExerisDomain.module})
 * @param path the base API path for the entity's generated endpoints
 * @param aggregate the aggregate root this entity belongs to, when it is not one itself
 * @param description human-readable prose for generated documentation
 * @param apiVersion the declared API version. Carried on the wire and read by no generator —
 *                   no emitted artifact publishes an {@code /api/<version>} segment, so this
 *                   does not describe where an endpoint is served (see
 *                   {@code @ExerisDomain.apiVersion}). <strong>Deprecated for removal in
 *                   1.0.0</strong>, with the attribute it carries; both producers fill it
 *                   until then
 * @param tags grouping labels carried into the generated OpenAPI/AsyncAPI document
 * @param restApi whether REST endpoints are generated for this entity
 * @param graphqlApi whether a GraphQL schema and resolvers are generated
 * @param realTimeApi whether real-time streaming endpoints (SSE/WebTransport) are generated
 * @param internalClient whether an internal HTTP client is generated for service-to-service calls
 * @param tenantScoped whether rows are tenant-partitioned. <strong>Deprecated in favour of
 *        {@link #dataScope()}</strong>, which expresses the same question as a mutually-exclusive
 *        tier rather than a boolean; read through {@link #effectiveDataScope()}, which falls back
 *        to this flag when no tier was declared
 *
 * @param softDelete whether deletion marks a row rather than removing it
 * @param audited whether created/updated timestamp and user columns are maintained
 * @param versioned whether optimistic locking is enabled through a version column
 * @param roles default roles required to reach this entity's API. Declared but not extracted:
 *        the kernel's edge decides on scopes and declares no role kind (kernel ADR-061/ADR-063),
 *        so what this would compile into is undecided
 *
 * @param permissions default permissions required to reach this entity's API — the half of the
 *        pair that maps onto a named scope, and so the one a generated route policy could carry
 *
 * @param sensitive whether the entity holds sensitive or personal data, which tightens logging,
 *        export and at-rest handling in the generated tree
 *
 * @param cacheable whether generated reads are cached
 * @param cacheTtl the cache entry lifetime, as an ISO-8601 duration
 * @param cacheRegion the cache region or namespace entries are placed in
 * @param fullTextSearch whether a full-text search index and query surface are generated
 * @param searchConfig the PostgreSQL text-search configuration the index is built with
 * @param tableName the physical table name, from {@code @ExerisDomain.tableName}. Optional:
 *        blank means "derive it", and {@link #effectiveTableName()} is the accessor that applies
 *        the default — the snake-cased English plural of {@link #entityName()}
 *
 * @param fields the entity's persisted and presented fields, in declaration order
 * @param actions the domain actions callable on the entity
 * @param events the domain events the entity publishes
 * @param relationships the entity's associations to other entities
 * @param projections the read models derived from the entity
 * @param eventHandlers the handlers this entity declares for events, its own or another's
 * @param uiMetadata the entity-level presentation facet driving generated UI
 * @param graphMetadata the graph-projection facet, when the entity is mirrored into a graph store
 * @param sagaMetadata the saga definition, when the entity carries one
 * @param eventSourced the event-sourcing facet, when the entity is sourced from its event stream
 * @param internalApi the internal-API facet: what is hidden, read-only or disabled on the
 *        generated surface
 *
 * @param systemFields which system columns (primary key, tenant, audit, soft-delete, version)
 *        the entity carries and what they are named
 *
 * @param rules the entity-level invariants declared with {@code @Rule}
 * @param dataScope the data-scope tier — the mutually-exclusive successor of
 *        {@link #tenantScoped()}. Absent means no tier was declared; read through
 *        {@link #effectiveDataScope()}
 *
 * @param routeAccess whether the entity's generated routes admit unauthenticated callers.
 *        Absent means the author declared nothing and the generated policy's default decides.
 *        Reserved: no processor extracts it and no generator emits a route policy from it, and
 *        it is outside the 1.0.0 freeze (ADR-072)
 *
 * @param channel the duplex channel this entity exposes, or absent if it exposes none. Present
 *        with no components set is a channel that declares nothing further — which is why this
 *        is a record rather than a second boolean beside {@link #realTimeApi()}. Reserved: no
 *        processor extracts it and no generator opens an endpoint from it, and it is outside the
 *        1.0.0 freeze (ADR-072)
 *
 * @since 0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainMetadata(
        // ═══════════════════════════════════════════════════════════════════
        // IDENTITY (from @ExerisDomain)
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("entityName") String entityName,
        @JsonProperty("packageName") String packageName,
        @JsonProperty("module") String module,
        @JsonProperty("path") String path,
        @JsonProperty("aggregate") String aggregate,
        @JsonProperty("description") String description,
        @JsonProperty("apiVersion") String apiVersion,
        @JsonProperty("tags") List<String> tags,

        // ═══════════════════════════════════════════════════════════════════
        // API CONFIGURATION
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("restApi") boolean restApi,
        @JsonProperty("graphqlApi") boolean graphqlApi,
        @JsonProperty("realTimeApi") boolean realTimeApi,
        @JsonProperty("internalClient") boolean internalClient,

        // ═══════════════════════════════════════════════════════════════════
        // DATA MANAGEMENT FLAGS
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("tenantScoped") boolean tenantScoped,
        @JsonProperty("softDelete") boolean softDelete,
        @JsonProperty("audited") boolean audited,
        @JsonProperty("versioned") boolean versioned,

        // ═══════════════════════════════════════════════════════════════════
        // SECURITY
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("roles") List<String> roles,
        @JsonProperty("permissions") List<String> permissions,
        @JsonProperty("sensitive") boolean sensitive,

        // ═══════════════════════════════════════════════════════════════════
        // CACHING
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("cacheable") boolean cacheable,
        @JsonProperty("cacheTtl") String cacheTtl,
        @JsonProperty("cacheRegion") String cacheRegion,

        // ═══════════════════════════════════════════════════════════════════
        // SEARCH
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("fullTextSearch") boolean fullTextSearch,
        @JsonProperty("searchConfig") String searchConfig,

        // ═══════════════════════════════════════════════════════════════════
        // DATABASE
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("tableName") String tableName,

        // ═══════════════════════════════════════════════════════════════════
        // NESTED METADATA
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("fields") List<FieldMetadata> fields,
        @JsonProperty("actions") List<ActionMetadata> actions,
        @JsonProperty("events") List<DomainEventMetadata> events,
        @JsonProperty("relationships") List<RelationshipMetadata> relationships,
        @JsonProperty("projections") List<ProjectionMetadata> projections,
        @JsonProperty("eventHandlers") List<EventHandlerMetadata> eventHandlers,

        // ═══════════════════════════════════════════════════════════════════
        // ADVANCED FEATURES
        // ═══════════════════════════════════════════════════════════════════
        @JsonProperty("uiMetadata") UIMetadata uiMetadata,
        @JsonProperty("graphMetadata") GraphMetadata graphMetadata,
        @JsonProperty("sagaMetadata") SagaMetadata sagaMetadata,
        @JsonProperty("eventSourced") EventSourcedMetadata eventSourced,
        @JsonProperty("internalApi") InternalApiMetadata internalApi,
        @JsonProperty("systemFields") SystemFieldsMetadata systemFields,

        // Entity-level invariants declared with @Rule. See RFC-2026-06-18.
        @JsonProperty("rules") List<RuleMetadata> rules,

        // Data-scope tier: the mutually-exclusive successor of the deprecated tenantScoped
        // boolean. Absent ⇒ fall back to tenantScoped via effectiveDataScope(). See RFC-2026-06-24 / ADR-059.
        @JsonProperty("dataScope") DataScope dataScope,

        // Route access: the identity half of the kernel's route-authorization decision
        // (kernel ADR-061), covering the routes generated for this entity. Absent ⇒ the
        // author declared nothing and the generated policy's default decides — there is no
        // UNSPECIFIED constant, by design. Reserved: no processor writes it and no generator
        // reads it, and the kernel holds route authorization at tier preview, so it is
        // outside the 1.0.0 freeze. See ADR-072.
        @JsonProperty("routeAccess") RouteAccess routeAccess,

        // Duplex channel: the one shape the SSE surface above cannot express, because SSE
        // is one-directional by construction and this entity's clients also speak. Absent ⇒
        // no channel; present with nothing set ⇒ a channel that declares nothing further,
        // which a boolean could not distinguish from the first. Reserved: no processor writes
        // it and no generator reads it, and the kernel holds …spi.websocket at tier preview —
        // benchmark-gated, not shape-gated (kernel ADR-084 §10) — so it is outside the 1.0.0
        // freeze. See ADR-072.
        @JsonProperty("channel") ChannelMetadata channel
) {

    /**
     * A delegating constructor for backward compatibility with code compiled against a previous
     * version of this record. Omits {@code routeAccess} and {@code channel}, both of which default
     * to {@code null}: no route access declared, and no duplex channel.
     *
     * <p>Prefer {@link #builder(String, String)}, which sets any component by name and does not
     * change shape when the record grows.
     *
     * @param entityName the {@code entityName} the result carries
     * @param packageName the {@code packageName} the result carries
     * @param module the {@code module} the result carries
     * @param path the {@code path} the result carries
     * @param aggregate the {@code aggregate} the result carries
     * @param description the {@code description} the result carries
     * @param apiVersion the {@code apiVersion} the result carries
     * @param tags the {@code tags} the result carries
     * @param restApi the {@code restApi} the result carries
     * @param graphqlApi the {@code graphqlApi} the result carries
     * @param realTimeApi the {@code realTimeApi} the result carries
     * @param internalClient the {@code internalClient} the result carries
     * @param tenantScoped the {@code tenantScoped} the result carries
     * @param softDelete the {@code softDelete} the result carries
     * @param audited the {@code audited} the result carries
     * @param versioned the {@code versioned} the result carries
     * @param roles the {@code roles} the result carries
     * @param permissions the {@code permissions} the result carries
     * @param sensitive the {@code sensitive} the result carries
     * @param cacheable the {@code cacheable} the result carries
     * @param cacheTtl the {@code cacheTtl} the result carries
     * @param cacheRegion the {@code cacheRegion} the result carries
     * @param fullTextSearch the {@code fullTextSearch} the result carries
     * @param searchConfig the {@code searchConfig} the result carries
     * @param tableName the {@code tableName} the result carries
     * @param fields the {@code fields} the result carries
     * @param actions the {@code actions} the result carries
     * @param events the {@code events} the result carries
     * @param relationships the {@code relationships} the result carries
     * @param projections the {@code projections} the result carries
     * @param eventHandlers the {@code eventHandlers} the result carries
     * @param uiMetadata the {@code uiMetadata} the result carries
     * @param graphMetadata the {@code graphMetadata} the result carries
     * @param sagaMetadata the {@code sagaMetadata} the result carries
     * @param eventSourced the {@code eventSourced} the result carries
     * @param internalApi the {@code internalApi} the result carries
     * @param systemFields the {@code systemFields} the result carries
     * @param rules the {@code rules} the result carries
     * @param dataScope the {@code dataScope} the result carries
     */
    public DomainMetadata(String entityName, String packageName, String module, String path,
                          String aggregate, String description, String apiVersion,
                          List<String> tags, boolean restApi, boolean graphqlApi,
                          boolean realTimeApi, boolean internalClient, boolean tenantScoped,
                          boolean softDelete, boolean audited, boolean versioned,
                          List<String> roles, List<String> permissions, boolean sensitive,
                          boolean cacheable, String cacheTtl, String cacheRegion,
                          boolean fullTextSearch, String searchConfig, String tableName,
                          List<FieldMetadata> fields, List<ActionMetadata> actions,
                          List<DomainEventMetadata> events,
                          List<RelationshipMetadata> relationships,
                          List<ProjectionMetadata> projections,
                          List<EventHandlerMetadata> eventHandlers, UIMetadata uiMetadata,
                          GraphMetadata graphMetadata, SagaMetadata sagaMetadata,
                          EventSourcedMetadata eventSourced, InternalApiMetadata internalApi,
                          SystemFieldsMetadata systemFields, List<RuleMetadata> rules,
                          DataScope dataScope) {
        this(entityName, packageName, module, path, aggregate, description, apiVersion, tags,
                restApi, graphqlApi, realTimeApi, internalClient, tenantScoped, softDelete,
                audited, versioned, roles, permissions, sensitive, cacheable, cacheTtl,
                cacheRegion, fullTextSearch, searchConfig, tableName, fields, actions, events,
                relationships, projections, eventHandlers, uiMetadata, graphMetadata, sagaMetadata,
                eventSourced, internalApi, systemFields, rules, dataScope, null, null);
    }

    /**
     * The declared API version — the carrier of {@code @ExerisDomain.apiVersion}.
     *
     * <p>Declared explicitly, rather than left to the record, only so it can carry the
     * deprecation; it returns the component unchanged.
     *
     * @return the declared API version, {@code "v1"} unless the source set another
     * @deprecated since 0.12.0, for removal in 1.0.0, together with
     *         {@code @ExerisDomain.apiVersion}, which no generator reads: no emitted
     *         artifact serves or requests an {@code /api/<version>} segment (see
     *         {@code MIGRATION.md}). There is no replacement — the route an entity is
     *         served at is {@link #effectivePath()}. The component stays on the wire, and
     *         both the {@code exeris-tooling} processor and the {@code -io} reader keep
     *         filling it, until 1.0.0, so baselines written before then read back
     *         unchanged.
     */
    @Deprecated(since = "0.12.0", forRemoval = true)
    public String apiVersion() {
        return apiVersion;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fully qualified class name.
     *
     * @return the {@code String}
     */
    public String fullyQualifiedName() {
        return packageName + "." + entityName;
    }

    /**
     * The table this entity is stored in: {@link #tableName()} when it is set, otherwise the
     * snake-cased {@link #pluralName()} — {@code Order} → {@code orders},
     * {@code OrderLineItem} → {@code order_line_items}, {@code Colony} → {@code colonies},
     * {@code Box} → {@code boxes}.
     *
     * <p>Snake case inserts {@code _} wherever a lower-case letter is followed by an upper-case
     * one and then lower-cases the whole name under {@link Locale#ROOT}, so the result is the
     * same on every JVM whatever its default locale (a Turkish default would otherwise turn
     * {@code Item} into {@code ıtem}). An acronym is not split: {@code HTTPRoute} →
     * {@code httproutes}.
     *
     * <p>A table whose existing name differs from the derived one — {@code colonys} where
     * this gives {@code colonies}, say — keeps it through {@code @ExerisDomain.tableName}.
     * See {@code MIGRATION.md}.
     *
     * @return the explicit table name, or the derived one
     */
    public String effectiveTableName() {
        return (tableName != null && !tableName.isBlank()) ? tableName : toSnakeCase(pluralName());
    }

    /**
     * The base route of this entity's generated endpoints: {@link #path()} when it is set,
     * otherwise {@code "/"} plus the kebab-cased {@link #pluralName()} — {@code Order} →
     * {@code /orders}, {@code OrderLineItem} → {@code /order-line-items}, {@code Colony} →
     * {@code /colonies}.
     *
     * <p>{@code @ExerisDomain.path} is mandatory, so metadata extracted from annotated source
     * always takes the first branch; the derivation serves metadata built by hand or by a
     * tool. Kebab case follows the snake-case rule of {@link #effectiveTableName()} with
     * {@code -} for {@code _}, under {@link Locale#ROOT}.
     *
     * <p>It takes the same plural as {@link #pluralName()} and {@link #effectiveTableName()},
     * so the derived route, the derived table and the title agree.
     *
     * @return the explicit path, or the derived one
     */
    public String effectivePath() {
        return (path != null && !path.isBlank()) ? path : "/" + toKebabCase(pluralName());
    }

    /**
     * Effective aggregate name.
     *
     * @return the {@code String}
     */
    public String effectiveAggregate() {
        return (aggregate != null && !aggregate.isBlank()) ? aggregate : entityName;
    }

    /**
     * The English plural of {@link #entityName()}, keeping its case — the one rule
     * {@link #effectivePath()} and {@link #effectiveTableName()} derive from too.
     *
     * <p>Applied to the end of the name, which in a camel-case compound is its last word:
     * <ul>
     *   <li>ends in {@code s}, {@code x}, {@code z}, {@code ch} or {@code sh} → {@code +es}
     *       ({@code Status} → {@code Statuses}, {@code Box} → {@code Boxes},
     *       {@code Branch} → {@code Branches});</li>
     *   <li>ends in a consonant followed by {@code y} → {@code y} becomes {@code ies}
     *       ({@code Colony} → {@code Colonies}, {@code Technology} →
     *       {@code Technologies}); a vowel before the {@code y} takes a plain {@code s}
     *       ({@code Day} → {@code Days});</li>
     *   <li>anything else → {@code +s}.</li>
     * </ul>
     *
     * <p>That is all it knows. An irregular noun gets the regular ending ({@code Person} →
     * {@code Persons}), a name that is already plural gets another one ({@code Settings} →
     * {@code Settingses}), and a {@code z} is not doubled ({@code Quiz} → {@code Quizes}).
     * Those are what {@code @ExerisDomain.path} and {@code @ExerisDomain.tableName} are for;
     * the rule itself stays small and deterministic, because its output is part of the
     * frozen contract from 1.0.0 and a later change would silently rename tables. An empty
     * or absent {@code entityName} yields {@code ""}.
     *
     * @return the plural entity name
     */
    public String pluralName() {
        return plural(entityName);
    }

    /**
     * Display name for entity (same as entityName but can add spaces before capitals).
     *
     * @return the {@code String}
     */
    public String displayName() {
        // Add space before capital letters: "OrderItem" -> "Order Item"
        return entityName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    /**
     * Whether any {@code fields} is declared.
     *
     * @return {@code true} when {@link #fields()} is neither null nor empty
     */
    public boolean hasFields() {
        return fields != null && !fields.isEmpty();
    }

    /**
     * Whether any {@code actions} is declared.
     *
     * @return {@code true} when {@link #actions()} is neither null nor empty
     */
    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    /**
     * Whether any {@code events} is declared.
     *
     * @return {@code true} when {@link #events()} is neither null nor empty
     */
    public boolean hasEvents() {
        return events != null && !events.isEmpty();
    }

    /**
     * Whether any {@code eventHandlers} is declared.
     *
     * @return {@code true} when {@link #eventHandlers()} is neither null nor empty
     */
    public boolean hasEventHandlers() {
        return eventHandlers != null && !eventHandlers.isEmpty();
    }

    /**
     * Whether any {@code rules} is declared.
     *
     * @return {@code true} when {@link #rules()} is neither null nor empty
     */
    public boolean hasRules() {
        return rules != null && !rules.isEmpty();
    }

    /**
     * Whether any {@code relationships} is declared.
     *
     * @return {@code true} when {@link #relationships()} is neither null nor empty
     */
    public boolean hasRelationships() {
        return relationships != null && !relationships.isEmpty();
    }

    /**
     * Find a field by name.
     *
     * @param fieldName the {@code fieldName} the result carries
     * @return the {@code java.util.Optional}
     */
    public java.util.Optional<FieldMetadata> findField(String fieldName) {
        if (fields == null || fieldName == null) {
            return java.util.Optional.empty();
        }
        return fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst();
    }

    /**
     * Whether a {@code eventSourced} is declared.
     *
     * @return {@code true} when {@link #eventSourced()} is present
     */
    public boolean isEventSourced() {
        return eventSourced != null;
    }

    /**
     * Whether a {@code sagaMetadata} is declared.
     *
     * @return {@code true} when {@link #sagaMetadata()} is present
     */
    public boolean isSaga() {
        return sagaMetadata != null;
    }

    /**
     * Whether a {@code graphMetadata} is declared.
     *
     * @return {@code true} when {@link #graphMetadata()} is present
     */
    public boolean hasGraphMetadata() {
        return graphMetadata != null;
    }

    /**
     * Whether the entity is marked internal — service-to-service only, not part
     * of the public API surface.
     *
     * <p>Keys off {@link InternalApiMetadata#internal()}, which is the component
     * both extraction paths populate: the {@code exeris-tooling} processor
     * ({@code ExerisDomainProcessor.extractInternalApiMetadata}) and the
     * {@code -io} reader ({@code SourceModelReader.internalApi}) both map the
     * mere presence of {@code @InternalApi} to {@code internal = true} and leave
     * every other component at its default, because the SDK annotation and this
     * AST record describe different concepts (a known, documented drift). Reading
     * {@link InternalApiMetadata#hidden()} instead — as this method did through
     * 0.10.0 — made it {@code false} for every entity on the build-time path, and
     * {@code false} even for {@link InternalApiMetadata#internal(String)}, the
     * factory named after it.
     *
     * @return {@code true} when {@code @InternalApi} metadata is present and marks
     *         the entity internal
     */
    public boolean isInternal() {
        return internalApi != null && internalApi.internal();
    }

    /**
     * The entity's effective {@link DataScope}: the explicit {@link #dataScope()}
     * if one is set, otherwise the fallback from the deprecated
     * {@link #tenantScoped()} boolean — {@code true} reads as
     * {@link DataScope#TENANT}, {@code false} as {@link DataScope#GLOBAL}.
     *
     * <p>This is the AST half of the {@code tenantScoped} deprecation window
     * (0.10.0 → removal at 1.0.0): a baseline or metadata file written before
     * 0.10.0 carries only the boolean, and reads back with the same meaning it
     * always had. Never returns {@code null} — an entity always has a tier.
     *
     * @return the declared tier, or the {@code tenantScoped} fallback
     * @since 0.10
     */
    public DataScope effectiveDataScope() {
        if (dataScope != null) {
            return dataScope;
        }
        return tenantScoped ? DataScope.TENANT : DataScope.GLOBAL;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUILDER (for easier construction in processor)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts a builder for a {@code DomainMetadata}.
     *
     * @param entityName the {@code entityName} the result carries
     * @param packageName the {@code packageName} the result carries
     * @return a new builder
     */
    public static Builder builder(String entityName, String packageName) {
        return new Builder(entityName, packageName);
    }

    private static String plural(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("z")
                || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        if (name.endsWith("y") && name.length() > 1
                && "aeiou".indexOf(name.charAt(name.length() - 2)) < 0) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        return name + "s";
    }

    private static String toSnakeCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String toKebabCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /**
     * A mutable builder for {@code DomainMetadata}.
     *
     * <p>Each setter sets the record component of the same name. Those components are
     * documented by the record's own {@code @param} tags and are deliberately not restated
     * here — a per-setter repetition of the component's meaning is filler, and filler is what
     * makes generated javadoc worth less than none.
     */
    public static final class Builder {
        private final String entityName;
        private final String packageName;
        private String module = "";
        private String path = "";
        private String aggregate = "";
        private String description = "";
        private String apiVersion = "v1";
        private List<String> tags = List.of();
        private boolean restApi = true;
        private boolean graphqlApi = false;
        private boolean realTimeApi = false;
        private boolean internalClient = false;
        private boolean tenantScoped = false;
        private boolean softDelete = false;
        private boolean audited = false;
        private boolean versioned = false;
        private List<String> roles = List.of();
        private List<String> permissions = List.of();
        private boolean sensitive = false;
        private boolean cacheable = false;
        private String cacheTtl = "PT5M";
        private String cacheRegion = "";
        private boolean fullTextSearch = false;
        private String searchConfig = "english";
        private String tableName = "";
        private List<FieldMetadata> fields = List.of();
        private List<ActionMetadata> actions = List.of();
        private List<DomainEventMetadata> events = List.of();
        private List<RelationshipMetadata> relationships = List.of();
        private List<ProjectionMetadata> projections = List.of();
        private List<EventHandlerMetadata> eventHandlers = List.of();
        private List<RuleMetadata> rules = List.of();
        private UIMetadata uiMetadata = null;
        private GraphMetadata graphMetadata = null;
        private SagaMetadata sagaMetadata = null;
        private EventSourcedMetadata eventSourced = null;
        private InternalApiMetadata internalApi = null;
        private SystemFieldsMetadata systemFields = null;
        private DataScope dataScope = null;
        private RouteAccess routeAccess = null;
        private ChannelMetadata channel = null;

        private Builder(String entityName, String packageName) {
            this.entityName = entityName;
            this.packageName = packageName;
        }

        public Builder module(String v) { this.module = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder aggregate(String v) { this.aggregate = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        /**
         * Sets {@link DomainMetadata#apiVersion()}.
         *
         * @param v the declared API version
         * @return this builder
         * @deprecated since 0.12.0, for removal in 1.0.0, with the component it sets — see
         *         {@link DomainMetadata#apiVersion()}. Producers that mirror
         *         {@code @ExerisDomain.apiVersion} keep calling it until then; nothing else
         *         should start.
         */
        @Deprecated(since = "0.12.0", forRemoval = true)
        public Builder apiVersion(String v) { this.apiVersion = v; return this; }
        public Builder tags(List<String> v) { this.tags = v; return this; }
        public Builder restApi(boolean v) { this.restApi = v; return this; }
        public Builder graphqlApi(boolean v) { this.graphqlApi = v; return this; }
        public Builder realTimeApi(boolean v) { this.realTimeApi = v; return this; }
        public Builder internalClient(boolean v) { this.internalClient = v; return this; }
        public Builder tenantScoped(boolean v) { this.tenantScoped = v; return this; }
        public Builder softDelete(boolean v) { this.softDelete = v; return this; }
        public Builder audited(boolean v) { this.audited = v; return this; }
        public Builder versioned(boolean v) { this.versioned = v; return this; }
        public Builder roles(List<String> v) { this.roles = v; return this; }
        public Builder permissions(List<String> v) { this.permissions = v; return this; }
        public Builder sensitive(boolean v) { this.sensitive = v; return this; }
        public Builder cacheable(boolean v) { this.cacheable = v; return this; }
        public Builder cacheTtl(String v) { this.cacheTtl = v; return this; }
        public Builder cacheRegion(String v) { this.cacheRegion = v; return this; }
        public Builder fullTextSearch(boolean v) { this.fullTextSearch = v; return this; }
        public Builder searchConfig(String v) { this.searchConfig = v; return this; }
        public Builder tableName(String v) { this.tableName = v; return this; }
        public Builder fields(List<FieldMetadata> v) { this.fields = v; return this; }
        public Builder actions(List<ActionMetadata> v) { this.actions = v; return this; }
        public Builder events(List<DomainEventMetadata> v) { this.events = v; return this; }
        public Builder relationships(List<RelationshipMetadata> v) { this.relationships = v; return this; }
        public Builder projections(List<ProjectionMetadata> v) { this.projections = v; return this; }
        public Builder eventHandlers(List<EventHandlerMetadata> v) { this.eventHandlers = v; return this; }
        public Builder rules(List<RuleMetadata> v) { this.rules = v; return this; }
        public Builder uiMetadata(UIMetadata v) { this.uiMetadata = v; return this; }
        public Builder graphMetadata(GraphMetadata v) { this.graphMetadata = v; return this; }
        public Builder sagaMetadata(SagaMetadata v) { this.sagaMetadata = v; return this; }
        public Builder eventSourced(EventSourcedMetadata v) { this.eventSourced = v; return this; }
        public Builder internalApi(InternalApiMetadata v) { this.internalApi = v; return this; }
        public Builder systemFields(SystemFieldsMetadata v) { this.systemFields = v; return this; }
        public Builder dataScope(DataScope v) { this.dataScope = v; return this; }
        public Builder routeAccess(RouteAccess v) { this.routeAccess = v; return this; }
        public Builder channel(ChannelMetadata v) { this.channel = v; return this; }

        /**
         * Builds the {@code DomainMetadata} from this builder's current state.
         *
         * @return the built {@code DomainMetadata}
         */
        public DomainMetadata build() {
            return new DomainMetadata(
                    entityName, packageName, module, path, aggregate, description, apiVersion, tags,
                    restApi, graphqlApi, realTimeApi, internalClient,
                    tenantScoped, softDelete, audited, versioned,
                    roles, permissions, sensitive,
                    cacheable, cacheTtl, cacheRegion,
                    fullTextSearch, searchConfig,
                    tableName,
                    fields, actions, events, relationships, projections, eventHandlers,
                    uiMetadata, graphMetadata, sagaMetadata, eventSourced, internalApi, systemFields,
                    rules, dataScope, routeAccess, channel
            );
        }
    }
}

