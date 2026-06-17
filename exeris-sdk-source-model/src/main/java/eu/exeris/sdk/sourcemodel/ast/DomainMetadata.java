/*
 * Copyright (C) 2025 Exeris. All rights reserved.
 */
package eu.exeris.sdk.sourcemodel.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
 * @author Exeris SDK Team
 * @since 0.1.0
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
        @JsonProperty("systemFields") SystemFieldsMetadata systemFields
) {

    // ═══════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Fully qualified class name.
     */
    public String fullyQualifiedName() {
        return packageName + "." + entityName;
    }

    /**
     * Effective table name (explicit or derived from entityName).
     */
    public String effectiveTableName() {
        return (tableName != null && !tableName.isBlank()) ? tableName : toSnakeCase(entityName);
    }

    /**
     * Effective API path (explicit or derived from entityName).
     */
    public String effectivePath() {
        return (path != null && !path.isBlank()) ? path : "/" + toKebabCase(entityName) + "s";
    }

    /**
     * Effective aggregate name.
     */
    public String effectiveAggregate() {
        return (aggregate != null && !aggregate.isBlank()) ? aggregate : entityName;
    }

    /**
     * Plural name for entity (derived from entityName).
     */
    public String pluralName() {
        // Simple pluralization - add 's' to entityName
        if (entityName.endsWith("s") || entityName.endsWith("x") || entityName.endsWith("z")
                || entityName.endsWith("ch") || entityName.endsWith("sh")) {
            return entityName + "es";
        }
        if (entityName.endsWith("y") && entityName.length() > 1) {
            char beforeY = entityName.charAt(entityName.length() - 2);
            if (!"aeiou".contains(String.valueOf(beforeY))) {
                return entityName.substring(0, entityName.length() - 1) + "ies";
            }
        }
        return entityName + "s";
    }

    /**
     * Display name for entity (same as entityName but can add spaces before capitals).
     */
    public String displayName() {
        // Add space before capital letters: "OrderItem" -> "Order Item"
        return entityName.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    public boolean hasFields() {
        return fields != null && !fields.isEmpty();
    }

    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    public boolean hasEvents() {
        return events != null && !events.isEmpty();
    }

    public boolean hasEventHandlers() {
        return eventHandlers != null && !eventHandlers.isEmpty();
    }

    public boolean hasRelationships() {
        return relationships != null && !relationships.isEmpty();
    }

    /**
     * Find a field by name.
     */
    public java.util.Optional<FieldMetadata> findField(String fieldName) {
        if (fields == null || fieldName == null) {
            return java.util.Optional.empty();
        }
        return fields.stream()
                .filter(f -> fieldName.equals(f.name()))
                .findFirst();
    }

    public boolean isEventSourced() {
        return eventSourced != null;
    }

    public boolean isSaga() {
        return sagaMetadata != null;
    }

    public boolean hasGraphMetadata() {
        return graphMetadata != null;
    }

    public boolean isInternal() {
        return internalApi != null && internalApi.hidden();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUILDER (for easier construction in processor)
    // ═══════════════════════════════════════════════════════════════════════

    public static Builder builder(String entityName, String packageName) {
        return new Builder(entityName, packageName);
    }

    private static String toSnakeCase(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String toKebabCase(String s) {
        if (s == null || s.isBlank()) return "";
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

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
        private UIMetadata uiMetadata = null;
        private GraphMetadata graphMetadata = null;
        private SagaMetadata sagaMetadata = null;
        private EventSourcedMetadata eventSourced = null;
        private InternalApiMetadata internalApi = null;
        private SystemFieldsMetadata systemFields = null;

        private Builder(String entityName, String packageName) {
            this.entityName = entityName;
            this.packageName = packageName;
        }

        public Builder module(String v) { this.module = v; return this; }
        public Builder path(String v) { this.path = v; return this; }
        public Builder aggregate(String v) { this.aggregate = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
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
        public Builder uiMetadata(UIMetadata v) { this.uiMetadata = v; return this; }
        public Builder graphMetadata(GraphMetadata v) { this.graphMetadata = v; return this; }
        public Builder sagaMetadata(SagaMetadata v) { this.sagaMetadata = v; return this; }
        public Builder eventSourced(EventSourcedMetadata v) { this.eventSourced = v; return this; }
        public Builder internalApi(InternalApiMetadata v) { this.internalApi = v; return this; }
        public Builder systemFields(SystemFieldsMetadata v) { this.systemFields = v; return this; }

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
                    uiMetadata, graphMetadata, sagaMetadata, eventSourced, internalApi, systemFields
            );
        }
    }
}

