package pt.up.fe.comp2025.optimization.graph_utils;


import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;
import java.util.List;

public class Node {
    private String id;
    private int virtualReg;
    private List<Edge> edges;
    private boolean hasColor = true;
    private List<Pair<Integer,Integer>> livelinessRange;

    public Node(String id, int virtualReg) {
        this.id = id;
        this.virtualReg = virtualReg;
        this.edges = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public int getVirtualReg() {
        return virtualReg;
    }

    public boolean hasColor() {
        return hasColor;
    }

    public void setHasColor(boolean hasColor) {
        this.hasColor = hasColor;
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setLivelinessRange(List<Pair<Integer,Integer>> livelinessRange) {
        this.livelinessRange = livelinessRange;
    }

    public List<Pair<Integer,Integer>> getLivelinessRange() {
        return livelinessRange;
    }

    public List<Node> getColoredNeighbours(){
        ArrayList<Node> coloredNeighbours = new ArrayList<>();

        for (Edge edge : edges) {
            if (edge.getDest().hasColor())
                coloredNeighbours.add(edge.getDest());
        }
        return coloredNeighbours;
    }

    public boolean intersectsLivelinessRange(List<Pair<Integer, Integer>> other) {
        for (Pair<Integer, Integer> range1 : this.livelinessRange) {
            for (Pair<Integer, Integer> range2 : other) {

                int start1 = range1.a;
                int end1 = range1.b;

                int start2 = range2.a;
                int end2 = range2.b;

                if (end1 <= start2 || start1 >= end2) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

}
