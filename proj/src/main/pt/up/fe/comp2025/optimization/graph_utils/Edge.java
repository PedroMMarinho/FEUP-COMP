package pt.up.fe.comp2025.optimization.graph_utils;

public class Edge {
    private Node source;
    private Node dest;

    public Edge(Node source, Node dest) {
        this.source = source;
        this.dest = dest;
    }

    public Node getSource() {
        return source;
    }

    public Node getDest() {
        return dest;
    }

}
