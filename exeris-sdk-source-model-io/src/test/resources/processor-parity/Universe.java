package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.SharedScope;
import eu.exeris.sdk.annotation.system.TenantId;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", dataScope = ExerisDomain.DataScope.UNIVERSE)
public class Universe {
    @TenantId
    private UUID owner;
    @SharedScope
    private UUID world;
}
