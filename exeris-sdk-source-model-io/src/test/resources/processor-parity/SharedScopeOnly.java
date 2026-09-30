package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.SharedScope;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", dataScope = ExerisDomain.DataScope.GLOBAL, primaryKeyField = "id")
public class SharedScopeOnly {
    @SharedScope
    private UUID world;
}
