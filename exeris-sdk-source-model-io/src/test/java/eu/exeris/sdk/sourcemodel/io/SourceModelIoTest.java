package eu.exeris.sdk.sourcemodel.io;

import eu.exeris.sdk.sourcemodel.ast.CapabilityModuleMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainEventMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.EnumMetadata;
import eu.exeris.sdk.sourcemodel.ast.ProvidesMetadata;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RequiresMetadata;
import eu.exeris.sdk.sourcemodel.ast.SagaMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for {@code exeris-sdk-source-model-io} (ADR-037): the
 * JavaParser-based reader (entity name, fields, {@code @Relationship}s, enums)
 * and the idempotent, comment/annotation-preserving writer, exercised against
 * budgetHQ-shaped entities.
 */
@DisplayName("source-model-io: read (fields/relationships/enums) + preserving write")
class SourceModelIoTest {

    /**
     * Representative entity: header comment, a non-Exeris annotation
     * ({@code @Deprecated}), a field comment, and the canonical
     * {@code @Field(required = true)} shape. budgetHQ doesn't yet author
     * {@code @ExerisDomain} sources, so this stands in for the real corpus;
     * the full budgetHQ round-trip is deferred until BHQ adopts the SDK.
     */
    private static final String ACCOUNT = """
            /*
             * Copyright 2026 budgetHQ. Licensed under Apache-2.0.
             */
            package app.budgethq.account;

            import eu.exeris.sdk.annotation.ExerisDomain;
            import eu.exeris.sdk.annotation.Field;

            @ExerisDomain
            public class Account {

                // human-readable label shown in the UI
                @Field(required = true)
                private String label;

                @Deprecated
                private double balance;

                @Field
                private String iban;
            }
            """;

    private final SourceModelReader reader = new SourceModelReader();
    private final SourceModelWriter writer = new SourceModelWriter();

    @Nested
    @DisplayName("reader: .java -> DomainMetadata")
    class Reader {

        @Test
        void extractsEntityNamePackageAndFields() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();

            assertThat(domain.entityName()).isEqualTo("Account");
            assertThat(domain.packageName()).isEqualTo("app.budgethq.account");
            assertThat(domain.fields()).extracting("name")
                    .containsExactly("label", "balance", "iban");
        }

        @Test
        void honoursFieldRequiredShape() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();

            // @Field(required = true) -> required
            assertThat(domain.findField("label")).get()
                    .extracting("required").isEqualTo(true);
            // no @Field -> not required
            assertThat(domain.findField("balance")).get()
                    .extracting("required").isEqualTo(false);
            // bare @Field marker -> not required
            assertThat(domain.findField("iban")).get()
                    .extracting("required").isEqualTo(false);
        }

        @Test
        void entityNameIsTheClassSimpleName() {
            // There is no second candidate, and that is the point: the annotation declares
            // no `name` attribute and the processor derives the name from the class element
            // alone, so no test may assert an @ExerisDomain(name=...) override — it would pin
            // a behaviour no build can produce.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Ledger {}
                    """;
            assertThat(reader.read(src).orElseThrow().entityName()).isEqualTo("Ledger");
        }

        @Test
        void readsDomainLevelAttributesPresentOnly() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "billing", tenantScoped = true,
                                  softDelete = true, restApi = false)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.module()).isEqualTo("billing");
            assertThat(d.tenantScoped()).isTrue();
            assertThat(d.softDelete()).isTrue();
            assertThat(d.restApi()).isFalse();          // explicit override
            // absent attribute keeps the builder default (not read, not forced)
            assertThat(d.audited()).isFalse();
        }

        @Test
        void readsTheTableNameOverridePresentOnly() {
            // @ExerisDomain.tableName is the author's override of the derived table. Read verbatim
            // when declared; absent keeps the builder's "", so effectiveTableName() derives the
            // snake-cased plural.
            String declared = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "empire", path = "/colonies", tableName = "colonys")
                    public class Colony {}
                    """;
            String absent = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "empire", path = "/colonies")
                    public class Colony {}
                    """;
            DomainMetadata pinned = reader.read(declared).orElseThrow();
            assertThat(pinned.tableName()).isEqualTo("colonys");
            assertThat(pinned.effectiveTableName()).isEqualTo("colonys");

            DomainMetadata derived = reader.read(absent).orElseThrow();
            assertThat(derived.tableName()).isEmpty();
            assertThat(derived.effectiveTableName()).isEqualTo("colonies");
        }

        @Test
        @SuppressWarnings("removal") // the deprecated carrier is what this test pins
        void doesNotReadTheDeprecatedApiVersion() {
            // @ExerisDomain.apiVersion is deprecated for removal at 1.0.0, and neither build path
            // carries it: the processor does not read it either, so a declared value and an
            // absent one both leave the component null, and the two paths agree (ADR-042).
            String declared = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "billing", apiVersion = "v2")
                    public class Invoice {}
                    """;
            String absent = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "billing")
                    public class Invoice {}
                    """;
            assertThat(reader.read(declared).orElseThrow().apiVersion()).isNull();
            assertThat(reader.read(absent).orElseThrow().apiVersion()).isNull();
        }

        @Test
        void absentBooleanAttributeKeepsBuilderDefault() {
            // restApi defaults TRUE in the builder (the surprising branch — all other
            // booleans default false); an absent restApi must stay true, not be forced.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "billing")
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();
            assertThat(d.restApi()).isTrue();
            assertThat(d.tenantScoped()).isFalse();
        }

        @Test
        void readsDataScopeTier() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(module = "billing",
                                  dataScope = ExerisDomain.DataScope.TENANT)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.dataScope()).isEqualTo(DataScope.TENANT);
            // The tier is declared alone, so the deprecated boolean stays at its
            // default — and effectiveDataScope() must answer from the tier, not
            // from the boolean it did not need.
            assertThat(d.tenantScoped()).isFalse();
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.TENANT);
        }

        @Test
        void readsReservedUniverseTier() {
            // The reader reports UNIVERSE rather than refusing it, and that stays true
            // even though the processor now rejects the tier outright: the two answer
            // different questions. The reader's job is to say what the source declares,
            // so a tool round-tripping a file must see the author's tier — refusing it
            // here would silently rewrite their intent. Deciding whether the tier can be
            // *emitted* belongs to the build that emits.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(dataScope = ExerisDomain.DataScope.UNIVERSE)
                    public class Almanac {}
                    """;
            assertThat(reader.read(src).orElseThrow().dataScope())
                    .isEqualTo(DataScope.UNIVERSE);
        }

        @Test
        void unspecifiedTierIsAbsentNotAFourthValue() {
            // UNSPECIFIED exists only because an annotation attribute cannot default
            // to null. Carrying it through would invent a tier the AST enum lacks.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(tenantScoped = true,
                                  dataScope = ExerisDomain.DataScope.UNSPECIFIED)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.dataScope()).isNull();
            // ...so resolution falls through to the deprecated boolean, exactly as it
            // does for a source that never mentions dataScope at all.
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.TENANT);
        }

        @Test
        void unknownTierNameReadsAsAbsent() {
            // The annotation enum and the AST enum are bridged by constant-name
            // identity (the relationshipType precedent). A name only one side knows
            // must degrade to absent, not throw — a reader is not a validator.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(dataScope = ExerisDomain.DataScope.GALAXY)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.dataScope()).isNull();
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.GLOBAL);
        }

        @Test
        void absentDataScopeLeavesTheDeprecatedBooleanInCharge() {
            // The parity case that keeps pre-0.10.0 sources reading back unchanged.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(tenantScoped = true)
                    public class Invoice {}
                    """;
            DomainMetadata d = reader.read(src).orElseThrow();

            assertThat(d.dataScope()).isNull();
            assertThat(d.effectiveDataScope()).isEqualTo(DataScope.TENANT);
        }

        @Test
        void returnsEmptyWhenNoExerisDomainType() {
            Optional<DomainMetadata> domain = reader.read(
                    "package x; public class Plain { private int n; }");
            assertThat(domain).isEmpty();
        }

        @Test
        void throwsOnInvalidSource() {
            assertThatThrownBy(() -> reader.read("%%% not valid java %%%"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("reader: @CapabilityModule -> CapabilityModuleMetadata")
    class Capabilities {

        /** IDP-shaped cap: repeated @Provides, ranged + optional @Requires, same-unit lifecycle. */
        private static final String IDENTITY_CAP = """
                package app.caps.identity;

                import eu.exeris.sdk.annotation.capability.CapabilityLifecycle;
                import eu.exeris.sdk.annotation.capability.CapabilityModule;
                import eu.exeris.sdk.annotation.capability.Provides;
                import eu.exeris.sdk.annotation.capability.Requires;

                @CapabilityModule
                @Provides(service = IdentityService.class, version = "1.2.0")
                @Provides(service = TokenService.class)
                @Requires(service = AuditService.class, versionRange = "[1.0.0,2.0.0)")
                @Requires(service = MetricsService.class, optional = true)
                public class IdentityCap {
                }

                @CapabilityLifecycle
                class IdentityCapLifecycle {
                }
                """;

        @Test
        void readsProvidesDeclarations() {
            CapabilityModuleMetadata cap = reader.readCapabilityModule(IDENTITY_CAP).orElseThrow();

            assertThat(cap.provides()).containsExactly(
                    ProvidesMetadata.of("IdentityService", "1.2.0"),
                    // no version attribute -> unversioned (null), not ""
                    ProvidesMetadata.of("TokenService"));
        }

        @Test
        void readsRequiresDeclarations() {
            CapabilityModuleMetadata cap = reader.readCapabilityModule(IDENTITY_CAP).orElseThrow();

            assertThat(cap.requires()).containsExactly(
                    RequiresMetadata.of("AuditService", "[1.0.0,2.0.0)"),
                    RequiresMetadata.optional("MetricsService"));
        }

        @Test
        void lifecycleOwnerIsFqnOfSameUnitLifecycleClass() {
            CapabilityModuleMetadata cap = reader.readCapabilityModule(IDENTITY_CAP).orElseThrow();

            assertThat(cap.lifecycleOwner()).isEqualTo("app.caps.identity.IdentityCapLifecycle");
        }

        @Test
        void lifecycleOwnerIsNullWhenAbsent() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    @CapabilityModule
                    public class BareCap {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.hasLifecycleOwner()).isFalse();
            assertThat(cap.provides()).isEmpty();
            assertThat(cap.requires()).isEmpty();
        }

        @Test
        void blankVersionAndVersionRangeMapToNull() {
            // explicit "" is the annotations' "unversioned" default — must not survive as ""
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    import eu.exeris.sdk.annotation.capability.Provides;
                    import eu.exeris.sdk.annotation.capability.Requires;
                    @CapabilityModule
                    @Provides(service = A.class, version = "")
                    @Requires(service = B.class, versionRange = "")
                    public class Cap {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.provides().getFirst().hasVersion()).isFalse();
            assertThat(cap.requires().getFirst().hasVersionRange()).isFalse();
        }

        @Test
        void readsHandWrittenListContainers() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    import eu.exeris.sdk.annotation.capability.Provides;
                    import eu.exeris.sdk.annotation.capability.Requires;
                    @CapabilityModule
                    @Provides.List({
                            @Provides(service = A.class, version = "1.0.0"),
                            @Provides(service = B.class)
                    })
                    @Requires.List(@Requires(service = C.class, optional = true))
                    public class Cap {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.provides()).containsExactly(
                    ProvidesMetadata.of("A", "1.0.0"), ProvidesMetadata.of("B"));
            assertThat(cap.requires()).containsExactly(RequiresMetadata.optional("C"));
        }

        @Test
        void mixesDirectDeclarationsAndListContainerInSourceOrder() {
            // a cap may carry both forms at once; the reader expands the container
            // in-place, so declaration order across the two forms is preserved
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    import eu.exeris.sdk.annotation.capability.Provides;
                    @CapabilityModule
                    @Provides(service = A.class)
                    @Provides.List({
                            @Provides(service = B.class),
                            @Provides(service = C.class)
                    })
                    @Provides(service = D.class)
                    public class Cap {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.provides()).extracting(ProvidesMetadata::service)
                    .containsExactly("A", "B", "C", "D");
        }

        @Test
        void lifecycleOwnerIgnoresAnnotatedInterface() {
            // @CapabilityLifecycle targets TYPE, so an interface can carry it, but a
            // lifecycle owner is a class — an annotated interface is not the owner
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityLifecycle;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    @CapabilityModule
                    public class Cap {}
                    @CapabilityLifecycle
                    interface NotAnOwner {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.hasLifecycleOwner()).isFalse();
        }

        @Test
        void serviceIsKeptAsWrittenIncludingQualifiedForm() {
            // ADR-038: written form persisted, FQN normalization is tooling's job
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.capability.CapabilityModule;
                    import eu.exeris.sdk.annotation.capability.Provides;
                    @CapabilityModule
                    @Provides(service = eu.exeris.idp.IdentityService.class)
                    public class Cap {}
                    """;
            CapabilityModuleMetadata cap = reader.readCapabilityModule(src).orElseThrow();

            assertThat(cap.provides().getFirst().service())
                    .isEqualTo("eu.exeris.idp.IdentityService");
        }

        @Test
        void returnsEmptyWhenNoCapabilityModuleType() {
            assertThat(reader.readCapabilityModule(
                    "package x; public class Plain {}")).isEmpty();
            // an @ExerisDomain entity is not a cap
            assertThat(reader.readCapabilityModule(ACCOUNT)).isEmpty();
        }

        @Test
        void throwsOnInvalidSource() {
            assertThatThrownBy(() -> reader.readCapabilityModule("%%% not valid java %%%"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unmodeledFacetsGuardArmsForCapabilitySources() {
            // capability sources are in the guard's scope now; the divergence set is
            // empty, so a fully-read cap reports no unmodeled facets
            assertThat(reader.unmodeledFacets(IDENTITY_CAP)).isEmpty();
        }
    }

    @Nested
    @DisplayName("writer: idempotent, formatting/comment-preserving edits")
    class Writer {

        @Test
        void addsFieldPreservingCommentsAndNonExerisAnnotations() {
            String result = writer.addField(ACCOUNT, "String", "currency");

            // new field present
            assertThat(result).contains("private String currency;");
            // user comment preserved verbatim
            assertThat(result).contains("// human-readable label shown in the UI");
            // non-Exeris annotation preserved
            assertThat(result).contains("@Deprecated");
            // Exeris annotation preserved verbatim (not reformatted)
            assertThat(result).contains("@Field(required = true)");
            // header comment preserved
            assertThat(result).contains("Copyright 2026 budgetHQ");
        }

        @Test
        void isIdempotentWhenFieldAlreadyExists() {
            // 'label' already exists -> no-op, source returned unchanged
            assertThat(writer.addField(ACCOUNT, "String", "label")).isEqualTo(ACCOUNT);
        }

        @Test
        void throwsWhenNoExerisDomainType() {
            assertThatThrownBy(() -> writer.addField(
                    "package x; public class Plain {}", "String", "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("@ExerisDomain");
        }

        @Test
        void throwsOnInvalidSource() {
            assertThatThrownBy(() -> writer.addField("%%% nope %%%", "String", "x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void renameFieldPreservesCommentsAndAnnotationsAndIsIdempotent() {
            String renamed = writer.renameField(ACCOUNT, "label", "title");

            assertThat(renamed).contains("private String title;");
            assertThat(renamed).doesNotContain("String label;");
            assertThat(renamed).contains("// human-readable label shown in the UI");
            assertThat(renamed).contains("@Field(required = true)");

            // re-applying with the old name is a no-op (label no longer exists)
            assertThat(writer.renameField(renamed, "label", "title")).isEqualTo(renamed);
        }

        @Test
        void renameIsNoOpWhenSourceAbsentOrTargetExists() {
            assertThat(writer.renameField(ACCOUNT, "missing", "x")).isEqualTo(ACCOUNT);
            // 'iban' already exists -> renaming onto it would duplicate, so no-op
            assertThat(writer.renameField(ACCOUNT, "label", "iban")).isEqualTo(ACCOUNT);
            // from == to -> 'to' is by definition present, so also a no-op
            assertThat(writer.renameField(ACCOUNT, "label", "label")).isEqualTo(ACCOUNT);
        }

        @Test
        void changeFieldTypePreservesAndIsNoOpWhenUnchanged() {
            String changed = writer.changeFieldType(ACCOUNT, "balance", "java.math.BigDecimal");

            assertThat(changed).contains("java.math.BigDecimal balance;");
            assertThat(changed).contains("@Deprecated"); // sibling annotation preserved

            assertThat(writer.changeFieldType(ACCOUNT, "balance", "double")).isEqualTo(ACCOUNT);
            assertThat(writer.changeFieldType(ACCOUNT, "missing", "int")).isEqualTo(ACCOUNT);
        }

        @Test
        void removeFieldDropsDeclarationAndPreservesOthers() {
            String removed = writer.removeField(ACCOUNT, "balance");

            assertThat(removed).doesNotContain("balance");
            assertThat(removed).doesNotContain("@Deprecated"); // whole declaration removed
            assertThat(removed).contains("private String label;");
            assertThat(removed).contains("// human-readable label shown in the UI");
            assertThat(removed).contains("private String iban;");

            assertThat(writer.removeField(removed, "balance")).isEqualTo(removed); // idempotent
        }

        @Test
        void removeFieldFromMultiVariableDeclarationDropsOnlyThatVariable() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Point { private int a, b; }
                    """;
            String removed = writer.removeField(src, "a");
            assertThat(removed).doesNotContain("int a");   // 'a' dropped from the declaration
            assertThat(removed).contains("int b");         // sibling kept, separator not mangled
        }

        @Test
        void addRelationshipAddsAnnotatedFieldAndIsIdempotent() {
            String added = writer.addRelationship(ACCOUNT, "customer", "Customer", "MANY_TO_ONE");

            assertThat(added).contains("Customer customer");
            assertThat(added).contains("@Relationship");
            assertThat(added).contains("RelationshipType.MANY_TO_ONE");
            assertThat(added).contains("// human-readable label shown in the UI"); // preserved

            // re-applying with the same field name is a no-op
            assertThat(writer.addRelationship(added, "customer", "Customer", "MANY_TO_ONE"))
                    .isEqualTo(added);
        }

        @Test
        void addRelationshipNoOpWhenFieldExists() {
            assertThat(writer.addRelationship(ACCOUNT, "label", "Customer", "MANY_TO_ONE"))
                    .isEqualTo(ACCOUNT);
        }

        @Test
        void addRelationshipThrowsIllegalArgumentOnMalformedType() {
            // a type string that yields an unparseable annotation -> IllegalArgumentException
            assertThatThrownBy(() -> writer.addRelationship(ACCOUNT, "x", "Customer", "BAD)SYNTAX"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void removeRelationshipRemovesOnlyRelationshipFields() {
            String withRel = writer.addRelationship(ACCOUNT, "customer", "Customer", "ONE_TO_ONE");

            String removed = writer.removeRelationship(withRel, "customer");
            assertThat(removed).doesNotContain("customer");
            assertThat(removed).doesNotContain("@Relationship");
            assertThat(removed).contains("private String label;"); // other fields preserved
        }

        @Test
        void removeRelationshipIsNoOpForPlainFieldOrAbsent() {
            // 'balance' is a plain @Deprecated field, NOT a @Relationship -> must not be deleted
            assertThat(writer.removeRelationship(ACCOUNT, "balance")).isEqualTo(ACCOUNT);
            assertThat(writer.removeRelationship(ACCOUNT, "missing")).isEqualTo(ACCOUNT);
        }

        @Test
        void addActionAddsAnnotatedMethodAndIsIdempotent() {
            String added = writer.addAction(ACCOUNT, "approve");

            assertThat(added).contains("@Action");
            assertThat(added).contains("void approve()");
            assertThat(added).contains("name = \"approve\"");
            assertThat(added).contains("path = \"/approve\"");
            assertThat(added).contains("// human-readable label shown in the UI"); // preserved

            assertThat(writer.addAction(added, "approve")).isEqualTo(added); // idempotent
        }

        @Test
        void addActionNoOpWhenActionExists() {
            String once = writer.addAction(ACCOUNT, "approve");
            assertThat(writer.addAction(once, "approve")).isEqualTo(once);
        }

        @Test
        void removeActionRemovesMethodAndPreservesFields() {
            String withAction = writer.addAction(ACCOUNT, "approve");

            String removed = writer.removeAction(withAction, "approve");
            assertThat(removed).doesNotContain("@Action");
            assertThat(removed).doesNotContain("approve");
            assertThat(removed).contains("private String label;"); // fields preserved
        }

        @Test
        void removeActionMatchesByAnnotationNameAndSparesPlainMethods() {
            // action name "approve" lives on @Action.name, not the method name 'doApprove';
            // 'helper' has no @Action and must survive
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Action;
                    @ExerisDomain
                    public class Invoice {
                        @Action(name = "approve", path = "/a")
                        public void doApprove() {}
                        public void helper() {}
                    }
                    """;
            String removed = writer.removeAction(src, "approve");
            assertThat(removed).doesNotContain("doApprove");
            assertThat(removed).doesNotContain("@Action");
            assertThat(removed).contains("helper()"); // non-action method preserved
        }

        @Test
        void removeActionNoOpWhenAbsent() {
            assertThat(writer.removeAction(ACCOUNT, "missing")).isEqualTo(ACCOUNT);
        }

        @Test
        void addActionThrowsIllegalArgumentOnMalformedName() {
            // a name that yields an unparseable @Action annotation -> IllegalArgumentException
            assertThatThrownBy(() -> writer.addAction(ACCOUNT, "bad\"syntax"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("reader: @Relationship extraction")
    class Relationships {

        // NB: @Relationship declares required attributes (targetEntity, displayField)
        // with no defaults; they're omitted here on purpose — JavaParser parses
        // source syntactically and does not validate annotation completeness, and
        // the reader derives targetEntity from the field type, not the attribute.
        private static final String ORDER = """
                package app.budgethq.order;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Relationship;
                import eu.exeris.sdk.annotation.Relationship.RelationshipType;
                import java.util.List;

                @ExerisDomain
                public class Order {
                    @Relationship(relationshipType = RelationshipType.MANY_TO_ONE)
                    private Customer customer;

                    @Relationship(relationshipType = RelationshipType.ONE_TO_MANY, mappedBy = "order")
                    private List<OrderLine> lines;
                }
                """;

        @Test
        void readsTypeTargetAndMappedBy() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.relationships()).hasSize(2);

            assertThat(domain.relationships()).anySatisfy(r -> {
                assertThat(r.fieldName()).isEqualTo("customer");
                assertThat(r.targetEntity()).isEqualTo("Customer");
                assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_ONE);
            });
        }

        @Test
        void unwrapsCollectionElementTypeForTargetEntity() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.relationships()).anySatisfy(r -> {
                assertThat(r.fieldName()).isEqualTo("lines");
                assertThat(r.targetEntity()).isEqualTo("OrderLine"); // List<OrderLine> unwrapped
                assertThat(r.type()).isEqualTo(RelationshipMetadata.RelationType.ONE_TO_MANY);
                assertThat(r.mappedBy()).isEqualTo("order");
            });
        }

        @Test
        void relationshipFieldsAreNotAlsoPlainFields() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();
            assertThat(domain.fields()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: enum extraction")
    class Enums {

        @Test
        void readsEnumNamePackageAndValues() {
            String src = """
                    package app.budgethq.account;
                    public enum AccountType { CHECKING, SAVINGS, CREDIT }
                    """;
            List<EnumMetadata> enums = reader.readEnums(src);

            assertThat(enums).hasSize(1);
            EnumMetadata type = enums.get(0);
            assertThat(type.name()).isEqualTo("AccountType");
            assertThat(type.packageName()).isEqualTo("app.budgethq.account");
            assertThat(type.qualifiedName()).isEqualTo("app.budgethq.account.AccountType");
            assertThat(type.values()).extracting("name")
                    .containsExactly("CHECKING", "SAVINGS", "CREDIT");
        }

        @Test
        void qualifiedNameIsBareNameWhenNoPackage() {
            List<EnumMetadata> enums = reader.readEnums("public enum Color { RED, GREEN }");
            assertThat(enums).singleElement()
                    .satisfies(e -> assertThat(e.qualifiedName()).isEqualTo("Color"));
        }

        @Test
        void returnsEmptyWhenNoEnums() {
            assertThat(reader.readEnums("package x; public class NoEnumsHere {}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: @Action + @ActionParam extraction")
    class Actions {

        private static final String INVOICE = """
                package app.budgethq.invoice;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Action;
                import eu.exeris.sdk.annotation.ActionParam;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain
                public class Invoice {
                    @Field private String number;

                    @Action(name = "approve", label = "Approve", httpMethod = "POST", path = "/approve",
                            description = "Approve the invoice")
                    public void approve(
                            @ActionParam(label = "Reason", required = true, description = "Why it is approved") String reason,
                            @ActionParam(label = "Notify", required = false, defaultValue = "true") boolean notify,
                            @ActionParam(label = "Memo") String memo,
                            @ActionParam(name = "ccEmail", label = "CC") String cc) { }

                    @Action(name = "archive", label = "Archive", path = "/archive", async = true)
                    public void archive() { }

                    // no name attribute -> falls back to the method name
                    @Action(label = "Refresh", path = "/refresh")
                    public void refresh() { }
                }
                """;

        @Test
        void readsActionNameLabelHttpMethodAndAsync() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();

            assertThat(domain.actions()).extracting("name")
                    .containsExactlyInAnyOrder("approve", "archive", "refresh");

            assertThat(domain.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("approve");
                assertThat(a.displayName()).isEqualTo("Approve");
                assertThat(a.httpMethod()).isEqualTo("POST");
                assertThat(a.description()).isEqualTo("Approve the invoice");
                assertThat(a.async()).isFalse();
            });
            assertThat(domain.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("archive");
                assertThat(a.async()).isTrue();
                assertThat(a.params()).isEmpty();
            });
        }

        @Test
        void actionNameFallsBackToMethodNameWhenAttributeAbsent() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();
            assertThat(domain.actions()).anySatisfy(a -> assertThat(a.name()).isEqualTo("refresh"));
        }

        @Test
        void readsMethodNameDistinctFromActionIdentity() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();
            // when name == method, methodName mirrors it
            assertThat(domain.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("approve");
                assertThat(a.methodName()).isEqualTo("approve");
            });

            // when @Action(name=…) differs from the annotated method, methodName tracks the method
            String src = """
                    package app.fleet;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Action;
                    @ExerisDomain
                    public class Fleet {
                        @Action(name = "commandFormation", path = "/command-formation")
                        public void setFormation() { }
                    }
                    """;
            DomainMetadata fleet = reader.read(src).orElseThrow();
            assertThat(fleet.actions()).singleElement().satisfies(a -> {
                assertThat(a.name()).isEqualTo("commandFormation");
                assertThat(a.methodName()).isEqualTo("setFormation");
                assertThat(a.effectiveMethodName()).isEqualTo("setFormation");
            });
        }

        @Test
        void readsActionParamsWithRequiredDefaultAndType() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();

            var approve = domain.actions().stream()
                    .filter(a -> a.name().equals("approve")).findFirst().orElseThrow();
            assertThat(approve.params()).extracting("name")
                    .containsExactly("reason", "notify", "memo", "ccEmail");

            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("reason");
                assertThat(p.type()).isEqualTo("String");
                assertThat(p.required()).isTrue();
                assertThat(p.description()).isEqualTo("Why it is approved");
            });
            // @ActionParam(name = "ccEmail") overrides the parameter name "cc"
            assertThat(approve.params()).anySatisfy(p -> assertThat(p.name()).isEqualTo("ccEmail"));
            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("notify");
                assertThat(p.type()).isEqualTo("boolean");
                assertThat(p.required()).isFalse();
                assertThat(p.defaultValue()).isEqualTo("true");
            });
            // 'memo' omits required -> defaults to true (mirrors @ActionParam.required)
            assertThat(approve.params()).anySatisfy(p -> {
                assertThat(p.name()).isEqualTo("memo");
                assertThat(p.required()).isTrue();
            });
        }

        @Test
        void actionMethodsAreNotReadAsFields() {
            DomainMetadata domain = reader.read(INVOICE).orElseThrow();
            assertThat(domain.fields()).extracting("name").containsExactly("number");
        }

        @Test
        void readsPerActionStreamingDriverInProcessorParity() {
            String src = """
                    package app.reporting;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Action;
                    @ExerisDomain
                    public class Report {
                        @Action(name = "generate", path = "/generate",
                                streaming = true, streamEventType = "ReportProgress")
                        public void generate() { }

                        // blank streamEventType normalizes to null ("no event type")
                        @Action(name = "tail", path = "/tail", streaming = true, streamEventType = "")
                        public void tail() { }

                        // realTimeUpdates is deliberately NOT read (processor parity:
                        // no generator consumer yet, so extracting it would only
                        // create an inert AST attribute)
                        @Action(name = "watch", path = "/watch", realTimeUpdates = true)
                        public void watch() { }
                    }
                    """;
            DomainMetadata report = reader.read(src).orElseThrow();

            assertThat(report.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("generate");
                assertThat(a.streaming()).isTrue();
                assertThat(a.streamEventType()).isEqualTo("ReportProgress");
                assertThat(a.hasStreamEventType()).isTrue();
            });
            assertThat(report.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("tail");
                assertThat(a.streaming()).isTrue();
                assertThat(a.streamEventType()).isNull();
            });
            assertThat(report.actions()).anySatisfy(a -> {
                assertThat(a.name()).isEqualTo("watch");
                assertThat(a.streaming()).isFalse();
                assertThat(a.realTimeUpdates()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("reader: class-level @UI extraction")
    class Ui {

        private static final String PRODUCT = """
                package app.budgethq.product;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.UI;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain
                @UI(listView = true, exportable = true, editForm = false)
                public class Product {
                    @Field private String sku;
                }
                """;

        @Test
        void readsViewFlagsWithProcessorDefaultTrueConvention() {
            var ui = reader.read(PRODUCT).orElseThrow().uiMetadata();

            assertThat(ui).isNotNull();
            assertThat(ui.listView()).isTrue();      // explicit
            assertThat(ui.exportable()).isTrue();     // explicit (overrides default false)
            assertThat(ui.editForm()).isFalse();      // explicit (overrides default true)
            // absent attributes default to true (matches the processor)
            assertThat(ui.detailView()).isTrue();
            assertThat(ui.createForm()).isTrue();
            assertThat(ui.searchable()).isTrue();
            assertThat(ui.filterable()).isTrue();
        }

        @Test
        void uiMetadataIsNullWhenNoUiAnnotation() {
            String noUi = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Bare {}
                    """;
            assertThat(reader.read(noUi).orElseThrow().uiMetadata()).isNull();
        }

        @Test
        void bareMarkerUiUsesAllDefaults() {
            // @UI with no attributes -> every view flag defaults true, exportable false
            String marker = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain
                    @UI
                    public class Bare {}
                    """;
            var ui = reader.read(marker).orElseThrow().uiMetadata();
            assertThat(ui).isNotNull();
            assertThat(ui.listView()).isTrue();
            assertThat(ui.detailView()).isTrue();
            assertThat(ui.createForm()).isTrue();
            assertThat(ui.editForm()).isTrue();
            assertThat(ui.searchable()).isTrue();
            assertThat(ui.filterable()).isTrue();
            assertThat(ui.exportable()).isFalse();
        }

        @Test
        void nestedExerisDomainUiAttributeIsNotRead() {
            // Parity: the processor (findAnnotation over directly-present annotations)
            // reads ONLY a standalone class-level @UI — never @ExerisDomain(ui=@UI(...)).
            // The reader matches: the nested form yields null UI. (@ExerisDomain.ui()
            // is effectively unconsumed SDK-wide; changing that must move processor +
            // reader together and is out of scope here.)
            String nested = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.UI;
                    @ExerisDomain(ui = @UI(listView = true, exportable = true))
                    public class Order {}
                    """;
            assertThat(reader.read(nested).orElseThrow().uiMetadata()).isNull();
        }
    }

    @Nested
    @DisplayName("reader: round-trip completeness guard (unmodeledFacets)")
    class Completeness {

        @Test
        void emptyForFullyModeledEntity() {
            // ACCOUNT uses only @ExerisDomain, @Field (+ a non-facet @Deprecated)
            assertThat(reader.unmodeledFacets(ACCOUNT)).isEmpty();
        }

        @Test
        void fullyAnnotatedEntityHasNoDivergences() {
            // every processor facet is read: the guard is empty even
            // for an entity touching all of them. @Projection/@NavMenu/@PrimaryKey are
            // processor-gaps (never divergences); @Validation is read into FieldMetadata.
            String src = """
                    package app.budgethq.order;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    import eu.exeris.sdk.annotation.Graph;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.Projection;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    import eu.exeris.sdk.annotation.NavMenu;
                    import eu.exeris.sdk.annotation.InternalApi;
                    import eu.exeris.sdk.annotation.Validation;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.system.PrimaryKey;

                    @ExerisDomain(tenantScoped = true, roles = {"ADMIN"})
                    @EventSourced @Graph @Saga
                    @InternalApi(rateLimit = 100)
                    @DomainEvent(trigger = Trigger.CREATE, topic = "orders.created")
                    @Projection @NavMenu
                    public class Order {
                        @PrimaryKey private Long id;
                        @Field @Validation(min = 1, max = 9999) private String contact;
                        @Field private String code;
                    }
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void modeledDomainAttributesAreNotFlagged() {
            // tenantScoped/softDelete are read -> guard stays empty
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain(tenantScoped = true, softDelete = true)
                    public class Bare {}
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void onlyNameAttributeIsNotFlagged() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    @ExerisDomain
                    public class Bare {}
                    """;
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }

        @Test
        void emptyWhenNoExerisDomainType() {
            assertThat(reader.unmodeledFacets("package x; public class Plain {}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("reader: @DomainEvent extraction (events)")
    class Events {

        private static final String ORDER = """
                package app.budgethq.order;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.DomainEvent;
                import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain
                @DomainEvent(name = "OrderPlaced", trigger = Trigger.CREATE,
                        topic = "orders.created", description = "Customer placed an order")
                @DomainEvent(trigger = Trigger.UPDATE, topic = "orders.updated")
                public class Order {
                    @Field private String code;
                }
                """;

        @Test
        void readsRepeatedDomainEventsWithExplicitName() {
            DomainMetadata domain = reader.read(ORDER).orElseThrow();

            assertThat(domain.events()).extracting("name")
                    .containsExactlyInAnyOrder("OrderPlaced", "OrderUpdatedEvent");
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderPlaced");
                assertThat(e.topic()).isEqualTo("orders.created");
                assertThat(e.description()).isEqualTo("Customer placed an order");
                // aggregateType mirrors the processor: the class simple name
                assertThat(e.aggregateType()).isEqualTo("Order");
            });
        }

        @Test
        void derivesEventNameFromTriggerWhenNameAbsent() {
            // @DomainEvent(trigger = UPDATE) with no name -> "Order" + "UpdatedEvent"
            DomainMetadata domain = reader.read(ORDER).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderUpdatedEvent");
                assertThat(e.topic()).isEqualTo("orders.updated");
                assertThat(e.description()).isNull();
            });
        }

        @Test
        void readsTheTriggerEvenWhenTheEventNameIsExplicit() {
            // With an explicit name the trigger contributes no suffix to the event name, so
            // the trigger component is the only place it survives.
            DomainMetadata domain = reader.read(ORDER).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderPlaced");
                assertThat(e.trigger()).isEqualTo(DomainEventMetadata.Trigger.CREATE);
                assertThat(e.hasTrigger()).isTrue();
            });
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderUpdatedEvent");
                assertThat(e.trigger()).isEqualTo(DomainEventMetadata.Trigger.UPDATE);
            });
        }

        @Test
        void readsActionAndFieldNamesForTheTriggersThatRequireThem() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    @DomainEvent(name = "OrderCancelled", trigger = Trigger.ACTION, action = "cancel")
                    @DomainEvent(name = "StatusChanged", trigger = Trigger.FIELD_CHANGED, field = "status")
                    public class Order {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();

            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderCancelled");
                assertThat(e.trigger()).isEqualTo(DomainEventMetadata.Trigger.ACTION);
                assertThat(e.actionName()).isEqualTo("cancel");
                assertThat(e.fieldName()).isNull();
            });
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("StatusChanged");
                assertThat(e.trigger()).isEqualTo(DomainEventMetadata.Trigger.FIELD_CHANGED);
                assertThat(e.fieldName()).isEqualTo("status");
                assertThat(e.actionName()).isNull();
            });
        }

        @Test
        void anUnknownTriggerConstantLeavesTheComponentUnsetRatherThanFailingTheParse() {
            // The two enums are independent types (ADR-059 precedent), so a constant added
            // on the annotation side alone must degrade to "not extracted", not to a broken
            // read. The name derivation falls through to the generic "Event" suffix.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    @DomainEvent(trigger = Trigger.BULK_CREATE)
                    public class Thing {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement().satisfies(e -> {
                assertThat(e.name()).isEqualTo("ThingEvent");
                assertThat(e.trigger()).isNull();
                assertThat(e.hasTrigger()).isFalse();
            });
        }

        @Test
        void defaultsTriggerToCreateWhenAbsent() {
            // no trigger attribute at all -> processor defaults to CREATE -> "CreatedEvent"
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    @ExerisDomain
                    @DomainEvent(topic = "things.created")
                    public class Thing {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement().satisfies(e -> {
                assertThat(e.name()).isEqualTo("ThingCreatedEvent");
                // ...but the COMPONENT stays unset. The name derivation defaults an absent
                // trigger to CREATE because it only needs a suffix; the component must not,
                // because "not declared" and "fires on create" are different claims to a
                // generator deciding where to place a publish call.
                assertThat(e.trigger()).isNull();
            });
        }

        @Test
        void unmappedTriggerFallsBackToGenericSuffix() {
            // SNAPSHOT has no explicit suffix mapping -> generic "Event"
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    @DomainEvent(trigger = Trigger.SNAPSHOT, topic = "things.snap")
                    public class Thing {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("ThingEvent"));
        }

        @Test
        void unwrapsHandWrittenDomainEventsContainer() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.DomainEvents;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    @DomainEvents({
                        @DomainEvent(name = "Created", trigger = Trigger.CREATE, topic = "o.created"),
                        @DomainEvent(name = "Deleted", trigger = Trigger.DELETE, topic = "o.deleted")
                    })
                    public class Order {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).extracting("name")
                    .containsExactlyInAnyOrder("Created", "Deleted");
        }

        @Test
        void unwrapsSingleElementContainerWithoutArrayBraces() {
            // legal Java: a single-element annotation array may omit the { } braces
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.DomainEvents;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    @DomainEvents(@DomainEvent(name = "Created", trigger = Trigger.CREATE, topic = "o.created"))
                    public class Order {}
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement()
                    .satisfies(e -> assertThat(e.name()).isEqualTo("Created"));
        }

        @Test
        void readsNestedClassEventLegacyForm() {
            // processor's legacy path: a nested @DomainEvent class -> name = class name, topic only
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.DomainEvent;
                    import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                    @ExerisDomain
                    public class Order {
                        @DomainEvent(trigger = Trigger.CREATE, topic = "orders.created")
                        public static class OrderCreated {}
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            assertThat(domain.events()).singleElement().satisfies(e -> {
                assertThat(e.name()).isEqualTo("OrderCreated");
                assertThat(e.topic()).isEqualTo("orders.created");
                assertThat(e.description()).isNull();
                assertThat(e.aggregateType()).isNull(); // withTopic leaves it null, mirroring the processor
            });
        }

        @Test
        void emptyEventsWhenNoDomainEvent() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();
            assertThat(domain.events()).isEmpty();
        }

        // ── EV1: resolved payload-field subset (ADR-042 lock-step with the processor) ──

        private static final String ORDER_WITH_FIELDS = """
                package app.budgethq.order;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.DomainEvent;
                import eu.exeris.sdk.annotation.DomainEvent.Trigger;
                import eu.exeris.sdk.annotation.Field;

                @ExerisDomain
                @DomainEvent(name = "OrderCreated", trigger = Trigger.CREATE, topic = "orders.created")
                @DomainEvent(name = "OrderPicked", trigger = Trigger.ACTION, topic = "orders.picked",
                        includeFields = {"amount", "orderNumber"}, sensitiveFields = "customerEmail")
                @DomainEvent(name = "OrderRedacted", trigger = Trigger.UPDATE, topic = "orders.redacted",
                        excludeFields = {"customerEmail"})
                public class Order {
                    @Field private String orderNumber;
                    @Field private java.math.BigDecimal amount;
                    @Field private String customerEmail;
                }
                """;

        @Test
        void defaultPayloadIsAllFieldNamesInDeclarationOrder() {
            // No includeFields/excludeFields -> ALL @Field names, declaration order.
            DomainMetadata domain = reader.read(ORDER_WITH_FIELDS).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderCreated");
                assertThat(e.payloadFields())
                        .containsExactly("orderNumber", "amount", "customerEmail");
                assertThat(e.sensitiveFields()).isEmpty();
            });
        }

        @Test
        void includeFieldsRestrictsPayloadVerbatim() {
            // includeFields non-empty -> exactly those names (in written order),
            // and sensitiveFields is read verbatim (single brace-elided element).
            DomainMetadata domain = reader.read(ORDER_WITH_FIELDS).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderPicked");
                assertThat(e.payloadFields()).containsExactly("amount", "orderNumber");
                assertThat(e.sensitiveFields()).containsExactly("customerEmail");
            });
        }

        @Test
        void excludeFieldsRemovedFromDefaultPayload() {
            // No includeFields, excludeFields removes a name -> remaining @Field
            // names in declaration order.
            DomainMetadata domain = reader.read(ORDER_WITH_FIELDS).orElseThrow();
            assertThat(domain.events()).anySatisfy(e -> {
                assertThat(e.name()).isEqualTo("OrderRedacted");
                assertThat(e.payloadFields()).containsExactly("orderNumber", "amount");
            });
        }

        @Test
        void payloadResolutionMatchesProcessorBaselineAndUnmodeledFacetsEmpty() {
            // The reader's resolved payload is the processor baseline (computed by
            // the identical includeFields-else-all minus-excludeFields rule), and
            // adding payload reading introduces no annotation-level divergence.
            assertThat(reader.unmodeledFacets(ORDER_WITH_FIELDS)).isEmpty();

            DomainMetadata domain = reader.read(ORDER_WITH_FIELDS).orElseThrow();
            // Baseline: same resolution rule expressed independently here.
            List<String> all = List.of("orderNumber", "amount", "customerEmail");
            assertThat(eventNamed(domain, "OrderCreated").payloadFields()).isEqualTo(all);
            assertThat(eventNamed(domain, "OrderPicked").payloadFields())
                    .isEqualTo(List.of("amount", "orderNumber"));
            assertThat(eventNamed(domain, "OrderRedacted").payloadFields())
                    .isEqualTo(all.stream().filter(f -> !f.equals("customerEmail")).toList());
        }

        private DomainEventMetadata eventNamed(DomainMetadata domain, String name) {
            return domain.events().stream()
                    .filter(e -> name.equals(e.name()))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Nested
    @DisplayName("reader: @Graph / @EventSourced / @Saga / @InternalApi extraction")
    class Facets {

        @Test
        void readsGraphLabelFromNodeClassElseClassName() {
            String withNodeClass = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    @ExerisDomain
                    @Graph(nodeClass = "PersonNode")
                    public class Person {}
                    """;
            assertThat(reader.read(withNodeClass).orElseThrow().graphMetadata()).satisfies(g -> {
                assertThat(g.label()).isEqualTo("PersonNode");
                // mirrors the processor: properties null, edges/queries empty
                assertThat(g.properties()).isNull();
                assertThat(g.edges()).isEmpty();
                assertThat(g.queries()).isEmpty();
            });

            String bareGraph = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Graph;
                    @ExerisDomain
                    @Graph
                    public class Person {}
                    """;
            // no nodeClass -> label falls back to the class simple name
            assertThat(reader.read(bareGraph).orElseThrow().graphMetadata().label()).isEqualTo("Person");
        }

        @Test
        void absentGraphLeavesMetadataNull() {
            assertThat(reader.read(ACCOUNT).orElseThrow().graphMetadata()).isNull();
        }

        @Test
        void translatesEventSourcedStreamPrefixAndSnapshotThreshold() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    @ExerisDomain
                    @EventSourced(streamPrefix = "orders", snapshotThreshold = 25)
                    public class Order {}
                    """;
            assertThat(reader.read(src).orElseThrow().eventSourced()).satisfies(es -> {
                // streamPrefix -> aggregateType, snapshotThreshold -> snapshotEvery
                assertThat(es.aggregateType()).isEqualTo("orders");
                assertThat(es.snapshotEvery()).isEqualTo(25);
                assertThat(es.enabled()).isTrue(); // builder default preserved
            });
        }

        @Test
        void eventSourcedDefaultsAggregateToClassNameAndSnapshotTo50() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.EventSourced;
                    @ExerisDomain
                    @EventSourced
                    public class Order {}
                    """;
            assertThat(reader.read(src).orElseThrow().eventSourced()).satisfies(es -> {
                // empty streamPrefix -> class simple name
                assertThat(es.aggregateType()).isEqualTo("Order");
                assertThat(es.snapshotEvery()).isEqualTo(50);
            });
        }

        @Test
        void readsSagaWithStepsSortedByOrder() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.SagaStep;
                    @ExerisDomain
                    @Saga(name = "OrderFulfillment", description = "End-to-end fulfillment",
                            timeout = "PT10M", maxRetries = 7)
                    public class Fulfillment {
                        @SagaStep(order = 2, name = "ship", service = "shipping",
                                command = "ship", compensation = "unship", timeout = "PT1M", parallel = true)
                        public void ship() {}

                        @SagaStep(order = 1, service = "payment", command = "charge")
                        public void pay() {}
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata()).satisfies(s -> {
                assertThat(s.name()).isEqualTo("OrderFulfillment");
                assertThat(s.description()).isEqualTo("End-to-end fulfillment");
                assertThat(s.timeout()).isEqualTo("PT10M");
                assertThat(s.maxRetries()).isEqualTo(7);
                // sorted by order: pay(1) before ship(2)
                assertThat(s.steps()).extracting("name").containsExactly("pay", "ship");
                assertThat(s.steps()).anySatisfy(step -> {
                    assertThat(step.name()).isEqualTo("ship");
                    assertThat(step.order()).isEqualTo(2);
                    assertThat(step.service()).isEqualTo("shipping");
                    assertThat(step.command()).isEqualTo("ship");
                    assertThat(step.compensation()).isEqualTo("unship");
                    assertThat(step.timeout()).isEqualTo("PT1M");
                    assertThat(step.parallel()).isTrue();
                });
            });
        }

        @Test
        void sagaNameAndStepNameFallBackWhenAbsent() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import eu.exeris.sdk.annotation.SagaStep;
                    @ExerisDomain
                    @Saga
                    public class Settlement {
                        @SagaStep(service = "s", command = "c")
                        public void settle() {}
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata()).satisfies(s -> {
                assertThat(s.name()).isEqualTo("Settlement"); // class simple name
                // present-only: absent timeout/maxRetries keep the SagaMetadata.Builder
                // defaults — and the processor (also present-only on the same builder)
                // produces exactly these, so it is parity, not a silent divergence.
                assertThat(s.timeout()).isEqualTo("PT30M");
                assertThat(s.maxRetries()).isEqualTo(3);
                assertThat(s.steps()).singleElement().satisfies(step -> {
                    assertThat(step.name()).isEqualTo("settle"); // method name fallback
                    assertThat(step.order()).isEqualTo(1);       // default order
                });
            });
        }

        /**
         * Kernel ADR-064 addresses a saga plan by {@code (name, version)}, and the processor
         * reads {@code @Saga.version}. A reader that skipped it would give {@code version = 3}
         * back as {@code 1} — a different plan identity from the one in the processor's
         * baseline, with both sides emitting well-formed metadata.
         */
        @Test
        void sagaVersionIsReadAsDeclaredAndDefaultsToOne() {
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = 3)").version()).isEqualTo(3);
            // Absent: the annotation default, which is also the builder default — the
            // processor is present-only on the same builder, so both paths say 1.
            assertThat(sagaOf("@Saga(name = \"Fulfilment\")").version()).isEqualTo(1);
            assertThat(sagaOf("@Saga").version()).isEqualTo(1);
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = 1)").version()).isEqualTo(1);
            // The other attributes are untouched by the version read.
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = 2, maxRetries = 5)"))
                    .satisfies(s -> {
                        assertThat(s.name()).isEqualTo("Fulfilment");
                        assertThat(s.version()).isEqualTo(2);
                        assertThat(s.maxRetries()).isEqualTo(5);
                    });
        }

        @Test
        void sagaVersionIsCarriedAsWrittenEvenWhereTheKernelRefusesIt() {
            // javac folds these into the processor's value unchanged, and the kernel refuses a
            // version below 1 at FlowDefinitionBuilder.version(int). Repairing them to 1 here
            // would disagree with the processor AND hide the refusal the author needs to see.
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = -2)").version()).isEqualTo(-2);
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = 0)").version()).isZero();
            // The one literal whose magnitude is not an int by itself: JavaParser hands it back
            // as a Long, and it must still land on the value javac folds it to.
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = -2147483648)").version())
                    .isEqualTo(Integer.MIN_VALUE);
        }

        @Test
        void sagaVersionFromAConstantIsNotResolved() {
            // The documented structural difference, pinned so that closing it is a deliberate
            // change: the processor sees javac's folded constant, a syntactic reader sees a name
            // or an expression and keeps the default.
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = Versions.CURRENT)").version())
                    .isEqualTo(1);
            assertThat(sagaOf("@Saga(name = \"Fulfilment\", version = 1 + 2)").version()).isEqualTo(1);
        }

        @Test
        void sagaCompensationSectionIsReadAsDeclared() {
            SagaMetadata saga = sagaOf("""
                    @Saga(name = "Fulfilment", compensationTimeout = "PT2M",
                            compensationMaxRetries = 7, compensationRetryDelay = "PT30S",
                            continueCompensationOnFailure = true, compensationDlq = "orders.dlq",
                            compensationFailureHandler = com.acme.Escalation.class,
                            manualInterventionOnCompensationFailure = true)""");
            assertThat(saga.compensationTimeout()).isEqualTo("PT2M");
            assertThat(saga.compensationMaxRetries()).isEqualTo(7);
            assertThat(saga.compensationRetryDelay()).isEqualTo("PT30S");
            assertThat(saga.continueCompensationOnFailure()).isTrue();
            assertThat(saga.compensationDlq()).isEqualTo("orders.dlq");
            assertThat(saga.compensationFailureHandler()).isEqualTo("com.acme.Escalation");
            assertThat(saga.manualInterventionOnCompensationFailure()).isTrue();
        }

        @Test
        void sagaCompensationLeftUndeclaredIsAbsentNotDefaulted() {
            // null means "not declared, the annotation default applies": writing the default
            // here would put keys on every saga that the processor, which does not extract the
            // section, leaves off.
            SagaMetadata saga = sagaOf("@Saga(name = \"Fulfilment\")");
            assertThat(saga.compensationTimeout()).isEqualTo("PT10M"); // builder default
            assertThat(saga.compensationMaxRetries()).isNull();
            assertThat(saga.compensationRetryDelay()).isNull();
            assertThat(saga.continueCompensationOnFailure()).isNull();
            assertThat(saga.compensationDlq()).isNull();
            assertThat(saga.compensationFailureHandler()).isNull();
            assertThat(saga.manualInterventionOnCompensationFailure()).isNull();
        }

        @Test
        void sagaCompensationZeroAndFalseAreCarriedNotDropped() {
            SagaMetadata saga = sagaOf("""
                    @Saga(name = "Fulfilment", compensationMaxRetries = 0,
                            continueCompensationOnFailure = false,
                            manualInterventionOnCompensationFailure = false)""");
            assertThat(saga.compensationMaxRetries()).isZero();
            assertThat(saga.continueCompensationOnFailure()).isFalse();
            assertThat(saga.manualInterventionOnCompensationFailure()).isFalse();
            // Signed, as javac folds it: carried, not repaired.
            assertThat(sagaOf("@Saga(compensationMaxRetries = -1)").compensationMaxRetries()).isEqualTo(-1);
        }

        @Test
        void sagaCompensationNoneValuesReadAsNull() {
            SagaMetadata saga = sagaOf("""
                    @Saga(name = "Fulfilment", compensationDlq = "  ",
                            compensationFailureHandler = void.class)""");
            assertThat(saga.compensationDlq()).isNull();
            assertThat(saga.compensationFailureHandler()).isNull();
        }

        @Test
        void sagaCompensationStrategyAndOrderAreNotRead() {
            // The annotation enums declare constants the AST enums lack (STOP_ON_FAILURE,
            // MANUAL, CUSTOM order), so a read by name would carry a wrong value. Pinned so that
            // reading them is a deliberate change made with the enum reconciliation.
            SagaMetadata saga = sagaOf("""
                    @Saga(compensationStrategy = Saga.CompensationStrategy.BEST_EFFORT,
                            compensationOrder = Saga.CompensationOrder.FORWARD)""");
            assertThat(saga.compensationStrategy()).isEqualTo(SagaMetadata.CompensationStrategy.ALL_OR_NOTHING);
            assertThat(saga.compensationOrder()).isEqualTo(SagaMetadata.CompensationOrder.REVERSE);
        }

        @Test
        void sagaFailureHandlerResolvesTheWayJavacScopesIt() {
            // The declaring class itself, and a member reached through it.
            assertThat(handlerOf("", "Fulfilment.class", "")).isEqualTo("x.Fulfilment");
            assertThat(handlerOf("", "Fulfilment.Escalation.class", "public static class Escalation {}"))
                    .isEqualTo("x.Fulfilment.Escalation");
            // A single-type import, and a member of an imported type.
            assertThat(handlerOf("import com.acme.Escalation;", "Escalation.class", ""))
                    .isEqualTo("com.acme.Escalation");
            assertThat(handlerOf("import com.acme.Handlers;", "Handlers.Escalation.class", ""))
                    .isEqualTo("com.acme.Handlers.Escalation");
            // Already qualified: its first segment is a package, so it is kept.
            assertThat(handlerOf("", "com.acme.Escalation.class", "")).isEqualTo("com.acme.Escalation");
            // Neither declared nor imported, and no on-demand import: the unit's package.
            assertThat(handlerOf("", "Escalation.class", "")).isEqualTo("x.Escalation");
        }

        @Test
        void sagaFailureHandlerUnderAnOnDemandImportIsKeptAsWritten() {
            // Same package or com.acme.*: javac knows, a syntactic reader does not, so it keeps
            // what the source says instead of guessing.
            assertThat(handlerOf("import com.acme.*;", "Escalation.class", "")).isEqualTo("Escalation");
            // A static on-demand import cannot bring in a top-level type, so it does not block.
            assertThat(handlerOf("import static com.acme.Util.*;", "Escalation.class", ""))
                    .isEqualTo("x.Escalation");
            // A name qualified through the declaring class still resolves under one.
            assertThat(handlerOf("import com.acme.*;", "Fulfilment.Escalation.class",
                    "public static class Escalation {}"))
                    .isEqualTo("x.Fulfilment.Escalation");
        }

        @Test
        void sagaFailureHandlerResolvesThroughTheEnclosingType() {
            // An entity nested in a holder sees the holder's member types by simple name, and
            // they shadow an on-demand import. Its own member types are not in scope for an
            // annotation on it: javac rejects Escalation.class there.
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import com.acme.*;
                    public class Holder {
                        public static class Escalation {}
                        @ExerisDomain
                        @Saga(compensationFailureHandler = Escalation.class)
                        public static class Fulfilment {}
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata().compensationFailureHandler())
                    .isEqualTo("x.Holder.Escalation");
        }

        @Test
        void sagaFailureHandlerInAnotherTopLevelTypeOfTheUnitResolves() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    import com.acme.*;
                    @ExerisDomain
                    @Saga(compensationFailureHandler = Escalation.class)
                    public class Fulfilment {}
                    class Escalation {}
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata().compensationFailureHandler())
                    .isEqualTo("x.Escalation");
        }

        @Test
        void sagaFailureHandlerInTheDefaultPackageIsTheSimpleName() {
            String src = """
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    @ExerisDomain
                    @Saga(compensationFailureHandler = Escalation.class)
                    public class Fulfilment {}
                    """;
            assertThat(reader.read(src).orElseThrow().sagaMetadata().compensationFailureHandler())
                    .isEqualTo("Escalation");
        }

        private String handlerOf(String imports, String classLiteral, String body) {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    %s
                    @ExerisDomain
                    @Saga(compensationFailureHandler = %s)
                    public class Fulfilment { %s }
                    """.formatted(imports, classLiteral, body);
            return reader.read(src).orElseThrow().sagaMetadata().compensationFailureHandler();
        }

        private SagaMetadata sagaOf(String sagaAnnotation) {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Saga;
                    @ExerisDomain
                    %s
                    public class Fulfilment {}
                    """.formatted(sagaAnnotation);
            return reader.read(src).orElseThrow().sagaMetadata();
        }

        @Test
        void readsInternalApiAsPresenceOnly() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.InternalApi;
                    @ExerisDomain
                    @InternalApi(rateLimit = 100)
                    public class Ledger {}
                    """;
            assertThat(reader.read(src).orElseThrow().internalApi()).satisfies(api -> {
                // known SDK<->AST drift: only presence is extracted -> internal = true
                assertThat(api.internal()).isTrue();
                assertThat(api.hidden()).isFalse();
                assertThat(api.readOnly()).isFalse();
            });
        }

        @Test
        void absentFacetsLeaveMetadataNull() {
            DomainMetadata domain = reader.read(ACCOUNT).orElseThrow();
            assertThat(domain.graphMetadata()).isNull();
            assertThat(domain.eventSourced()).isNull();
            assertThat(domain.sagaMetadata()).isNull();
            assertThat(domain.internalApi()).isNull();
        }
    }

    @Nested
    @DisplayName("reader: @Field attribute surface + @Validation (field fidelity)")
    class Fields {

        // @Field.label() has no default, so bare @Field / @Field(...) without label is a
        // compile error in real Java — used here intentionally: the reader tolerates
        // editor-incomplete source (no symbol/attribute-completeness enforcement).
        private static final String PRODUCT = """
                package app.budgethq.product;
                import eu.exeris.sdk.annotation.ExerisDomain;
                import eu.exeris.sdk.annotation.Field;
                import eu.exeris.sdk.annotation.Validation;

                @ExerisDomain
                public class Product {
                    @Field(label = "SKU", description = "Stock code", unique = true, indexed = true,
                            searchable = true, sortable = true, filterable = true, readOnly = true,
                            inCreate = false, inUpdate = false, computed = true,
                            computedFrom = {"a", "b"})
                    private String sku;

                    @Field
                    @Validation(min = 5, max = 100, pattern = "[A-Z]+")
                    private String code;

                    private long internalCounter; // no @Field
                }
                """;

        @Test
        void readsFullFieldAttributeSurfacePresentOnly() {
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("sku")).get().satisfies(field -> {
                assertThat(field.displayName()).isEqualTo("SKU");
                assertThat(field.description()).isEqualTo("Stock code");
                assertThat(field.unique()).isTrue();
                assertThat(field.indexed()).isTrue();
                assertThat(field.searchable()).isTrue();
                assertThat(field.sortable()).isTrue();
                assertThat(field.filterable()).isTrue();
                assertThat(field.readOnly()).isTrue();
                assertThat(field.inCreate()).isFalse();
                assertThat(field.inUpdate()).isFalse();
                assertThat(field.computed()).isTrue();
                assertThat(field.computedFrom()).containsExactly("a", "b");
            });
        }

        @Test
        void annotatedFieldLeavesSearchSortFilterAtBuilderDefault() {
            // THE parity fix: a field WITH @Field uses the builder (search/sort/filter
            // default false), unlike the processor's no-@Field path which uses simple().
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("code")).get().satisfies(field -> {
                assertThat(field.searchable()).isFalse();
                assertThat(field.sortable()).isFalse();
                assertThat(field.filterable()).isFalse();
                assertThat(field.inCreate()).isTrue();  // builder default
                assertThat(field.inUpdate()).isTrue();
            });
        }

        @Test
        void fieldWithoutAnnotationUsesSimpleDefaults() {
            // no @Field -> FieldMetadata.simple() -> search/sort/filter TRUE (the asymmetry)
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("internalCounter")).get().satisfies(field -> {
                assertThat(field.searchable()).isTrue();
                assertThat(field.sortable()).isTrue();
                assertThat(field.filterable()).isTrue();
            });
        }

        @Test
        void readsValidationMinMaxPattern() {
            // min is deliberately non-zero: FieldMetadata is @JsonInclude(NON_DEFAULT)
            // and Jackson 3 drops boxed Long(0) on serialization (see AstJsonRoundTripTest),
            // so 0 is not a wire-safe min until the Field/Validation overlap fix.
            DomainMetadata domain = reader.read(PRODUCT).orElseThrow();
            assertThat(domain.findField("code")).get().satisfies(field -> {
                assertThat(field.min()).isEqualTo(5L);
                assertThat(field.max()).isEqualTo(100L);
                assertThat(field.pattern()).isEqualTo("[A-Z]+");
            });
        }

        @Test
        void readsNegativeValidationMin() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain
                    public class T {
                        @Field @Validation(min = -10, max = -1) private int delta;
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().findField("delta")).get().satisfies(field -> {
                assertThat(field.min()).isEqualTo(-10L); // unary minus unwrapped
                assertThat(field.max()).isEqualTo(-1L);
            });
        }

        @Test
        void deprecatedValidationRequiredFallsBackWhenFieldDoesNot() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain
                    public class T {
                        @Field @Validation(required = true) private String a;
                        @Field(required = false) @Validation(required = true) private String b;
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            // a: @Field has no required -> deprecated @Validation.required carries over
            assertThat(domain.findField("a")).get().extracting("required").isEqualTo(true);
            // b: canonical @Field.required=false wins; the deprecated fallback does not override
            assertThat(domain.findField("b")).get().extracting("required").isEqualTo(false);
        }

        @Test
        void deprecatedValidateOnMapsToFormLifecycle() {
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain
                    public class T {
                        @Field @Validation(validateOn = "CREATE") private String onCreate;
                        @Field @Validation(validateOn = "UPDATE") private String onUpdate;
                        @Field @Validation(validateOn = "WHENEVER") private String unknown;
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            // CREATE -> not in update form
            assertThat(domain.findField("onCreate")).get().satisfies(field -> {
                assertThat(field.inUpdate()).isFalse();
                assertThat(field.inCreate()).isTrue();
            });
            // UPDATE -> not in create form
            assertThat(domain.findField("onUpdate")).get().satisfies(field -> {
                assertThat(field.inCreate()).isFalse();
                assertThat(field.inUpdate()).isTrue();
            });
            // unrecognised value -> no fallback applied (both stay at builder default true)
            assertThat(domain.findField("unknown")).get().satisfies(field -> {
                assertThat(field.inCreate()).isTrue();
                assertThat(field.inUpdate()).isTrue();
            });
        }

        @Test
        void validationIgnoredWithoutField() {
            // @Validation alone (no @Field) -> processor uses simple(), validation dropped
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Validation;
                    @ExerisDomain
                    public class T {
                        @Validation(min = 5, required = true) private String loose;
                    }
                    """;
            assertThat(reader.read(src).orElseThrow().findField("loose")).get().satisfies(field -> {
                assertThat(field.min()).isNull();        // validation not consulted
                assertThat(field.required()).isFalse();
                assertThat(field.searchable()).isTrue(); // simple() path
            });
        }

        @Test
        void readsFieldDataTypeAndStaysOnTheCodegenBaseline() {
            // ADR-042 lock-step: the reader mirrors the processor's
            // @Field.dataType extraction. The value reaches FieldMetadata.dataType and
            // a @Field(dataType=...) source produces no unmodeled facet (the read is
            // attribute-complete against the codegen baseline, so reattach/conflict
            // detection does not misread it as user drift).
            String src = """
                    package x;
                    import eu.exeris.sdk.annotation.ExerisDomain;
                    import eu.exeris.sdk.annotation.Field;
                    @ExerisDomain
                    public class Invoice {
                        @Field(label = "Amount", dataType = "currency") private java.math.BigDecimal amount;
                        @Field(label = "Reference") private String reference;
                    }
                    """;
            DomainMetadata domain = reader.read(src).orElseThrow();
            // present-only: the annotated dataType is read…
            assertThat(domain.findField("amount")).get()
                    .extracting("dataType").isEqualTo("currency");
            // …and an unset dataType stays null (no "" leaking onto the wire)
            assertThat(domain.findField("reference")).get()
                    .extracting("dataType").isNull();
            // the equivalence guard stays empty (dataType is an attribute, not a
            // guarded annotation; UNMODELED_FACET_ANNOTATIONS remains empty)
            assertThat(reader.unmodeledFacets(src)).isEmpty();
        }
    }
}
