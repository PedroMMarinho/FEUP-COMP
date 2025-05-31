package pt.up.fe.comp2025.optimization.graph_utils;

import org.antlr.v4.runtime.misc.Pair;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.VarScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InterferenceGraph {

    private HashMap<String, Descriptor> varTable;
    private List<HashMap<Integer, List<String>>> liveliness;
    private boolean isStaticMethod;
    private HashMap<Integer, Node> nodes;


    public InterferenceGraph(List<HashMap<Integer, List<String>>> liveliness, Method method) {
        this.liveliness = liveliness;
        this.varTable = method.getVarTable();
        this.isStaticMethod = method.isStaticMethod();
        this.nodes = getGraphNodes();
        computeEdges();
    }

    private void computeEdges() {
        List<Node> allNodes = new ArrayList<>(nodes.values());

        for (int i = 0; i < allNodes.size(); i++) {
            Node source = allNodes.get(i);

            for (int j = i + 1; j < allNodes.size(); j++) {
                Node dest = allNodes.get(j);

                if (source.intersectsLivelinessRange(dest.getLivelinessRange())) {
                    source.addEdge(new Edge(source, dest));
                    dest.addEdge(new Edge(dest, source));
                }
            }
        }
    }

    private HashMap<Integer, Node> getGraphNodes() {
        HashMap<Integer, Node> nodes = new HashMap<>();

        for (var identifier : varTable.keySet()) {
            Descriptor descriptor = varTable.get(identifier);

            if (descriptor.getScope() != VarScope.LOCAL) {
                continue;
            }

            Node node = new Node(identifier,descriptor.getVirtualReg());
            node.setLivelinessRange(getNodesLivelinessRange(identifier));
            nodes.put(descriptor.getVirtualReg(), node);

        }
        return nodes;
    }

    private List<Pair<Integer,Integer>> getNodesLivelinessRange(String identifier) {
        List<Pair<Integer,Integer>> livelinessRange = new ArrayList<>();
        var in = liveliness.get(0);
        var out = liveliness.get(1);


        int currentRangeStart = -1;
        List<Integer> instructionIds = new ArrayList<>(in.keySet());

        for (var instructionId : instructionIds){
            boolean isLiveAtEndOfThisInstruction = out.get(instructionId) != null && out.get(instructionId).contains(identifier);

            if (currentRangeStart == -1) {
                if (isLiveAtEndOfThisInstruction) {
                    currentRangeStart = instructionId;
                }
            }else{
                if (!isLiveAtEndOfThisInstruction) {
                    livelinessRange.add(new Pair<>(currentRangeStart, instructionId));
                    currentRangeStart = -1;
                }
            }

        }

        if (currentRangeStart != -1) {
            livelinessRange.add(new Pair<>(currentRangeStart, instructionIds.size()));
        }

        return livelinessRange;
    }

    public boolean isStaticMethod() {
        return isStaticMethod;
    }

    public HashMap<Integer, Node> getNodes() {
        return nodes;
    }

    public HashMap<String, Descriptor> getVarTable() {
        return varTable;
    }

    public List<HashMap<Integer, List<String>>> getLiveliness() {
        return liveliness;
    }

}
