package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.SharedScope;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", dataScope = ExerisDomain.DataScope.TENANT)
public class SharedScopeOffTier {
    private UUID tenantId;
    @SharedScope
    private UUID world;
}
