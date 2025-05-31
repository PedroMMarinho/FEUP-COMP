package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import pt.up.fe.comp2025.optimization.graph_utils.GraphColoringAlgorithm;
import pt.up.fe.comp2025.optimization.graph_utils.InterferenceGraph;


public class RegisterAllocationOpt {

    private ClassUnit classUnit;
    private LiveLinessAnalyser liveLinessAnalyser;

    RegisterAllocationOpt(ClassUnit classUnit) {
        this.classUnit = classUnit;
        this.liveLinessAnalyser = new LiveLinessAnalyser();
    }

    public void allocateReg(int nRegisters) {
        try {
            classUnit.checkMethodLabels();
            classUnit.buildCFGs();
            classUnit.buildVarTables();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        for (var method : classUnit.getMethods()) {
            // Compute liveliness
            var liveliness = liveLinessAnalyser.computeAnalysis(method);
            // Gen Interference Graph
            var graph = new InterferenceGraph(liveliness, method);
            // Apply Coloring
            GraphColoringAlgorithm.compute(graph,nRegisters, nRegisters == 0);
        }

    }


}