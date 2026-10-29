package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import eu.exeris.sdk.annotation.system.Version;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", versioned = true)
public class PrimaryKeyMarker {
    @PrimaryKey(strategy = PrimaryKey.Strategy.UUID_DATABASE)
    private UUID orderId;
    @Version
    private long revision;
}
