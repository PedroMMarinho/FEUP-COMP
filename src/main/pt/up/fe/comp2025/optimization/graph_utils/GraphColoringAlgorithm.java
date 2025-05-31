package pt.up.fe.comp2025.optimization.graph_utils;

import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.VarScope;

import java.util.*;

public class GraphColoringAlgorithm {

    public static void compute(final InterferenceGraph graph, int kRegisters, boolean minimizeNumRegs) {
        // Initialization
        var varTable = graph.getVarTable();
        var isStaticMethod = graph.isStaticMethod();
        var nodes = graph.getNodes();
        var liveliness = graph.getLiveliness();

        for (var lively : liveliness) {
            System.out.println(lively);
        }

        System.out.println("\n=== GRAPH COLORING VISUALIZATION ===");
        System.out.println("Method type: " + (isStaticMethod ? "Static" : "Instance"));
        System.out.println("K registers requested: " + kRegisters);
        System.out.println("Minimizing registers: " + minimizeNumRegs);

        // Print initial variable table
        System.out.println("\n--- Initial Variable Table ---");
        for (var entry : varTable.entrySet()) {
            Descriptor desc = entry.getValue();
            System.out.println(String.format("%-10s | %-10s | reg: %d",
                    entry.getKey(),
                    desc.getScope(),
                    desc.getVirtualReg()));
        }

        // Print interference graph
        System.out.println("\n--- Interference Graph ---");
        for (var entry : nodes.entrySet()) {
            Node node = entry.getValue();
            System.out.print(node.getId() + " interferes with: ");
            List<String> neighbors = new ArrayList<>();
            for (Edge edge : node.getEdges()) {
                neighbors.add(edge.getDest().getId());
            }
            System.out.println(String.join(", ", neighbors));
        }

        HashMap<String, Descriptor> newVarTable = new HashMap<>();

        int baseRegisterCount = 0;

        // STEP 1: Handle special registers first
        System.out.println("\n--- Register Allocation Steps ---");
        System.out.println("STEP 1: Special registers");

        if (!isStaticMethod) {
            for (var identifier : varTable.keySet()) {
                if (identifier.equals("this")) {
                    newVarTable.put("this", new Descriptor(VarScope.LOCAL, 0, varTable.get("this").getVarType()));
                    System.out.println("  Assigned 'this' to register 0");
                    break;
                }
            }
            baseRegisterCount++;
        }

        // STEP 2: Assign parameters to sequential registers starting from appropriate index
        System.out.println("STEP 2: Parameter registers");
        int nextParamRegister = isStaticMethod ? 0 : 1;
        for (var identifier : varTable.keySet()) {
            Descriptor descriptor = varTable.get(identifier);
            if (descriptor.getScope() == VarScope.PARAMETER) {
                newVarTable.put(identifier, new Descriptor(VarScope.PARAMETER, nextParamRegister, descriptor.getVarType()));
                System.out.println("  Assigned parameter '" + identifier + "' to register " + nextParamRegister);
                nextParamRegister++;
                baseRegisterCount++;
            }
        }

        // STEP 3: Handle fields if any
        System.out.println("STEP 3: Field registers");
        int nextFieldRegister = nextParamRegister;
        for (var identifier : varTable.keySet()) {
            Descriptor descriptor = varTable.get(identifier);
            if (descriptor.getScope() == VarScope.FIELD) {
                newVarTable.put(identifier, new Descriptor(VarScope.FIELD, nextFieldRegister, descriptor.getVarType()));
                System.out.println("  Assigned field '" + identifier + "' to register " + nextFieldRegister);
                nextFieldRegister++;
            }
        }

        // STEP 4: Calculate total available registers (base + k)
        int totalAvailableRegisters = baseRegisterCount + kRegisters;

        System.out.println("STEP 4: Register calculations");
        System.out.println("  Base register count: " + baseRegisterCount);
        System.out.println("  Additional registers (k): " + kRegisters);
        System.out.println("  Total available registers: " + totalAvailableRegisters);

        // STEP 5: Apply graph coloring to LOCAL variables only
        System.out.println("STEP 5: Graph coloring for local variables");
        HashMap<Integer, List<String>> availableColors = new HashMap<>();
        for (int i = nextFieldRegister; i < totalAvailableRegisters; i++) {
            availableColors.put(i, new ArrayList<>());
        }

        System.out.println("  Available registers for locals: " + availableColors.keySet());

        Stack<Node> stack = new Stack<>();
        boolean nodeRemoval;

        System.out.println("\n--- Simplification Phase ---");
        int iteration = 1;
        do {
            nodeRemoval = false;
            var nodeL = nodes.entrySet().iterator();

            System.out.println("Iteration " + iteration + ":");

            while (nodeL.hasNext()) {
                Node node = nodeL.next().getValue();
                String nodeId = node.getId();

                // Skip non-local variables and "this"
                if (!varTable.containsKey(nodeId) ||
                        varTable.get(nodeId).getScope() != VarScope.LOCAL ||
                        nodeId.equals("this")) {
                    continue;
                }

                int degree = node.getColoredNeighbours().size();
                if (degree < availableColors.size()) {
                    System.out.println("  Node '" + nodeId + "' has degree " + degree + " < " +
                            availableColors.size() + ", removing and pushing to stack");
                    node.setHasColor(false);
                    stack.push(node);
                    nodeL.remove();
                    nodeRemoval = true;
                } else {
                    System.out.println("  Node '" + nodeId + "' has degree " + degree + " >= " +
                            availableColors.size() + ", keeping for now");
                }
            }
            iteration++;
        } while (nodeRemoval);

        boolean uncolorableNodesExist = false;
        for (var entry : nodes.entrySet()) {
            String nodeId = entry.getValue().getId();
            if (!varTable.containsKey(nodeId) ||
                    varTable.get(nodeId).getScope() != VarScope.LOCAL ||
                    nodeId.equals("this")) {
                continue;
            }

            System.err.println("Cannot color graph - uncolorable node: " + nodeId);
            uncolorableNodesExist = true;
        }

        if (uncolorableNodesExist) {
            System.out.println("\n--- Graph Coloring Failed ---");
            if(minimizeNumRegs){
                compute(graph,kRegisters + 1, true);
            }
            return;
        }

        System.out.println("\n--- Coloring Phase ---");
        System.out.println("Stack size: " + stack.size());

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            node.setHasColor(true);
            nodes.put(node.getVirtualReg(), node);

            String nodeId = node.getId();

            if (!varTable.containsKey(nodeId) ||
                    varTable.get(nodeId).getScope() != VarScope.LOCAL ||
                    nodeId.equals("this")) {
                continue;
            }

            System.out.println("Processing node: " + nodeId);

            System.out.print("  Neighbors: ");
            for (var neighbor : node.getColoredNeighbours()) {
                String neighborId = neighbor.getId();
                if (newVarTable.containsKey(neighborId)) {
                    System.out.print(neighborId + "(reg:" + newVarTable.get(neighborId).getVirtualReg() + ") ");
                } else {
                    System.out.print(neighborId + "(uncolored) ");
                }
            }
            System.out.println();

            boolean coloredSuccessfully = false;

            for (var reg : availableColors.keySet()) {
                boolean isAvailableForColoring = true;

                for (var neighbour : node.getColoredNeighbours()) {
                    if (availableColors.get(reg).contains(neighbour.getId())) {
                        isAvailableForColoring = false;
                        System.out.println("  Register " + reg + " not available (used by " +
                                neighbour.getId() + ")");
                        break;
                    }
                }

                if (isAvailableForColoring) {
                    availableColors.get(reg).add(nodeId);
                    coloredSuccessfully = true;
                    newVarTable.put(nodeId, new Descriptor(VarScope.LOCAL, reg, varTable.get(nodeId).getVarType()));
                    System.out.println("  Assigned register " + reg + " to " + nodeId);
                    break;
                }
            }

            if (!coloredSuccessfully) {
                if (minimizeNumRegs) {
                    totalAvailableRegisters++;
                    availableColors.put(totalAvailableRegisters - 1, new ArrayList<>());
                    stack.push(node);
                    System.out.println("  No available register, adding new register " +
                            (totalAvailableRegisters - 1) + " and trying again");
                } else {
                    System.err.println("Not enough registers for: " + nodeId);
                    return;
                }
            }
        }

        // STEP 6: Handle local variables that are NOT in the interference graph
        System.out.println("\nSTEP 6: Handling non-interfering local variables");

        for (var identifier : varTable.keySet()) {
            Descriptor descriptor = varTable.get(identifier);

            if (descriptor.getScope() == VarScope.LOCAL &&
                    !newVarTable.containsKey(identifier) &&
                    !identifier.equals("this")) {

                System.out.println("Processing non-interfering variable: " + identifier);

                boolean assigned = false;
                for (var reg : availableColors.keySet()) {
                    availableColors.get(reg).add(identifier);
                    newVarTable.put(identifier, new Descriptor(VarScope.LOCAL, reg, descriptor.getVarType()));
                    System.out.println("  Assigned register " + reg + " to " + identifier);
                    assigned = true;
                    break;
                }

                if (!assigned) {
                    if (minimizeNumRegs) {
                        totalAvailableRegisters++;
                        int newReg = totalAvailableRegisters - 1;
                        availableColors.put(newReg, new ArrayList<>());
                        availableColors.get(newReg).add(identifier);
                        newVarTable.put(identifier, new Descriptor(VarScope.LOCAL, newReg, descriptor.getVarType()));
                        System.out.println("  Added new register " + newReg + " for " + identifier);
                    } else {
                        System.err.println("Not enough registers for: " + identifier);
                        return;
                    }
                }
            }
        }

        for (var identifier : newVarTable.keySet()) {
            varTable.put(identifier, newVarTable.get(identifier));
        }

        ArrayList<Integer> usedRegs = new ArrayList<>();
        for (var descriptor : varTable.values()) {
            if (!usedRegs.contains(descriptor.getVirtualReg())) {
                usedRegs.add(descriptor.getVirtualReg());
            }
        }

        Collections.sort(usedRegs);

        System.out.println("\n=== FINAL REGISTER ALLOCATION ===");
        System.out.println("Number of registers allocated: " + usedRegs.size());
        System.out.println("Used registers: " + usedRegs);

        System.out.println("\n--- Final Variable Table ---");
        for (var entry : varTable.entrySet()) {
            Descriptor desc = entry.getValue();
            System.out.println(String.format("%-10s | %-10s | reg: %d",
                    entry.getKey(),
                    desc.getScope(),
                    desc.getVirtualReg()));
        }

        System.out.println("\n--- Visual Register Allocation ---");
        for (int reg : usedRegs) {
            System.out.print("Register " + reg + ": ");
            List<String> varsInReg = new ArrayList<>();
            for (var entry : varTable.entrySet()) {
                if (entry.getValue().getVirtualReg() == reg) {
                    varsInReg.add(entry.getKey());
                }
            }
            System.out.println(String.join(", ", varsInReg));
        }

        System.out.println("\n=== END VISUALIZATION ===\n");
    }
}