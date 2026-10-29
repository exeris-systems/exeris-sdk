package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p")
public class PrimaryKeyOnId {
    @PrimaryKey
    private UUID id;
}
