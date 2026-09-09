// Proposed change to a generated-endpoint example in the annotations javadoc.
package app.example.order;

import eu.exeris.sdk.annotation.Action;
import eu.exeris.sdk.annotation.ExerisDomain;

@ExerisDomain
public class Order {

    /** Public price lookup — no roles required, so anyone may call it. */
    @Action(name = "quote", roles = {})
    public Quote quote(String sku) {
        return Quote.of(sku);
    }
}
