package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.AuditCreatedAt;
import eu.exeris.sdk.annotation.system.TenantId;
import eu.exeris.sdk.annotation.system.Version;
import java.time.Instant;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", dataScope = ExerisDomain.DataScope.TENANT, versioned = true, audited = true,
        createdByField = "author", tenantIdField = "owner")
public class Markers {
    @TenantId
    private UUID owner;
    @Version
    private long revision;
    @AuditCreatedAt
    private Instant born;
    private String author;
}
