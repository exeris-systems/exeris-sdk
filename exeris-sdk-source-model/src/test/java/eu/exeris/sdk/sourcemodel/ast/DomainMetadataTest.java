package eu.exeris.sdk.sourcemodel.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the {@link DomainMetadata} convenience methods and Builder defaults.
 * The round-trip wire-format test ({@link AstJsonRoundTripTest}) exercises
 * full-object serialization; this class targets branching logic
 * (effective*, plural rules, find*, is*) that round-trip alone cannot reach.
 */
@DisplayName("DomainMetadata: derived methods + Builder")
class DomainMetadataTest {

    private static DomainMetadata.Builder base() {
        return DomainMetadata.builder("Order", "com.acme.domain");
    }

    @Nested
    @DisplayName("Identity / naming derivations")
    class Naming {
        @Test
        void fullyQualifiedNameJoinsPackageAndEntity() {
            assertThat(base().build().fullyQualifiedName()).isEqualTo("com.acme.domain.Order");
        }

        @Test
        void effectiveTableNameUsesExplicitWhenProvided() {
            assertThat(base().tableName("orders_v2").build().effectiveTableName()).isEqualTo("orders_v2");
        }

        @Test
        void effectiveTableNameDerivesTheSnakeCasedPluralWhenBlank() {
            // Empty string is the Builder default for tableName. Until 0.12.0 this returned the
            // singular "order", a table no generator ever emitted.
            assertThat(base().build().effectiveTableName()).isEqualTo("orders");
        }

        @Test
        void effectiveTableNameSnakeCasesCamelEntityName() {
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().effectiveTableName())
                    .isEqualTo("order_line_items");
        }

        @Test
        void effectivePathUsesExplicitWhenProvided() {
            assertThat(base().path("/v2/orders").build().effectivePath()).isEqualTo("/v2/orders");
        }

        @Test
        void effectivePathDerivesKebabPluralWhenBlank() {
            assertThat(base().build().effectivePath()).isEqualTo("/orders");
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().effectivePath())
                    .isEqualTo("/order-line-items");
        }

        @Test
        void effectiveAggregateFallsBackToEntityName() {
            assertThat(base().build().effectiveAggregate()).isEqualTo("Order");
            assertThat(base().aggregate("CustomerOrder").build().effectiveAggregate()).isEqualTo("CustomerOrder");
        }

        @Test
        void displayNameSplitsOnCapitals() {
            assertThat(DomainMetadata.builder("OrderLineItem", "p").build().displayName())
                    .isEqualTo("Order Line Item");
            assertThat(base().build().displayName()).isEqualTo("Order");
        }
    }

    @Nested
    @DisplayName("pluralName covers each branch")
    class Plural {
        @Test
        void simpleNounAddsS() {
            assertThat(DomainMetadata.builder("Order", "p").build().pluralName()).isEqualTo("Orders");
        }

        @Test
        void sibilantEndingAddsEs() {
            assertThat(DomainMetadata.builder("Address", "p").build().pluralName()).isEqualTo("Addresses");
            assertThat(DomainMetadata.builder("Box", "p").build().pluralName()).isEqualTo("Boxes");
            assertThat(DomainMetadata.builder("Quiz", "p").build().pluralName()).isEqualTo("Quizes");
            assertThat(DomainMetadata.builder("Branch", "p").build().pluralName()).isEqualTo("Branches");
            assertThat(DomainMetadata.builder("Brush", "p").build().pluralName()).isEqualTo("Brushes");
        }

        @Test
        void consonantYBecomesIes() {
            assertThat(DomainMetadata.builder("Category", "p").build().pluralName()).isEqualTo("Categories");
            assertThat(DomainMetadata.builder("Country", "p").build().pluralName()).isEqualTo("Countries");
        }

        @Test
        void vowelYJustAddsS() {
            assertThat(DomainMetadata.builder("Day", "p").build().pluralName()).isEqualTo("Days");
            assertThat(DomainMetadata.builder("Toy", "p").build().pluralName()).isEqualTo("Toys");
        }

        @Test
        void singleLetterYDoesNotTriggerIesRule() {
            // Branch coverage: length-1 guards the substring math from blowing up.
            assertThat(DomainMetadata.builder("Y", "p").build().pluralName()).isEqualTo("Ys");
        }
    }

    @Nested
    @DisplayName("the derived route and table take the English plural (T6)")
    class PluralNaming {

        private DomainMetadata entity(String name) {
            return DomainMetadata.builder(name, "p").build();
        }

        @Test
        void consonantYBecomesIesInTheTableAndTheRoute() {
            // The names Stellar Tactics shipped as colonys / technologys / reassemblys.
            assertThat(entity("Colony").effectiveTableName()).isEqualTo("colonies");
            assertThat(entity("Colony").effectivePath()).isEqualTo("/colonies");
            assertThat(entity("Technology").effectiveTableName()).isEqualTo("technologies");
            assertThat(entity("Reassembly").effectiveTableName()).isEqualTo("reassemblies");
            assertThat(entity("SupplyDepot").effectiveTableName()).isEqualTo("supply_depots");
            assertThat(entity("DiplomaticPolicy").effectivePath()).isEqualTo("/diplomatic-policies");
        }

        @Test
        void sibilantEndingsTakeEs() {
            assertThat(entity("Box").effectiveTableName()).isEqualTo("boxes");
            assertThat(entity("Status").effectiveTableName()).isEqualTo("statuses");
            assertThat(entity("Status").effectivePath()).isEqualTo("/statuses");
            assertThat(entity("Branch").effectiveTableName()).isEqualTo("branches");
            assertThat(entity("FleetDispatch").effectiveTableName()).isEqualTo("fleet_dispatches");
        }

        @Test
        void aVowelBeforeYTakesAPlainS() {
            assertThat(entity("Survey").effectiveTableName()).isEqualTo("surveys");
            assertThat(entity("Survey").effectivePath()).isEqualTo("/surveys");
        }

        @Test
        void wherePlainSIsRightNothingMovesFromTheOldToolingDefault() {
            // exeris-tooling has named tables snake_case(entityName) + "s". The rule only differs
            // where English does not add a bare s, so every regular name keeps its table.
            for (String name : List.of("Order", "ConstructionOrder", "Fleet", "Engagement",
                    "GalaxyPresence", "Planet", "HTTPRoute")) {
                String naive = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT) + "s";
                assertThat(entity(name).effectiveTableName()).as(name).isEqualTo(naive);
            }
        }

        @Test
        void anIrregularNounGetsTheRegularEndingAndTheOverrideFixesIt() {
            // Pinned, not endorsed: the rule knows endings, not words. The override is the remedy,
            // and it wins over the derivation on both helpers.
            DomainMetadata person = entity("Person");
            assertThat(person.pluralName()).isEqualTo("Persons");
            assertThat(person.effectiveTableName()).isEqualTo("persons");

            DomainMetadata overridden = DomainMetadata.builder("Person", "p")
                    .tableName("people").path("/people").build();
            assertThat(overridden.effectiveTableName()).isEqualTo("people");
            assertThat(overridden.effectivePath()).isEqualTo("/people");
        }

        @Test
        void anAlreadyPluralNameIsNotRecognisedAndTheOverrideFixesIt() {
            DomainMetadata settings = entity("Settings");
            assertThat(settings.pluralName()).isEqualTo("Settingses");
            assertThat(settings.effectiveTableName()).isEqualTo("settingses");
            assertThat(DomainMetadata.builder("Settings", "p").tableName("settings").build()
                    .effectiveTableName()).isEqualTo("settings");
        }

        @Test
        void theDerivationDoesNotDependOnTheDefaultLocale() {
            // Under a Turkish default, String.toLowerCase() maps I to a dotless i, which would put
            // "ıtems" into a table name on one machine and "items" on another.
            Locale saved = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                assertThat(entity("Item").effectiveTableName()).isEqualTo("items");
                assertThat(entity("InventoryItem").effectivePath()).isEqualTo("/inventory-items");
            } finally {
                Locale.setDefault(saved);
            }
        }

        @Test
        void anAbsentEntityNameDerivesNothingRatherThanThrowing() {
            DomainMetadata unnamed = DomainMetadata.builder(null, "p").build();
            assertThat(unnamed.pluralName()).isEmpty();
            assertThat(unnamed.effectiveTableName()).isEmpty();
            assertThat(unnamed.effectivePath()).isEqualTo("/");
            assertThat(DomainMetadata.builder("", "p").build().pluralName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Collection-aware predicates")
    class Predicates {
        @Test
        void hasFieldsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasFields()).isFalse();
            assertThat(base().fields(List.of(FieldMetadata.simple("name", "String"))).build().hasFields()).isTrue();
        }

        @Test
        void hasActionsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasActions()).isFalse();
            assertThat(base().actions(List.of(ActionMetadata.simple("approve"))).build().hasActions()).isTrue();
        }

        @Test
        void hasEventsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasEvents()).isFalse();
            assertThat(base().events(List.of(DomainEventMetadata.simple("OrderCreated"))).build().hasEvents()).isTrue();
        }

        @Test
        void hasRelationshipsTrueOnlyWhenNonEmpty() {
            assertThat(base().build().hasRelationships()).isFalse();
            assertThat(base().relationships(List.of(RelationshipMetadata.manyToOne("customer", "Customer")))
                    .build().hasRelationships()).isTrue();
        }

        @Test
        void hasFieldsHandlesNullList() {
            // Builder default is List.of(), but consumer code may construct via the
            // canonical constructor with null lists — the helper must tolerate it.
            DomainMetadata nullLists = new DomainMetadata("Order", "p", "", "", "", "", "v1",
                    null, true, false, false, false, false, false, false, false,
                    null, null, false, false, "PT5M", "", false, "english", null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            assertThat(nullLists.hasFields()).isFalse();
            assertThat(nullLists.hasActions()).isFalse();
            assertThat(nullLists.hasEvents()).isFalse();
            assertThat(nullLists.hasEventHandlers()).isFalse();
            assertThat(nullLists.hasRules()).isFalse();
            assertThat(nullLists.hasRelationships()).isFalse();
        }
    }

    @Nested
    @DisplayName("Composite-feature flags")
    class CompositeFlags {
        @Test
        void isEventSourcedReflectsPresence() {
            assertThat(base().build().isEventSourced()).isFalse();
            assertThat(base().eventSourced(EventSourcedMetadata.enabled("Order")).build().isEventSourced()).isTrue();
        }

        @Test
        void isSagaReflectsPresence() {
            assertThat(base().build().isSaga()).isFalse();
            assertThat(base().sagaMetadata(SagaMetadata.simple("OrderSaga")).build().isSaga()).isTrue();
        }

        @Test
        void hasGraphMetadataReflectsPresence() {
            assertThat(base().build().hasGraphMetadata()).isFalse();
            assertThat(base().graphMetadata(GraphMetadata.simple("Order")).build().hasGraphMetadata()).isTrue();
        }

        @Test
        void isInternalReadsTheInternalComponent() {
            assertThat(base().build().isInternal()).isFalse();
            // Present but marking a different facet → false. `hidden` is "absent from
            // generated docs" and `readOnly` is "mutations disabled"; neither is
            // "internal", and each has its own component.
            assertThat(base().internalApi(InternalApiMetadata.readOnly("audit")).build().isInternal()).isFalse();
            assertThat(base().internalApi(InternalApiMetadata.hidden("legacy")).build().isInternal()).isFalse();
            assertThat(base().internalApi(InternalApiMetadata.internal("svc")).build().isInternal()).isTrue();
            // The shape both extraction paths actually emit: presence-only, every
            // other component left at its default. This is the case that was false
            // through 0.10.0, which made the predicate dead on every real build.
            assertThat(base().internalApi(InternalApiMetadata.builder().internal(true).build())
                    .build().isInternal()).isTrue();
        }
    }

    @Nested
    @DisplayName("findField branching")
    class FindField {
        @Test
        void findFieldReturnsMatchingFieldByName() {
            FieldMetadata amount = FieldMetadata.simple("amount", "BigDecimal");
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"), amount)).build();

            Optional<FieldMetadata> found = d.findField("amount");
            assertThat(found).isPresent().get().isEqualTo(amount);
        }

        @Test
        void findFieldReturnsEmptyWhenAbsent() {
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"))).build();
            assertThat(d.findField("nope")).isEmpty();
        }

        @Test
        void findFieldReturnsEmptyOnNullName() {
            DomainMetadata d = base().fields(List.of(FieldMetadata.simple("orderNumber", "String"))).build();
            assertThat(d.findField(null)).isEmpty();
        }

        @Test
        void findFieldReturnsEmptyOnEmptyFieldsList() {
            // Construct directly with an empty fields list to exercise the guard.
            DomainMetadata d = new DomainMetadata("Order", "p", "", "", "", "", "v1",
                    List.of(), true, false, false, false, false, false, false, false,
                    List.of(), List.of(), false, false, "PT5M", "", false, "english", null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            assertThat(d.findField("anything")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Builder default invariants")
    class BuilderDefaults {
        // apiVersion is deprecated for removal at 1.0.0 (Stellar finding T38) and still carried
        // until then; this test exercises the carrier on purpose.
        @SuppressWarnings("removal")
        @Test
        void unsetFieldsHaveSafeDefaults() {
            DomainMetadata d = base().build();
            assertThat(d.entityName()).isEqualTo("Order");
            assertThat(d.packageName()).isEqualTo("com.acme.domain");
            assertThat(d.apiVersion()).isEqualTo("v1");
            assertThat(d.cacheTtl()).isEqualTo("PT5M");
            assertThat(d.searchConfig()).isEqualTo("english");
            assertThat(d.restApi()).isTrue();
            assertThat(d.graphqlApi()).isFalse();
            assertThat(d.tenantScoped()).isFalse();
            assertThat(d.tags()).isEmpty();
            assertThat(d.fields()).isEmpty();
            assertThat(d.actions()).isEmpty();
            assertThat(d.events()).isEmpty();
            assertThat(d.relationships()).isEmpty();
            assertThat(d.projections()).isEmpty();
            assertThat(d.uiMetadata()).isNull();
        }

        // apiVersion is deprecated for removal at 1.0.0 (Stellar finding T38) and still carried
        // until then; this test exercises the carrier on purpose.
        @SuppressWarnings("removal")
        @Test
        void builderSettersAreFluent() {
            DomainMetadata d = base()
                    .module("sales").path("/orders").aggregate("CustomerOrder").description("desc")
                    .apiVersion("v2").tags(List.of("a", "b"))
                    .restApi(false).graphqlApi(true).realTimeApi(true).internalClient(true)
                    .tenantScoped(true).softDelete(true).audited(true).versioned(true)
                    .roles(List.of("ROLE_X")).permissions(List.of("p:r")).sensitive(true)
                    .cacheable(true).cacheTtl("PT10M").cacheRegion("region")
                    .fullTextSearch(true).searchConfig("german")
                    .tableName("orders")
                    .projections(List.of(ProjectionMetadata.simple("summary", List.of("id"))))
                    .uiMetadata(UIMetadata.defaults())
                    .graphMetadata(GraphMetadata.simple("Order"))
                    .sagaMetadata(SagaMetadata.simple("S"))
                    .eventSourced(EventSourcedMetadata.disabled())
                    .internalApi(InternalApiMetadata.internal("svc"))
                    .systemFields(SystemFieldsMetadata.defaults())
                    .build();

            assertThat(d.module()).isEqualTo("sales");
            assertThat(d.aggregate()).isEqualTo("CustomerOrder");
            assertThat(d.tags()).containsExactly("a", "b");
            assertThat(d.realTimeApi()).isTrue();
            assertThat(d.internalClient()).isTrue();
            assertThat(d.versioned()).isTrue();
            assertThat(d.permissions()).containsExactly("p:r");
            assertThat(d.sensitive()).isTrue();
            assertThat(d.cacheRegion()).isEqualTo("region");
            assertThat(d.fullTextSearch()).isTrue();
            assertThat(d.searchConfig()).isEqualTo("german");
            assertThat(d.tableName()).isEqualTo("orders");
            assertThat(d.projections()).hasSize(1);
            assertThat(d.uiMetadata()).isNotNull();
            assertThat(d.graphMetadata()).isNotNull();
            assertThat(d.sagaMetadata()).isNotNull();
            assertThat(d.eventSourced()).isNotNull();
            assertThat(d.internalApi()).isNotNull();
            assertThat(d.systemFields()).isNotNull();
        }
    }

    @Nested
    @DisplayName("apiVersion is deprecated for removal at 1.0.0, and still carried until then")
    class ApiVersionDeprecation {
        @Test
        void accessorAndBuilderSetterBothCarryTheDeprecation() throws Exception {
            // The attribute, the accessor and the setter go together at 1.0.0 (MIGRATION-0.x-to-1.0.md
            // §1). A carrier that lost its marker would let a consumer keep reading a component the
            // removal list says is going, with no warning until the build that breaks.
            for (var member : List.of(DomainMetadata.class.getMethod("apiVersion"),
                    DomainMetadata.Builder.class.getMethod("apiVersion", String.class))) {
                Deprecated deprecated = member.getAnnotation(Deprecated.class);
                assertThat(deprecated).as("%s must be deprecated", member).isNotNull();
                assertThat(deprecated.forRemoval()).as("%s must be marked for removal", member).isTrue();
                assertThat(deprecated.since()).isEqualTo("0.12.0");
            }
        }

        @Test
        @SuppressWarnings("removal") // the point of the test is the deprecated accessor
        void theComponentIsStillCarriedUnchanged() {
            // Deprecation changes no behaviour inside the window: the builder default stays "v1",
            // a set value comes back, and the explicit accessor returns the component itself.
            assertThat(base().build().apiVersion()).isEqualTo("v1");
            DomainMetadata d = base().apiVersion("v2").build();
            assertThat(d.apiVersion()).isEqualTo("v2");
            assertThat(d).isEqualTo(new DomainMetadata(
                    "Order", "com.acme.domain", "", "", "", "", "v2", List.of(),
                    true, false, false, false, false, false, false, false,
                    List.of(), List.of(), false, false, "PT5M", "", false, "english", "",
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null, null, null, null, List.of(), null));
        }
    }

    @Nested
    @DisplayName("the 0.11.0 constructor shape is kept")
    class PreviousArity {
        // apiVersion is deprecated for removal at 1.0.0 (Stellar finding T38) and still carried
        // until then; this test exercises the carrier on purpose.
        @SuppressWarnings("removal")
        @Test
        void thirtyNineArgumentsBuildTheSameRecordWithNoRouteAccessOrChannel() {
            // Kept so code compiled or written against 0.11.0 still links and compiles
            // (MIGRATION-0.x-to-1.0.md §3). It must equal the builder's record with both 0.12.0
            // components absent — two shapes that disagreed would be two contracts.
            List<FieldMetadata> fields = List.of(FieldMetadata.builder("ref", "String").build());
            DomainMetadata old = new DomainMetadata(
                    "Order", "com.acme.domain", "sales", "/orders", "CustomerOrder", "desc", "v2",
                    List.of("t"), false, true, true, true,
                    true, true, true, true,
                    List.of("ROLE_X"), List.of("p:r"), true,
                    true, "PT10M", "region",
                    true, "german", "orders",
                    fields, List.of(), List.of(), List.of(), List.of(), List.of(),
                    null, null, null, null, null, SystemFieldsMetadata.defaults(),
                    List.of(), DataScope.TENANT);
            DomainMetadata built = base()
                    .module("sales").path("/orders").aggregate("CustomerOrder").description("desc")
                    .apiVersion("v2").tags(List.of("t"))
                    .restApi(false).graphqlApi(true).realTimeApi(true).internalClient(true)
                    .tenantScoped(true).softDelete(true).audited(true).versioned(true)
                    .roles(List.of("ROLE_X")).permissions(List.of("p:r")).sensitive(true)
                    .cacheable(true).cacheTtl("PT10M").cacheRegion("region")
                    .fullTextSearch(true).searchConfig("german").tableName("orders")
                    .fields(fields)
                    .systemFields(SystemFieldsMetadata.defaults())
                    .dataScope(DataScope.TENANT)
                    .build();

            assertThat(old).isEqualTo(built);
            assertThat(old.routeAccess()).isNull();
            assertThat(old.channel()).isNull();
        }
    }

    @Nested
    @DisplayName("effectiveDataScope: explicit tier wins, tenantScoped is the fallback")
    class EffectiveDataScope {

        @Test
        void explicitTierWinsOverTenantScoped() {
            // The deprecation window's whole point: once the author declares a
            // tier, the legacy boolean must not override it — not even when the
            // two disagree, as they do here.
            DomainMetadata d = base().tenantScoped(true).dataScope(DataScope.GLOBAL).build();

            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.GLOBAL);
        }

        @Test
        void universeIsCarriedVerbatim() {
            DomainMetadata d = base().dataScope(DataScope.UNIVERSE).build();

            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.UNIVERSE);
        }

        @Test
        void absentTierFallsBackToTenantScopedTrue() {
            DomainMetadata d = base().tenantScoped(true).build();

            assertThat(d.dataScope()).isNull();
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.TENANT);
        }

        @Test
        void absentTierFallsBackToTenantScopedFalse() {
            DomainMetadata d = base().tenantScoped(false).build();

            assertThat(d.dataScope()).isNull();
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.GLOBAL);
        }

        @Test
        void neverReturnsNull() {
            // An entity always has a tier — a null return would push the
            // three-way decision back onto every downstream generator.
            assertThat(base().build().effectiveDataScope()).isNotNull();
        }
    }
}
