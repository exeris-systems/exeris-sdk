package parity;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Graph;
import eu.exeris.sdk.annotation.GraphEdge;
import eu.exeris.sdk.annotation.GraphEdges;
import java.util.UUID;

@ExerisDomain(module = "m", path = "/p")
@Graph(nodeClass = "EdgeNode")
public class Edges {
    @GraphEdge(type = "OWNS", targetLabel = "Car", target = Plain.class)
    private UUID car;
    @GraphEdge(type = "KNOWS", target = Plain.class, targetName = "Ignored")
    private UUID friend;
    @GraphEdge(type = "LIKES", targetName = "Thing")
    private UUID thing;
    @GraphEdge(type = "TAGGED", targetLabel = " ")
    private UUID tag;
    @GraphEdges({@GraphEdge(type = "HELD", target = parity.Plain.class)})
    private UUID held;
    @GraphEdge(type = "PAIR")
    private UUID left, right;
    private UUID noEdge;
}
