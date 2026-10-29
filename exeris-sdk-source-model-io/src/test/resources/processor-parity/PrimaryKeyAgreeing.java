package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.system.PrimaryKey;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p", primaryKeyField = "invoiceNo")
public class PrimaryKeyAgreeing {
    @PrimaryKey
    private UUID invoiceNo;
}
