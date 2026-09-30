package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Graph;
import eu.exeris.sdk.annotation.GraphEdge;
import eu.exeris.sdk.annotation.GraphEdges;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p")
@Graph
public class EdgesMixed {
    @GraphEdge(type = "DIRECT")
    @GraphEdges({@GraphEdge(type = "CONTAINED")})
    private UUID mixed;
}
