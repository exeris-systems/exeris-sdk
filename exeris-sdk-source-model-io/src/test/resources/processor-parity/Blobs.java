package parity;

import eu.exeris.sdk.annotation.Blob;
import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;

@ExerisDomain(module = "m", path = "/p", dataScope = ExerisDomain.DataScope.TENANT)
public class Blobs {
    @Field(label = "Scan", required = true)
    @Blob(container = "scans", contentTypes = {"application/pdf", "image/png"})
    private String scan;
    @Blob
    private String raw;
    @Blob(contentTypes = "image/jpeg")
    private String photo;
    private String plain;
}
