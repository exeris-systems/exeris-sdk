package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lightweight records / value-objects where the test surface is just
 * static factories and accessor defaults. Bundled here so each tiny record
 * does not need its own file.
 */
@DisplayName("Small AST records: factories + accessors")
class SimpleAstRecordsTest {

    @Nested
    @DisplayName("ValidationMetadata")
    @SuppressWarnings("removal") // guards the deprecated record's factories until its 1.0.0 removal (ADR-054)
    class Validation {
        @Test
        void withNotNullSetsOnlyNotNull() {
            ValidationMetadata v = ValidationMetadata.withNotNull();
            assertThat(v.notNull()).isTrue();
            assertThat(v.notBlank()).isFalse();
            assertThat(v.email()).isFalse();
            assertThat(v.url()).isFalse();
            assertThat(v.future()).isFalse();
            assertThat(v.past()).isFalse();
        }

        @Test
        void withNotBlankSetsOnlyNotBlank() {
            ValidationMetadata v = ValidationMetadata.withNotBlank();
            assertThat(v.notNull()).isFalse();
            assertThat(v.notBlank()).isTrue();
        }

        @Test
        void withLengthCarriesMinAndMax() {
            ValidationMetadata v = ValidationMetadata.withLength(2, 50);
            assertThat(v.minLength()).isEqualTo(2);
            assertThat(v.maxLength()).isEqualTo(50);
            assertThat(v.notNull()).isFalse();
            assertThat(v.notBlank()).isFalse();
        }
    }

    @Nested
    @DisplayName("DomainEventMetadata")
    class DomainEvent {
        @Test
        void simpleFactoryLeavesOptionalFieldsNull() {
            DomainEventMetadata e = DomainEventMetadata.simple("OrderCreated");
            assertThat(e.name()).isEqualTo("OrderCreated");
            assertThat(e.topic()).isNull();
            assertThat(e.description()).isNull();
            assertThat(e.aggregateType()).isNull();
        }

        @Test
        void withTopicFactorySetsTopic() {
            DomainEventMetadata e = DomainEventMetadata.withTopic("OrderCreated", "orders.created");
            assertThat(e.topic()).isEqualTo("orders.created");
        }
    }

    @Nested
    @DisplayName("ProjectionMetadata")
    class Projection {
        @Test
        void simpleFactoryIsNonCacheable() {
            ProjectionMetadata p = ProjectionMetadata.simple("summary", List.of("id", "total"));
            assertThat(p.name()).isEqualTo("summary");
            assertThat(p.fields()).containsExactly("id", "total");
            assertThat(p.cacheable()).isFalse();
            assertThat(p.description()).isNull();
        }
    }

    @Nested
    @DisplayName("GraphMetadata + leaves")
    class Graph {
        @Test
        void simpleFactoryProducesEmptyChildren() {
            GraphMetadata g = GraphMetadata.simple("Order");
            assertThat(g.label()).isEqualTo("Order");
            assertThat(g.properties()).isEmpty();
            assertThat(g.edges()).isEmpty();
            assertThat(g.queries()).isEmpty();
        }

        @Test
        void leafRecordsExposeAccessors() {
            GraphPropertyMetadata p = new GraphPropertyMetadata("name", "String", true);
            assertThat(p.name()).isEqualTo("name");
            assertThat(p.type()).isEqualTo("String");
            assertThat(p.indexed()).isTrue();

            GraphEdgeMetadata edge = new GraphEdgeMetadata("items", "OrderItem", "HAS_ITEMS");
            assertThat(edge.name()).isEqualTo("items");
            assertThat(edge.targetLabel()).isEqualTo("OrderItem");
            assertThat(edge.relationType()).isEqualTo("HAS_ITEMS");

            GraphQueryMetadata q = new GraphQueryMetadata("findAll", "MATCH (n) RETURN n", "List all nodes");
            assertThat(q.name()).isEqualTo("findAll");
            assertThat(q.cypher()).isEqualTo("MATCH (n) RETURN n");
            assertThat(q.description()).isEqualTo("List all nodes");
        }
    }

    @Nested
    @DisplayName("SystemFieldsMetadata")
    class SystemFields {
        @Test
        void defaultsFactoryUsesConventionalNames() {
            SystemFieldsMetadata sf = SystemFieldsMetadata.defaults();
            assertThat(sf.primaryKeyField()).isEqualTo("id");
            assertThat(sf.createdAtField()).isEqualTo("createdAt");
            assertThat(sf.createdByField()).isEqualTo("createdBy");
            assertThat(sf.updatedAtField()).isEqualTo("updatedAt");
            assertThat(sf.updatedByField()).isEqualTo("updatedBy");
            assertThat(sf.tenantIdField()).isEqualTo("tenantId");
            assertThat(sf.versionField()).isEqualTo("version");
            assertThat(sf.softDeleteField()).isNull();
            assertThat(sf.softDeleteTimestampField()).isNull();
            assertThat(sf.softDeletedByField()).isNull();
        }
    }

    @Nested
    @DisplayName("InternalApiMetadata")
    class InternalApi {
        @Test
        void hiddenFactorySetsOnlyHidden() {
            InternalApiMetadata m = InternalApiMetadata.hidden("legacy");
            assertThat(m.hidden()).isTrue();
            assertThat(m.readOnly()).isFalse();
            assertThat(m.internal()).isFalse();
            assertThat(m.reason()).isEqualTo("legacy");
            assertThat(m.disabledActions()).isEmpty();
        }

        @Test
        void readOnlyFactoryDisablesCrudActions() {
            InternalApiMetadata m = InternalApiMetadata.readOnly("audit-only");
            assertThat(m.readOnly()).isTrue();
            assertThat(m.disabledActions()).containsExactly("create", "update", "delete");
            assertThat(m.isActionDisabled("create")).isTrue();
            assertThat(m.isActionDisabled("view")).isFalse();
        }

        @Test
        void internalFactorySetsOnlyInternal() {
            InternalApiMetadata m = InternalApiMetadata.internal("svc-to-svc");
            assertThat(m.internal()).isTrue();
            assertThat(m.hidden()).isFalse();
        }

        @Test
        void isActionDisabledTolerantOfNullList() {
            InternalApiMetadata m = new InternalApiMetadata(false, false, false, null, null, null, null);
            assertThat(m.isActionDisabled("anything")).isFalse();
        }

        @Test
        void builderPropagatesEverySetter() {
            InternalApiMetadata m = InternalApiMetadata.builder()
                    .hidden(true)
                    .readOnly(true)
                    .internal(true)
                    .reason("test")
                    .since("0.2.0")
                    .disabledActions(List.of("delete"))
                    .allowedRoles(List.of("ROLE_OPS"))
                    .build();
            assertThat(m.hidden()).isTrue();
            assertThat(m.readOnly()).isTrue();
            assertThat(m.internal()).isTrue();
            assertThat(m.reason()).isEqualTo("test");
            assertThat(m.since()).isEqualTo("0.2.0");
            assertThat(m.disabledActions()).containsExactly("delete");
            assertThat(m.allowedRoles()).containsExactly("ROLE_OPS");
        }
    }

    @Nested
    @DisplayName("EnumMetadata + EnumValueMetadata")
    class EnumRecord {
        @Test
        void recordsExposeAllComponents() {
            EnumMetadata.EnumValueMetadata v = new EnumMetadata.EnumValueMetadata(
                    "FREE", "Free", "No charge", 0);
            EnumMetadata e = new EnumMetadata(
                    "TenantPlan",
                    "eu.exeris.foundation.domain.TenantPlan",
                    "eu.exeris.foundation.domain",
                    "Tenant subscription plans",
                    List.of(v));
            assertThat(e.name()).isEqualTo("TenantPlan");
            assertThat(e.qualifiedName()).isEqualTo("eu.exeris.foundation.domain.TenantPlan");
            assertThat(e.packageName()).isEqualTo("eu.exeris.foundation.domain");
            assertThat(e.description()).isEqualTo("Tenant subscription plans");
            assertThat(e.values()).hasSize(1).first().isEqualTo(v);
            assertThat(v.name()).isEqualTo("FREE");
            assertThat(v.displayName()).isEqualTo("Free");
            assertThat(v.description()).isEqualTo("No charge");
            assertThat(v.ordinal()).isZero();
        }
    }

    @Nested
    @DisplayName("Capability records (Provides / Requires / CapabilityModule)")
    class Capability {
        @Test
        void providesFactoriesAndHasVersion() {
            ProvidesMetadata unversioned = ProvidesMetadata.of("com.acme.RouteRegistry");
            assertThat(unversioned.service()).isEqualTo("com.acme.RouteRegistry");
            assertThat(unversioned.version()).isNull();
            assertThat(unversioned.hasVersion()).isFalse();

            ProvidesMetadata versioned = ProvidesMetadata.of("com.acme.RouteRegistry", "1.0.0");
            assertThat(versioned.version()).isEqualTo("1.0.0");
            assertThat(versioned.hasVersion()).isTrue();
            // blank version is treated as absent
            assertThat(ProvidesMetadata.of("X", "  ").hasVersion()).isFalse();
        }

        @Test
        void requiresFactoriesAndPredicates() {
            RequiresMetadata mandatory = RequiresMetadata.of("KERNEL_TRANSPORT");
            assertThat(mandatory.service()).isEqualTo("KERNEL_TRANSPORT");
            assertThat(mandatory.versionRange()).isNull();
            assertThat(mandatory.optional()).isFalse();
            assertThat(mandatory.hasVersionRange()).isFalse();

            RequiresMetadata opt = RequiresMetadata.optional("com.acme.MetricsSink");
            assertThat(opt.optional()).isTrue();

            RequiresMetadata ranged = RequiresMetadata.of("com.acme.UpstreamPool", "[1.0.0,2.0.0)");
            assertThat(ranged.hasVersionRange()).isTrue();
            assertThat(ranged.optional()).isFalse();

            RequiresMetadata optRanged = RequiresMetadata.optional("com.acme.MetricsSink", "[2.0.0,3.0.0)");
            assertThat(optRanged.optional()).isTrue();
            assertThat(optRanged.versionRange()).isEqualTo("[2.0.0,3.0.0)");
        }

        @Test
        void mandatoryServiceFieldIsNullGuarded() {
            assertThatThrownBy(() -> ProvidesMetadata.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("service is required");
            assertThatThrownBy(() -> RequiresMetadata.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("service is required");
        }

        @Test
        void capabilityModuleEmptyAndPredicates() {
            CapabilityModuleMetadata empty = CapabilityModuleMetadata.empty();
            assertThat(empty.provides()).isEmpty();
            assertThat(empty.requires()).isEmpty();
            assertThat(empty.lifecycleOwner()).isNull();
            assertThat(empty.hasProvides()).isFalse();
            assertThat(empty.hasRequires()).isFalse();
            assertThat(empty.hasLifecycleOwner()).isFalse();

            CapabilityModuleMetadata full = CapabilityModuleMetadata.builder()
                    .provides(List.of(ProvidesMetadata.of("com.acme.RouteRegistry")))
                    .requires(List.of(RequiresMetadata.of("KERNEL_TRANSPORT")))
                    .lifecycleOwner("com.acme.GatewayLifecycle")
                    .build();
            assertThat(full.hasProvides()).isTrue();
            assertThat(full.hasRequires()).isTrue();
            assertThat(full.hasLifecycleOwner()).isTrue();
            assertThat(full.lifecycleOwner()).isEqualTo("com.acme.GatewayLifecycle");
        }
    }
}
