/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.invoicing;

import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.ActionParamMetadata;
import eu.exeris.sdk.sourcemodel.ast.DataScope;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import eu.exeris.sdk.sourcemodel.ast.FieldMetadata;
import eu.exeris.sdk.sourcemodel.ast.ParamSource;
import eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata;
import eu.exeris.sdk.sourcemodel.ast.RouteAccess;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.tck.TckMappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the invoicing example sources exactly as the documentation quotes them and asserts the
 * metadata they declare. Source files are resolved against the module base directory, which is
 * surefire's working directory.
 */
@DisplayName("examples: invoicing")
class InvoicingExampleTest {

    private static final Path SOURCES = Path.of("src/main/java/eu/exeris/sdk/examples/invoicing");

    private final SourceModelReader reader = new SourceModelReader();

    private DomainMetadata read(String entity) throws IOException {
        return reader.read(Files.readString(SOURCES.resolve(entity + ".java"))).orElseThrow();
    }

    private static FieldMetadata field(DomainMetadata domain, String name) {
        return domain.fields().stream()
                .filter(f -> name.equals(f.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name + " in " + domain.entityName()));
    }

    private static ActionMetadata action(DomainMetadata domain, String name) {
        return domain.actions().stream()
                .filter(a -> name.equals(a.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no action " + name + " in " + domain.entityName()));
    }

    @Test
    @DisplayName("identity and data scope come from @ExerisDomain")
    void identity() throws IOException {
        DomainMetadata invoice = read("Invoice");

        assertThat(invoice.entityName()).isEqualTo("Invoice");
        assertThat(invoice.packageName()).isEqualTo("eu.exeris.sdk.examples.invoicing");
        assertThat(invoice.module()).isEqualTo("invoicing");
        assertThat(invoice.path()).isEqualTo("/invoices");
        assertThat(invoice.dataScope()).isEqualTo(DataScope.TENANT);
        assertThat(invoice.audited()).isTrue();
        assertThat(read("Customer").path()).isEqualTo("/customers");
        assertThat(read("InvoiceLine").path()).isEqualTo("/invoice-lines");
    }

    @Test
    @DisplayName("system fields are named by their marker annotations")
    void systemFields() throws IOException {
        var system = read("Invoice").systemFields();

        assertThat(system.primaryKeyField()).isEqualTo("id");
        assertThat(system.tenantIdField()).isEqualTo("tenantId");
        assertThat(system.versionField()).isEqualTo("version");
        assertThat(system.createdAtField()).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("@Field carries lifecycle flags, @Validation carries constraints")
    void lifecycleAndValidation() throws IOException {
        DomainMetadata customer = read("Customer");

        FieldMetadata name = field(customer, "name");
        assertThat(name.required()).isTrue();
        assertThat(name.inCreate()).isTrue();
        assertThat(name.inUpdate()).isTrue();
        assertThat(name.minLength()).isEqualTo(2);
        assertThat(name.maxLength()).isEqualTo(120);

        FieldMetadata taxId = field(customer, "taxId");
        assertThat(taxId.required()).isFalse();
        assertThat(taxId.inCreate()).isTrue();
        assertThat(taxId.inUpdate()).isFalse();
        assertThat(taxId.pattern()).isEqualTo("[A-Z0-9]{8,14}");

        FieldMetadata number = field(read("Invoice"), "number");
        assertThat(number.required()).isTrue();
        assertThat(number.inCreate()).isFalse();
        assertThat(number.inUpdate()).isFalse();
        assertThat(number.unique()).isTrue();
        assertThat(number.readOnly()).isTrue();

        FieldMetadata quantity = field(read("InvoiceLine"), "quantity");
        assertThat(quantity.min()).isEqualTo(1L);
        assertThat(quantity.max()).isEqualTo(10_000L);
        assertThat(field(read("InvoiceLine"), "unitPriceCents").min()).isZero();
    }

    @Test
    @DisplayName("@Relationship links Invoice to Customer and to its lines")
    void relationships() throws IOException {
        DomainMetadata invoice = read("Invoice");

        assertThat(invoice.relationships()).extracting(RelationshipMetadata::fieldName)
                .containsExactly("customer", "lines");

        RelationshipMetadata customer = invoice.relationships().getFirst();
        assertThat(customer.targetEntity()).isEqualTo("Customer");
        assertThat(customer.type()).isEqualTo(RelationshipMetadata.RelationType.MANY_TO_ONE);
        assertThat(customer.displayField()).isEqualTo("name");

        RelationshipMetadata lines = invoice.relationships().get(1);
        assertThat(lines.targetEntity()).isEqualTo("InvoiceLine");
        assertThat(lines.type()).isEqualTo(RelationshipMetadata.RelationType.ONE_TO_MANY);
        assertThat(lines.mappedBy()).isEqualTo("invoice");
        assertThat(lines.displayField()).isEqualTo("description");

        assertThat(read("InvoiceLine").relationships()).singleElement()
                .satisfies(r -> assertThat(r.targetEntity()).isEqualTo("Invoice"));
    }

    @Test
    @DisplayName("actions carry body and query parameters; exactly one is PUBLIC")
    void actions() throws IOException {
        DomainMetadata invoice = read("Invoice");

        assertThat(invoice.actions()).extracting(ActionMetadata::name)
                .containsExactly("issue", "viewShared");

        ActionMetadata issue = action(invoice, "issue");
        assertThat(issue.httpMethod()).isEqualTo("POST");
        assertThat(issue.routeAccess()).isNull();
        ActionParamMetadata dueInDays = issue.params().getFirst();
        assertThat(dueInDays.name()).isEqualTo("dueInDays");
        assertThat(dueInDays.required()).isFalse();
        assertThat(dueInDays.defaultValue()).isEqualTo("30");

        ActionMetadata shared = action(invoice, "viewShared");
        assertThat(shared.httpMethod()).isEqualTo("GET");
        assertThat(shared.routeAccess()).isEqualTo(RouteAccess.PUBLIC);
        ActionParamMetadata token = shared.params().getFirst();
        assertThat(token.bindingSource()).isEqualTo(ParamSource.QUERY);
        assertThat(token.name()).isEqualTo("shareToken");
        assertThat(token.queryKey()).isEqualTo("token");

        assertThat(invoice.routeAccess()).isNull();
        assertThat(invoice.actions()).filteredOn(a -> a.routeAccess() == RouteAccess.PUBLIC).hasSize(1);
    }

    @Test
    @DisplayName("the metadata survives a JSON round trip")
    void jsonRoundTrip() throws IOException {
        ObjectMapper mapper = TckMappers.canonical();

        for (String entity : new String[] {"Customer", "Invoice", "InvoiceLine"}) {
            DomainMetadata domain = read(entity);
            String json = mapper.writeValueAsString(domain);
            assertThat(mapper.readValue(json, DomainMetadata.class)).isEqualTo(domain);
        }
    }
}
