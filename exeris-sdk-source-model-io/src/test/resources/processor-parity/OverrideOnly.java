package parity;

import eu.exeris.sdk.annotation.ExerisDomain;

@ExerisDomain(module = "m", path = "/p", versionField = "revision", primaryKeyField = "code", softDeleteField = "gone")
public class OverrideOnly {
    private String code;
    private long revision;
    private boolean gone;
}
